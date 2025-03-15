package com.saturnribbon.multifilecopyplugin

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.saturnribbon.multifilecopyplugin.constants.Exclusions
import com.saturnribbon.multifilecopyplugin.util.FileContentUtils
import org.jetbrains.kotlin.psi.*

class CopySimplifiedStructureAction : AnAction() {
    private val LOG = Logger.getInstance(CopySimplifiedStructureAction::class.java)
    
    // Common test directory names
    private val testDirectories = setOf(
        "test", "tests", "testing",
        "src/test", "src/tests",
        "src/androidTest", "src/testDebug",
        "src/integrationTest", "src/unitTest"
    )
    
    // VCS directories to skip
    private val vcsDirectories = setOf(
        ".git", ".svn", ".hg", ".bzr", "_darcs", ".github"
    )
    
    // Class name patterns to skip
    private val skipClassPatterns = setOf(
        "module", "component", "config", "configuration"
    )

    init {
        templatePresentation.text = "Copy Simplified Structure to Clipboard"
        templatePresentation.description = "Copies a simplified structure of the project (public API only) without implementation details"
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val projectDir = project.guessProjectDir() ?: return

        val content = StringBuilder()
        content.append("----- Simplified Project Structure: ${project.name} -----\n\n")
        
        try {
            val psiManager = PsiManager.getInstance(project)
            processDirectory(project, projectDir, psiManager, content, 0)
            
            // Post-process the output to remove any remaining "override" keywords
            val finalContent = postProcessOutput(content.toString())
            
            FileContentUtils.copyToClipboard(finalContent)
        } catch (ex: Exception) {
            LOG.warn("Error processing project structure", ex)
        }
    }

    private fun processDirectory(
        project: Project,
        directory: VirtualFile,
        psiManager: PsiManager,
        content: StringBuilder,
        depth: Int
    ) {
        try {
            // Skip excluded directories
            if (directory.name in Exclusions.EXCLUDED_DIRECTORIES && depth > 0) {
                return
            }
            
            // Skip hidden directories (starting with .)
            if (directory.name.startsWith(".") && depth > 0) {
                return
            }
            
            // Skip VCS directories
            if (directory.name in vcsDirectories) {
                return
            }
            
            // Skip test directories
            if (isTestDirectory(directory)) {
                return
            }
            
            for (child in directory.children) {
                if (child.isDirectory) {
                    processDirectory(project, child, psiManager, content, depth + 1)
                } else {
                    processFile(project, child, psiManager, content)
                }
            }
        } catch (ex: Exception) {
            LOG.warn("Error processing directory: ${directory.path}", ex)
        }
    }

    private fun isTestDirectory(directory: VirtualFile): Boolean {
        // Check if the directory name matches common test directory names
        if (directory.name.lowercase() in setOf("test", "tests", "testing")) {
            return true
        }
        
        // Check if the full path contains test directory patterns
        val path = directory.path.replace('\\', '/')
        return testDirectories.any { testDir -> 
            path.endsWith("/$testDir") || path.contains("/$testDir/")
        }
    }

    private fun isTestFile(file: VirtualFile): Boolean {
        // Check if file is in a test directory
        var parent = file.parent
        while (parent != null) {
            if (isTestDirectory(parent)) {
                return true
            }
            parent = parent.parent
        }
        
        // Check if file name indicates it's a test
        val fileName = file.nameWithoutExtension.lowercase()
        return fileName.startsWith("test") || 
               fileName.endsWith("test") || 
               fileName.endsWith("tests") || 
               fileName.endsWith("spec") ||
               fileName.contains("test_")
    }

    private fun shouldSkipClass(className: String?): Boolean {
        if (className == null) return false
        val lowerName = className.lowercase()
        return skipClassPatterns.any { pattern ->
            lowerName == pattern || 
            lowerName.endsWith(pattern) || 
            lowerName.startsWith(pattern)
        }
    }

