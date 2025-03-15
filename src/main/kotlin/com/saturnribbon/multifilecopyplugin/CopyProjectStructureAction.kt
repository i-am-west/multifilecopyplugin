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

class CopyProjectStructureAction : AnAction() {
    private val LOG = Logger.getInstance(CopyProjectStructureAction::class.java)
    
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

    init {
        templatePresentation.text = "Copy Project Structure to Clipboard"
        templatePresentation.description = "Copies the structure of the project (classes, methods, fields) without implementation details"
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = FileContentUtils.getSelectedFiles(e)
        if (selectedFiles.isEmpty()) return

        val content = StringBuilder()
        content.append("----- Project Structure: ${project.name} -----\n\n")
        
        try {
            val psiManager = PsiManager.getInstance(project)
            
            // Create a set of directly selected files for tracking
            val directlySelectedFiles = selectedFiles.toSet()
            
            // Process each selected file or directory
            for (file in selectedFiles) {
                if (file.isDirectory) {
                    processDirectory(project, file, psiManager, content, 0)
                } else {
                    processFile(project, file, psiManager, content)
                }
            }
            
            FileContentUtils.copyToClipboard(content.toString())
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
                    val structure = extractJavaStructure(psiFile)
                    if (structure.isNotEmpty()) {
                        content.append("\n${file.path}:\n")
                        content.append(structure)
                    }
                }
                psiFile is KtFile -> {
                    val structure = extractKotlinStructure(psiFile)
                    if (structure.isNotEmpty()) {
                        content.append("\n${file.path}:\n")
                        content.append(structure)
                    }
                }
                // Add support for other languages as needed
            }
        } catch (e: Exception) {
            LOG.debug("Error processing file: ${file.path}", e)
        }
    }

    private fun extractJavaStructure(psiFile: PsiJavaFile): String {
        val content = StringBuilder()
        
        // Add package declaration
        if (psiFile.packageName.isNotEmpty()) {
            content.append("package ${psiFile.packageName};\n\n")
        }
        
        // Process classes
        for (psiClass in psiFile.classes) {
            extractJavaClass(psiClass, content, "")
        }
        
        return content.toString()
    }
    
    private fun extractJavaClass(psiClass: PsiClass, content: StringBuilder, indent: String) {
        // Add class declaration with modifiers
        val modifiers = psiClass.modifierList?.text ?: ""
        val classType = when {
            psiClass.isInterface -> "interface"
            psiClass.isEnum -> "enum"
            else -> "class"
        }
        
        content.append("$indent$modifiers $classType ${psiClass.name}")
        
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
        
        // Add fields
        for (field in psiClass.fields) {
            content.append("$indent    ${field.modifierList?.text ?: ""} ${field.type.presentableText} ${field.name};\n")
        }
        
        // Add method signatures
        for (method in psiClass.methods) {
            content.append("$indent    ${method.modifierList?.text ?: ""} ${method.returnType?.presentableText ?: "void"} ${method.name}(")
            // Add parameters
            content.append(method.parameterList.parameters.joinToString(", ") { 
                "${it.type.presentableText} ${it.name}" 
            })
            content.append(");\n")
        }
        
        // Add inner classes
        for (innerClass in psiClass.innerClasses) {
            extractJavaClass(innerClass, content, "$indent    ")
        }
        
        content.append("$indent}\n\n")
    }

    private fun extractKotlinStructure(ktFile: KtFile): String {
        val content = StringBuilder()
        
        // Add package declaration
        ktFile.packageFqName.takeIf { !it.isRoot }?.let {
            content.append("package $it\n\n")
        }
        
        // Process top-level declarations
        for (declaration in ktFile.declarations) {
            when (declaration) {
                is KtClass -> extractKotlinClass(declaration, content, "")
                is KtObjectDeclaration -> extractKotlinObject(declaration, content, "")
                is KtFunction -> {
                    val modifiers = declaration.modifierList?.text?.let { "$it " } ?: ""
                    val returnType = declaration.typeReference?.text ?: "Unit"
                    content.append("${modifiers}fun ${declaration.name}(")
                    content.append(declaration.valueParameters.joinToString(", ") {
                        "${it.name}: ${it.typeReference?.text ?: "Any"}"
                    })
                    content.append("): $returnType\n\n")
                }
                is KtProperty -> {
                    val modifiers = declaration.modifierList?.text?.let { "$it " } ?: ""
                    val type = declaration.typeReference?.text?.let { ": $it" } ?: ""
                    content.append("${modifiers}val ${declaration.name}$type\n\n")
                }
            }
        }
        
        return content.toString()
    }
    
    private fun extractKotlinClass(ktClass: KtClass, content: StringBuilder, indent: String) {
        // Add class declaration with modifiers
        val modifiers = ktClass.modifierList?.text?.let { "$it " } ?: ""
        val classType = when {
            ktClass.isInterface() -> "interface"
            ktClass.isEnum() -> "enum class"
            ktClass.isData() -> "data class"
            ktClass.isSealed() -> "sealed class"
            else -> "class"
        }
        
        content.append("$indent$modifiers$classType ${ktClass.name}")
        
        // Add primary constructor if present
        ktClass.primaryConstructor?.let { constructor ->
            content.append("(")
            content.append(constructor.valueParameters.joinToString(", ") {
                val paramModifiers = it.modifierList?.text?.let { mods -> "$mods " } ?: ""
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
        
        // Add properties
        for (property in ktClass.getProperties()) {
            val modifiers = property.modifierList?.text?.let { "$it " } ?: ""
            val type = property.typeReference?.text?.let { ": $it" } ?: ""
            content.append("$indent    ${modifiers}${if (property.isVar) "var" else "val"} ${property.name}$type\n")
        }
        
        // Add functions
        for (function in ktClass.declarations.filterIsInstance<KtFunction>()) {
            if (function.name == null) continue // Skip anonymous functions
            
            val modifiers = function.modifierList?.text?.let { "$it " } ?: ""
            val returnType = function.typeReference?.text?.let { ": $it" } ?: ""
            
            content.append("$indent    ${modifiers}fun ${function.name}(")
            content.append(function.valueParameters.joinToString(", ") {
                "${it.name}: ${it.typeReference?.text ?: "Any"}"
            })
            content.append(")$returnType\n")
        }
        
        // Add nested classes
        for (nestedClass in PsiTreeUtil.findChildrenOfType(ktClass, KtClass::class.java)) {
            if (nestedClass.parent == ktClass) {
                extractKotlinClass(nestedClass, content, "$indent    ")
            }
        }
        
        // Add nested objects
        for (nestedObject in PsiTreeUtil.findChildrenOfType(ktClass, KtObjectDeclaration::class.java)) {
            if (nestedObject.parent == ktClass) {
                extractKotlinObject(nestedObject, content, "$indent    ")
            }
        }
        
        content.append("$indent}\n\n")
    }
    
    private fun extractKotlinObject(ktObject: KtObjectDeclaration, content: StringBuilder, indent: String) {
        // Add object declaration with modifiers
        val modifiers = ktObject.modifierList?.text?.let { "$it " } ?: ""
        val isCompanion = ktObject.isCompanion()
        val objectType = if (isCompanion) "companion object" else "object"
        val objectName = if (ktObject.name.isNullOrEmpty() && isCompanion) "" else " ${ktObject.name ?: ""}"
        
        content.append("$indent$modifiers$objectType$objectName")
        
        // Add supertype list
        val supertypes = ktObject.superTypeListEntries
        if (supertypes.isNotEmpty()) {
            content.append(" : ")
            content.append(supertypes.joinToString(", ") { it.text })
        }
        
        content.append(" {\n")
        
        // Add properties - fixed to use declarations.filterIsInstance
        for (property in ktObject.declarations.filterIsInstance<KtProperty>()) {
            val propModifiers = property.modifierList?.text?.let { "$it " } ?: ""
            val type = property.typeReference?.text?.let { ": $it" } ?: ""
            content.append("$indent    ${propModifiers}${if (property.isVar) "var" else "val"} ${property.name}$type\n")
        }
        
        // Add functions
        for (function in ktObject.declarations.filterIsInstance<KtFunction>()) {
            if (function.name == null) continue // Skip anonymous functions
            
            val funcModifiers = function.modifierList?.text?.let { "$it " } ?: ""
            val returnType = function.typeReference?.text?.let { ": $it" } ?: ""
            
            content.append("$indent    ${funcModifiers}fun ${function.name}(")
            content.append(function.valueParameters.joinToString(", ") {
                "${it.name}: ${it.typeReference?.text ?: "Any"}"
            })
            content.append(")$returnType\n")
        }
        
        content.append("$indent}\n\n")
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
} 