    private fun processFile(
        project: Project,
        file: VirtualFile,
        psiManager: PsiManager,
        content: StringBuilder
    ) {
        try {
            // Skip excluded files
            if (file.name in Exclusions.EXCLUDED_FILES) {
                return
            }
            
            // Skip binary and image files
            val extension = file.extension?.lowercase() ?: ""
            if (extension in Exclusions.BINARY_EXTENSIONS || 
                extension in Exclusions.IMAGE_EXTENSIONS) {
                return
            }
            
            // Skip large files
            if (file.length > Exclusions.MAX_FILE_SIZE_BYTES) {
                return
            }
            
            // Skip test files
            if (isTestFile(file)) {
                return
            }
            
            // Skip VCS-ignored files
            val fileStatusManager = FileStatusManager.getInstance(project)
            if (fileStatusManager.getStatus(file) == FileStatus.IGNORED) {
                return
            }
            
            val psiFile = psiManager.findFile(file) ?: return
            
            // Process based on file type
            when {
                psiFile is PsiJavaFile -> {
                    // Check if file has any non-empty classes
                    val hasNonEmptyClasses = psiFile.classes.any { classHasNonPrivateMembers(it) }
                    
                    if (hasNonEmptyClasses) {
                        val structure = extractSimplifiedJavaStructure(psiFile)
                        if (structure.isNotEmpty()) {
                            // Get relative path by removing project base path
                            val projectPath = project.basePath ?: ""
                            val relativePath = file.path.removePrefix(projectPath).removePrefix("/").removePrefix("\\")
                            
                            content.append("\n$relativePath:\n")
                            content.append(structure)
                        }
                    }
                }
                psiFile is KtFile -> {
                    // Check if file has any non-empty declarations
                    val hasNonEmptyDeclarations = fileHasNonPrivateDeclarations(psiFile)
                    
                    if (hasNonEmptyDeclarations) {
                        val structure = extractSimplifiedKotlinStructure(psiFile)
                        if (structure.isNotEmpty()) {
                            // Get relative path by removing project base path
                            val projectPath = project.basePath ?: ""
                            val relativePath = file.path.removePrefix(projectPath).removePrefix("/").removePrefix("\\")
                            
                            content.append("\n$relativePath:\n")
                            content.append(structure)
                        }
                    }
                }
                // Add support for other languages as needed
            }
        } catch (e: Exception) {
            LOG.debug("Error processing file: ${file.path}", e)
        }
    }

    private fun extractSimplifiedJavaStructure(psiFile: PsiJavaFile): String {
        val content = StringBuilder()
        
        // Process classes (skip package declaration)
        for (psiClass in psiFile.classes) {
            if (!shouldSkipClass(psiClass.name)) {
                extractSimplifiedJavaClass(psiClass, content, "")
                content.append("\n") // Add a single blank line after each class
            }
        }
        
        // Remove the last newline if it exists to avoid extra blank line at the end
        if (content.isNotEmpty() && content.endsWith("\n\n")) {
            content.setLength(content.length - 1)
        }
        
        return content.toString()
    }
    
    private fun extractSimplifiedJavaClass(psiClass: PsiClass, content: StringBuilder, indent: String) {
        // Skip Module and Component classes
        if (shouldSkipClass(psiClass.name)) {
            return
        }
        
        // Check if class has any non-private members
        if (!classHasNonPrivateMembers(psiClass)) {
            return
        }
        
        // Add class declaration with simplified modifiers
        val modifiers = simplifyJavaModifiers(psiClass.modifierList?.text ?: "")
        val classType = when {
            psiClass.isInterface -> "interface"
            psiClass.isEnum -> "enum"
            else -> "class"
        }
        
        content.append("$indent$modifiers$classType ${psiClass.name}")
        
        // Add extends
        psiClass.extendsList?.referenceElements?.firstOrNull()?.let {
            content.append(" extends ${it.text}")
        }
        
        // Add implements
        val implementsList = psiClass.implementsList?.referenceElements
        if (!implementsList.isNullOrEmpty()) {
            content.append(" implements ")
            content.append(implementsList.joinToString(", ") { it.text })
        }
        
        content.append(" {\n")
        
        // Add fields (only public, protected, or package-private)
        for (field in psiClass.fields) {
            if (!field.hasModifierProperty(PsiModifier.PRIVATE)) {
                val fieldModifiers = simplifyJavaModifiers(field.modifierList?.text ?: "")
                content.append("$indent    $fieldModifiers${field.type.presentableText} ${field.name};\n")
            }
        }
        
        // Add method signatures (only public, protected, or package-private)
        for (method in psiClass.methods) {
            if (!method.hasModifierProperty(PsiModifier.PRIVATE)) {
                val methodModifiers = simplifyJavaModifiers(method.modifierList?.text ?: "")
                content.append("$indent    $methodModifiers${method.returnType?.presentableText ?: "void"} ${method.name}(")
                // Add parameters
                content.append(method.parameterList.parameters.joinToString(", ") { 
                    "${it.type.presentableText} ${it.name}" 
                })
                content.append(");\n")
            }
        }
        
        // Add inner classes
        for (innerClass in psiClass.innerClasses) {
            if (!innerClass.hasModifierProperty(PsiModifier.PRIVATE) && !shouldSkipClass(innerClass.name)) {
                extractSimplifiedJavaClass(innerClass, content, "$indent    ")
            }
        }
        
        content.append("$indent}\n")
    }
    
    private fun simplifyJavaModifiers(modifiers: String): String {
        // Remove annotations except REST API annotations
        val withoutAnnotations = modifiers.replace(Regex("@(?!GET|POST|PUT|DELETE|PATH\\b)\\w+(?:\\([^)]*\\))?\\s*"), "")
        
        // Remove private modifier (we're skipping private members anyway)
        val withoutPrivate = withoutAnnotations.replace(Regex("\\bprivate\\s+"), "")
        
        // Remove public modifier (it's the default)
        val withoutPublic = withoutPrivate.replace(Regex("\\bpublic\\s+"), "")
        
        // Remove protected modifier
        val withoutProtected = withoutPublic.replace(Regex("\\bprotected\\s+"), "")
        
        return if (withoutProtected.trim().isEmpty()) "" else "$withoutProtected "
    }

    private fun extractSimplifiedKotlinStructure(ktFile: KtFile): String {
        val content = StringBuilder()
        
        // Process top-level declarations (skip package declaration)
        for (declaration in ktFile.declarations) {
            when (declaration) {
                is KtClass -> {
                    if (!shouldSkipClass(declaration.name)) {
                        extractSimplifiedKotlinClass(declaration, content, "")
                        content.append("\n") // Add a single blank line after each class
                    }
                }
                is KtObjectDeclaration -> {
                    if (!shouldSkipClass(declaration.name)) {
                        extractSimplifiedKotlinObject(declaration, content, "")
                        content.append("\n") // Add a single blank line after each object
                    }
                }
                is KtFunction -> {
                    if (!isPrivateKotlinDeclaration(declaration)) {
                        val modifiers = simplifyKotlinModifiers(declaration.modifierList?.text)
                        val returnType = declaration.typeReference?.text ?: "Unit"
                        
                        // Remove "fun" keyword for all functions
                        content.append("${modifiers}${declaration.name}(")
                        content.append(declaration.valueParameters.joinToString(", ") {
                            "${it.name}: ${it.typeReference?.text ?: "Any"}"
                        })
                        content.append("): $returnType\n")
                        content.append("\n") // Add a single blank line after each function
                    }
                }
                is KtProperty -> {
                    if (!isPrivateKotlinDeclaration(declaration)) {
                        val modifiers = simplifyKotlinModifiers(declaration.modifierList?.text)
                        val type = declaration.typeReference?.text?.let { ": $it" } ?: ""
                        content.append("${modifiers}${if (declaration.isVar) "var" else "val"} ${declaration.name}$type\n")
                        content.append("\n") // Add a single blank line after each property
                    }
                }
            }
        }
        
        // Remove the last newline if it exists to avoid extra blank line at the end
        if (content.isNotEmpty() && content.endsWith("\n\n")) {
            content.setLength(content.length - 1)
        }
        
        return content.toString()
    }
    
    private fun extractSimplifiedKotlinClass(ktClass: KtClass, content: StringBuilder, indent: String) {
        // Skip Module and Component classes
        if (shouldSkipClass(ktClass.name)) {
            return
        }
        
        // Check if class has any non-private members
        if (!classHasNonPrivateKotlinMembers(ktClass)) {
            return
        }
        
        // Add class declaration with simplified modifiers
        val modifiers = simplifyKotlinModifiers(ktClass.modifierList?.text)
        val classType = when {
            ktClass.isInterface() -> "interface"
            ktClass.isEnum() -> "enum class"
            ktClass.isData() -> "data class"
            ktClass.isSealed() -> "sealed class"
            else -> "class"
        }
        
        content.append("$indent${modifiers}$classType ${ktClass.name}")
        
        // Add primary constructor if present
        ktClass.primaryConstructor?.let { constructor ->
            content.append("(")
            content.append(constructor.valueParameters.joinToString(", ") {
                val paramModifiers = simplifyKotlinModifiers(it.modifierList?.text)
                "$paramModifiers${it.name}: ${it.typeReference?.text ?: "Any"}"
            })
            content.append(")")
        }
        
        // Add supertype list
        val supertypes = ktClass.superTypeListEntries
        if (supertypes.isNotEmpty()) {
            content.append(" : ")
            content.append(supertypes.joinToString(", ") { it.text })
        }
        
        content.append(" {\n")
        
        // Add properties (only public, protected, or internal)
        for (property in ktClass.getProperties()) {
            if (!isPrivateKotlinDeclaration(property)) {
                val modifiers = simplifyKotlinModifiers(property.modifierList?.text)
                val type = property.typeReference?.text?.let { ": $it" } ?: ""
                content.append("$indent    ${modifiers}${if (property.isVar) "var" else "val"} ${property.name}$type\n")
            }
        }
        
        // Add functions (only public, protected, or internal)
        for (function in ktClass.declarations.filterIsInstance<KtFunction>()) {
            if (function.name == null) continue // Skip anonymous functions
            if (isPrivateKotlinDeclaration(function)) continue // Skip private functions
            
            val modifiers = simplifyKotlinModifiers(function.modifierList?.text)
            val returnType = function.typeReference?.text?.let { ": $it" } ?: ""
            
            // Remove "fun" keyword for all functions
            content.append("$indent    ${modifiers}${function.name}(")
            content.append(function.valueParameters.joinToString(", ") {
                "${it.name}: ${it.typeReference?.text ?: "Any"}"
            })
            content.append(")$returnType\n")
        }
        
        // Add nested classes
        for (nestedClass in PsiTreeUtil.findChildrenOfType(ktClass, KtClass::class.java)) {
            if (nestedClass.parent == ktClass && !isPrivateKotlinDeclaration(nestedClass) && !shouldSkipClass(nestedClass.name)) {
                extractSimplifiedKotlinClass(nestedClass, content, "$indent    ")
            }
        }
        
        // Add nested objects
        for (nestedObject in PsiTreeUtil.findChildrenOfType(ktClass, KtObjectDeclaration::class.java)) {
            if (nestedObject.parent == ktClass && !isPrivateKotlinDeclaration(nestedObject) && !shouldSkipClass(nestedObject.name)) {
                extractSimplifiedKotlinObject(nestedObject, content, "$indent    ")
            }
        }
        
        content.append("$indent}\n")
    }
    
    private fun extractSimplifiedKotlinObject(ktObject: KtObjectDeclaration, content: StringBuilder, indent: String) {
        // Skip Module and Component objects
        if (shouldSkipClass(ktObject.name)) {
            return
        }
        
        // Check if object has any non-private members
        if (!objectHasNonPrivateMembers(ktObject)) {
            return
        }
        
        // Add object declaration with simplified modifiers
        val modifiers = simplifyKotlinModifiers(ktObject.modifierList?.text)
        val isCompanion = ktObject.isCompanion()
        val objectType = if (isCompanion) "companion object" else "object"
        val objectName = if (ktObject.name.isNullOrEmpty() && isCompanion) "" else " ${ktObject.name ?: ""}"
        
        content.append("$indent${modifiers}$objectType$objectName")
        
        // Add supertype list
        val supertypes = ktObject.superTypeListEntries
        if (supertypes.isNotEmpty()) {
            content.append(" : ")
            content.append(supertypes.joinToString(", ") { it.text })
        }
        
        content.append(" {\n")
        
        // Add properties (only public, protected, or internal)
        for (property in ktObject.declarations.filterIsInstance<KtProperty>()) {
            if (!isPrivateKotlinDeclaration(property)) {
                val propModifiers = simplifyKotlinModifiers(property.modifierList?.text)
                val type = property.typeReference?.text?.let { ": $it" } ?: ""
                content.append("$indent    ${propModifiers}${if (property.isVar) "var" else "val"} ${property.name}$type\n")
            }
        }
        
        // Add functions (only public, protected, or internal)
        for (function in ktObject.declarations.filterIsInstance<KtFunction>()) {
            if (function.name == null) continue // Skip anonymous functions
            if (isPrivateKotlinDeclaration(function)) continue // Skip private functions
            
            val funcModifiers = simplifyKotlinModifiers(function.modifierList?.text)
            val returnType = function.typeReference?.text?.let { ": $it" } ?: ""
            
            // Remove "fun" keyword for all functions
            content.append("$indent    ${funcModifiers}${function.name}(")
            content.append(function.valueParameters.joinToString(", ") {
                "${it.name}: ${it.typeReference?.text ?: "Any"}"
            })
            content.append(")$returnType\n")
        }
        
        content.append("$indent}\n")
    }
    
    private fun isPrivateKotlinDeclaration(declaration: KtModifierListOwner): Boolean {
        val modifierList = declaration.modifierList ?: return false
        return modifierList.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.PRIVATE_KEYWORD)
    }
    
    private fun simplifyKotlinModifiers(modifiers: String?): String {
        if (modifiers.isNullOrBlank()) return ""
        
        // Remove annotations except REST API annotations
        val withoutAnnotations = modifiers.replace(Regex("@(?!GET|POST|PUT|DELETE|PATH\\b)\\w+(?:\\([^)]*\\))?\\s*"), "")
        
        // Remove private modifier (we're skipping private members anyway)
        val withoutPrivate = withoutAnnotations.replace(Regex("\\bprivate\\s+"), "")
        
        // Remove public modifier (it's the default)
        val withoutPublic = withoutPrivate.replace(Regex("\\bpublic\\s+"), "")
        
        // Remove protected modifier
        val withoutProtected = withoutPublic.replace(Regex("\\bprotected\\s+"), "")
        
        // Remove override modifier
        val withoutOverride = withoutProtected.replace(Regex("\\boverride\\s+"), "")
        
        return if (withoutOverride.trim().isEmpty()) "" else "$withoutOverride "
    }

    // Helper method to check if a Java class has any non-private members
    private fun classHasNonPrivateMembers(psiClass: PsiClass): Boolean {
        // Skip Module and Component classes
        if (shouldSkipClass(psiClass.name)) {
            return false
        }
        
        // Check if class has any non-private members
        val hasNonPrivateFields = psiClass.fields.any { !it.hasModifierProperty(PsiModifier.PRIVATE) }
        val hasNonPrivateMethods = psiClass.methods.any { !it.hasModifierProperty(PsiModifier.PRIVATE) }
        val hasNonPrivateInnerClasses = psiClass.innerClasses.any { 
            !it.hasModifierProperty(PsiModifier.PRIVATE) && classHasNonPrivateMembers(it)
        }
        
        return hasNonPrivateFields || hasNonPrivateMethods || hasNonPrivateInnerClasses
    }
    
    // Helper method to check if a Kotlin file has any non-private declarations
    private fun fileHasNonPrivateDeclarations(ktFile: KtFile): Boolean {
        // Check top-level declarations
        for (declaration in ktFile.declarations) {
            when (declaration) {
                is KtClass -> {
                    if (!shouldSkipClass(declaration.name) && classHasNonPrivateKotlinMembers(declaration)) {
                        return true
                    }
                }
                is KtObjectDeclaration -> {
                    if (!shouldSkipClass(declaration.name) && objectHasNonPrivateMembers(declaration)) {
                        return true
                    }
                }
                is KtFunction -> {
                    if (!isPrivateKotlinDeclaration(declaration)) {
                        return true
                    }
                }
                is KtProperty -> {
                    if (!isPrivateKotlinDeclaration(declaration)) {
                        return true
                    }
                }
            }
        }
        return false
    }
    
    // Helper method to check if a Kotlin class has any non-private members
    private fun classHasNonPrivateKotlinMembers(ktClass: KtClass): Boolean {
        // Check if class has any non-private members
        val hasNonPrivateProperties = ktClass.getProperties().any { !isPrivateKotlinDeclaration(it) }
        val hasNonPrivateFunctions = ktClass.declarations.filterIsInstance<KtFunction>().any { 
            !isPrivateKotlinDeclaration(it) && it.name != null
        }
        val hasNonPrivateNestedClasses = PsiTreeUtil.findChildrenOfType(ktClass, KtClass::class.java).any {
            it.parent == ktClass && !isPrivateKotlinDeclaration(it) && classHasNonPrivateKotlinMembers(it)
        }
        val hasNonPrivateNestedObjects = PsiTreeUtil.findChildrenOfType(ktClass, KtObjectDeclaration::class.java).any {
            it.parent == ktClass && !isPrivateKotlinDeclaration(it) && objectHasNonPrivateMembers(it)
        }
        
        return hasNonPrivateProperties || hasNonPrivateFunctions || hasNonPrivateNestedClasses || hasNonPrivateNestedObjects
    }
    
    // Helper method to check if a Kotlin object has any non-private members
    private fun objectHasNonPrivateMembers(ktObject: KtObjectDeclaration): Boolean {
        val hasNonPrivateProperties = ktObject.declarations.filterIsInstance<KtProperty>().any { 
            !isPrivateKotlinDeclaration(it) 
        }
        val hasNonPrivateFunctions = ktObject.declarations.filterIsInstance<KtFunction>().any { 
            !isPrivateKotlinDeclaration(it) && it.name != null
        }
        
        return hasNonPrivateProperties || hasNonPrivateFunctions
    }

    // Add a method to process the actual output before copying to clipboard
    private fun postProcessOutput(content: String): String {
        // Remove any remaining "override" keywords that might have been missed
        return content.replace(Regex("\\boverride\\s+"), "")
    }

    // This is the actual implementation - we need to keep 'override fun' here
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
} 