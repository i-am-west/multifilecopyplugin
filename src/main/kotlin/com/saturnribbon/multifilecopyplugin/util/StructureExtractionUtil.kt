package com.saturnribbon.multifilecopyplugin.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.saturnribbon.multifilecopyplugin.constants.Exclusions
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.lexer.KtTokens

/**
 * Utility class for extracting code structure with different levels of detail.
 */
object StructureExtractionUtil {
    private val LOG = Logger.getInstance(StructureExtractionUtil::class.java)
    
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
    
    // Class name patterns to skip for simplified structure
    private val skipClassPatterns = setOf(
        "module", "component", "config", "configuration"
    )

    /**
     * Detail level for structure extraction
     */
    enum class DetailLevel {
        FULL_CONTENT,       // Full file content
        PROJECT_STRUCTURE,  // Classes, methods, fields without implementation
        SIMPLIFIED_STRUCTURE // Public API only, omitting private members, etc.
    }

    /**
     * Extracts structure from the given files with the specified detail level.
     */
    fun extractStructure(
        files: Array<VirtualFile>,
        project: Project,
        detailLevel: DetailLevel,
        directlySelectedFiles: Set<VirtualFile> = emptySet()
    ): String {
        val content = StringBuilder()
        
        // Add appropriate header based on detail level
        when (detailLevel) {
            DetailLevel.FULL_CONTENT -> {
                // No header needed, each file will have its own header
            }
            DetailLevel.PROJECT_STRUCTURE -> {
                content.append("----- Project Structure: ${project.name} -----\n\n")
            }
            DetailLevel.SIMPLIFIED_STRUCTURE -> {
                content.append("----- Simplified Structure: ${project.name} -----\n\n")
            }
        }
        
        // Process files based on detail level
        when (detailLevel) {
            DetailLevel.FULL_CONTENT -> {
                // Use existing FileProcessingUtil for full content
                return FileProcessingUtil.processFiles(files, project, directlySelectedFiles)
            }
            DetailLevel.PROJECT_STRUCTURE, DetailLevel.SIMPLIFIED_STRUCTURE -> {
                val psiManager = PsiManager.getInstance(project)
                processFilesForStructure(files, project, psiManager, content, detailLevel, directlySelectedFiles)
            }
        }
        
        return content.toString()
    }
    
    /**
     * Processes files recursively to extract their structure.
     */
    private fun processFilesForStructure(
        files: Array<VirtualFile>,
        project: Project,
        psiManager: PsiManager,
        content: StringBuilder,
        detailLevel: DetailLevel,
        directlySelectedFiles: Set<VirtualFile>,
        processedFiles: MutableSet<VirtualFile> = mutableSetOf()
    ) {
        for (file in files) {
            if (processedFiles.contains(file)) continue
            
            when {
                file.isDirectory -> {
                    processDirectoryForStructure(file, project, psiManager, content, detailLevel, directlySelectedFiles, processedFiles)
                }
                else -> {
                    processFileForStructure(file, project, psiManager, content, detailLevel, directlySelectedFiles, processedFiles)
                }
            }
            
            processedFiles.add(file)
        }
    }
    
    /**
     * Processes a directory to extract structure from its files.
     */
    private fun processDirectoryForStructure(
        directory: VirtualFile,
        project: Project,
        psiManager: PsiManager,
        content: StringBuilder,
        detailLevel: DetailLevel,
        directlySelectedFiles: Set<VirtualFile>,
        processedFiles: MutableSet<VirtualFile>
    ) {
        // Skip test directories unless directly selected
        if (isTestDirectory(directory) && !directlySelectedFiles.contains(directory)) {
            return
        }
        
        // Skip VCS directories
        if (directory.name in vcsDirectories) {
            return
        }
        
        // Skip hidden directories unless directly selected
        if (directory.name.startsWith(".") && !directlySelectedFiles.contains(directory)) {
            return
        }
        
        // Process children
        directory.children?.let {
            processFilesForStructure(it, project, psiManager, content, detailLevel, directlySelectedFiles, processedFiles)
        }
    }
    
    /**
     * Processes a file to extract its structure.
     */
    private fun processFileForStructure(
        file: VirtualFile,
        project: Project,
        psiManager: PsiManager,
        content: StringBuilder,
        detailLevel: DetailLevel,
        directlySelectedFiles: Set<VirtualFile>,
        processedFiles: MutableSet<VirtualFile>
    ) {
        // Skip if file should not be processed
        if (!shouldProcessFileForStructure(file, project, directlySelectedFiles)) {
            return
        }
        
        try {
            val psiFile = psiManager.findFile(file) ?: return
            
            // Skip test files unless directly selected
            if (isTestFile(file) && !directlySelectedFiles.contains(file)) {
                return
            }
            
            // Add file path as header
            val filePath = when (detailLevel) {
                DetailLevel.SIMPLIFIED_STRUCTURE -> getRelativePath(file, project)
                else -> file.path
            }
            content.append("----- $filePath -----\n")
            
            // Extract structure based on file type
            when (psiFile) {
                is PsiJavaFile -> extractJavaFileStructure(psiFile, content, detailLevel)
                is KtFile -> extractKotlinFileStructure(psiFile, content, detailLevel)
                else -> {
                    // For other file types, just add a placeholder
                    if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE) {
                        // Skip non-code files in simplified structure
                        return
                    }
                    content.append("[File content not shown for this file type]\n\n")
                }
            }
            
            content.append("\n")
        } catch (e: Exception) {
            LOG.warn("Error processing file ${file.path}", e)
            content.append("// Error processing file: ${e.message}\n\n")
        }
    }
    
    /**
     * Extracts structure from a Java file.
     */
    private fun extractJavaFileStructure(
        psiFile: PsiJavaFile,
        content: StringBuilder,
        detailLevel: DetailLevel
    ) {
        // Add package declaration
        if (detailLevel != DetailLevel.SIMPLIFIED_STRUCTURE) {
            content.append("package ${psiFile.packageName};\n\n")
        }
        
        // Extract classes
        for (psiClass in psiFile.classes) {
            // Skip classes that match skip patterns in simplified mode
            if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE && 
                skipClassPatterns.any { pattern -> psiClass.name?.lowercase()?.contains(pattern) == true }) {
                continue
            }
            
            extractJavaClass(psiClass, content, "", detailLevel)
        }
    }
    
    /**
     * Extracts structure from a Java class.
     */
    private fun extractJavaClass(
        psiClass: PsiClass,
        content: StringBuilder,
        indent: String,
        detailLevel: DetailLevel
    ) {
        // Skip anonymous classes
        if (psiClass.name == null) return
        
        // Add class declaration with modifiers
        val modifiers = psiClass.modifierList?.text ?: ""
        val classType = when {
            psiClass.isInterface -> "interface"
            psiClass.isEnum -> "enum"
            else -> "class"
        }
        
        // For simplified structure, remove redundant modifiers
        val simplifiedModifiers = if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE) {
            modifiers.replace("public ", "").replace("protected ", "")
        } else {
            modifiers
        }
        
        content.append("$indent$simplifiedModifiers $classType ${psiClass.name}")
        
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
            // Skip private fields in simplified structure
            if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE && 
                field.modifierList?.hasModifierProperty(PsiModifier.PRIVATE) == true) {
                continue
            }
            
            val fieldModifiers = if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE) {
                field.modifierList?.text?.replace("public ", "")?.replace("protected ", "") ?: ""
            } else {
                field.modifierList?.text ?: ""
            }
            
            content.append("$indent    $fieldModifiers ${field.type.presentableText} ${field.name};\n")
        }
        
        // Add method signatures
        for (method in psiClass.methods) {
            // Skip private methods in simplified structure
            if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE && 
                method.modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
                continue
            }
            
            val methodModifiers = if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE) {
                method.modifierList.text.replace("public ", "").replace("protected ", "")
            } else {
                method.modifierList.text
            }
            
            content.append("$indent    $methodModifiers ${method.returnType?.presentableText ?: "void"} ${method.name}(")
            // Add parameters
            content.append(method.parameterList.parameters.joinToString(", ") { 
                "${it.type.presentableText} ${it.name}" 
            })
            content.append(");\n")
        }
        
        // Add inner classes
        for (innerClass in psiClass.innerClasses) {
            extractJavaClass(innerClass, content, "$indent    ", detailLevel)
        }
        
        content.append("$indent}\n\n")
    }
    
    /**
     * Extracts structure from a Kotlin file.
     */
    private fun extractKotlinFileStructure(
        ktFile: KtFile,
        content: StringBuilder,
        detailLevel: DetailLevel
    ) {
        // Add package declaration
        if (detailLevel != DetailLevel.SIMPLIFIED_STRUCTURE && ktFile.packageFqName.asString().isNotEmpty()) {
            content.append("package ${ktFile.packageFqName}\n\n")
        }
        
        // Extract top-level declarations
        for (declaration in ktFile.declarations) {
            when (declaration) {
                is KtClass -> {
                    // Skip classes that match skip patterns in simplified mode
                    if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE && 
                        skipClassPatterns.any { pattern -> declaration.name?.lowercase()?.contains(pattern) == true }) {
                        continue
                    }
                    
                    extractKotlinClass(declaration, content, "", detailLevel)
                }
                is KtObjectDeclaration -> {
                    extractKotlinObject(declaration, content, "", detailLevel)
                }
                is KtProperty -> {
                    // Skip private properties in simplified structure
                    if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE && 
                        declaration.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
                        continue
                    }
                    
                    val modifiers = if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE) {
                        declaration.modifierList?.text?.replace("public ", "")?.replace("protected ", "") ?: ""
                    } else {
                        declaration.modifierList?.text?.let { "$it " } ?: ""
                    }
                    
                    val type = declaration.typeReference?.text?.let { ": $it" } ?: ""
                    content.append("${modifiers}${if (declaration.isVar) "var" else "val"} ${declaration.name}$type\n")
                }
                is KtFunction -> {
                    // Skip private functions in simplified structure
                    if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE && 
                        declaration.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
                        continue
                    }
                    
                    val modifiers = if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE) {
                        // Remove override and fun keywords in simplified structure
                        ""
                    } else {
                        declaration.modifierList?.text?.let { "$it " } ?: ""
                    }
                    
                    val funKeyword = if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE) "" else "fun "
                    val returnType = declaration.typeReference?.text?.let { ": $it" } ?: ""
                    
                    content.append("$modifiers${funKeyword}${declaration.name}(")
                    content.append(declaration.valueParameters.joinToString(", ") {
                        "${it.name}: ${it.typeReference?.text ?: "Any"}"
                    })
                    content.append(")$returnType\n")
                }
            }
        }
    }
    
    /**
     * Extracts structure from a Kotlin class.
     */
    private fun extractKotlinClass(
        ktClass: KtClass,
        content: StringBuilder,
        indent: String,
        detailLevel: DetailLevel
    ) {
        // Add class declaration with modifiers
        val modifiers = if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE) {
            // Remove public and protected modifiers in simplified structure
            ktClass.modifierList?.text?.replace("public ", "")?.replace("protected ", "")?.let { "$it " } ?: ""
        } else {
            ktClass.modifierList?.text?.let { "$it " } ?: ""
        }
        
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
                val paramModifiers = if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE) {
                    it.modifierList?.text?.replace("public ", "")?.replace("protected ", "")?.let { mods -> "$mods " } ?: ""
                } else {
                    it.modifierList?.text?.let { mods -> "$mods " } ?: ""
                }
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
            // Skip private properties in simplified structure
            if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE && 
                property.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
                continue
            }
            
            val propModifiers = if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE) {
                property.modifierList?.text?.replace("public ", "")?.replace("protected ", "")?.let { "$it " } ?: ""
            } else {
                property.modifierList?.text?.let { "$it " } ?: ""
            }
            
            val type = property.typeReference?.text?.let { ": $it" } ?: ""
            content.append("$indent    ${propModifiers}${if (property.isVar) "var" else "val"} ${property.name}$type\n")
        }
        
        // Add functions
        for (function in ktClass.declarations.filterIsInstance<KtFunction>()) {
            if (function.name == null) continue // Skip anonymous functions
            
            // Skip private functions in simplified structure
            if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE && 
                function.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
                continue
            }
            
            val funcModifiers = if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE) {
                // Remove override and fun keywords in simplified structure
                ""
            } else {
                function.modifierList?.text?.let { "$it " } ?: ""
            }
            
            val funKeyword = if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE) "" else "fun "
            val returnType = function.typeReference?.text?.let { ": $it" } ?: ""
            
            content.append("$indent    ${funcModifiers}${funKeyword}${function.name}(")
            content.append(function.valueParameters.joinToString(", ") {
                "${it.name}: ${it.typeReference?.text ?: "Any"}"
            })
            content.append(")$returnType\n")
        }
        
        content.append("$indent}\n\n")
    }
    
    /**
     * Extracts structure from a Kotlin object declaration.
     */
    private fun extractKotlinObject(
        ktObject: KtObjectDeclaration,
        content: StringBuilder,
        indent: String,
        detailLevel: DetailLevel
    ) {
        // Add object declaration with modifiers
        val modifiers = if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE) {
            ktObject.modifierList?.text?.replace("public ", "")?.replace("protected ", "")?.let { "$it " } ?: ""
        } else {
            ktObject.modifierList?.text?.let { "$it " } ?: ""
        }
        
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
        
        // Add properties
        for (property in ktObject.declarations.filterIsInstance<KtProperty>()) {
            // Skip private properties in simplified structure
            if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE && 
                property.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
                continue
            }
            
            val propModifiers = if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE) {
                property.modifierList?.text?.replace("public ", "")?.replace("protected ", "")?.let { "$it " } ?: ""
            } else {
                property.modifierList?.text?.let { "$it " } ?: ""
            }
            
            val type = property.typeReference?.text?.let { ": $it" } ?: ""
            content.append("$indent    ${propModifiers}${if (property.isVar) "var" else "val"} ${property.name}$type\n")
        }
        
        // Add functions
        for (function in ktObject.declarations.filterIsInstance<KtFunction>()) {
            if (function.name == null) continue // Skip anonymous functions
            
            // Skip private functions in simplified structure
            if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE && 
                function.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
                continue
            }
            
            val funcModifiers = if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE) {
                // Remove override and fun keywords in simplified structure
                ""
            } else {
                function.modifierList?.text?.let { "$it " } ?: ""
            }
            
            val funKeyword = if (detailLevel == DetailLevel.SIMPLIFIED_STRUCTURE) "" else "fun "
            val returnType = function.typeReference?.text?.let { ": $it" } ?: ""
            
            content.append("$indent    ${funcModifiers}${funKeyword}${function.name}(")
            content.append(function.valueParameters.joinToString(", ") {
                "${it.name}: ${it.typeReference?.text ?: "Any"}"
            })
            content.append(")$returnType\n")
        }
        
        content.append("$indent}\n\n")
    }
    
    /**
     * Checks if a file should be processed for structure extraction.
     */
    private fun shouldProcessFileForStructure(
        file: VirtualFile,
        project: Project,
        directlySelectedFiles: Set<VirtualFile>
    ): Boolean {
        if (directlySelectedFiles.contains(file)) return true
        
        // Skip hidden files
        if (file.name.startsWith(".")) return false
        
        // Skip excluded files
        if (file.name in Exclusions.EXCLUDED_FILES) return false
        
        // Skip binary and image files
        val extension = file.extension?.lowercase() ?: ""
        if (extension in Exclusions.BINARY_EXTENSIONS ||
            extension in Exclusions.IMAGE_EXTENSIONS) return false
        
        // Skip ignored files
        val fileStatusManager = FileStatusManager.getInstance(project)
        if (fileStatusManager.getStatus(file) == FileStatus.IGNORED) return false
        
        // Skip large files
        if (file.length > Exclusions.MAX_FILE_SIZE_BYTES) return false
        
        // Only process Java and Kotlin files for structure
        if (extension !in setOf("java", "kt", "kts")) return false
        
        return true
    }
    
    /**
     * Checks if a file is a test file.
     */
    private fun isTestFile(file: VirtualFile): Boolean {
        val fileName = file.name.lowercase()
        return fileName.endsWith("test.java") || 
               fileName.endsWith("test.kt") ||
               fileName.startsWith("test") ||
               isInTestDirectory(file)
    }
    
    /**
     * Checks if a directory is a test directory.
     */
    private fun isTestDirectory(directory: VirtualFile): Boolean {
        val path = directory.path.lowercase()
        return testDirectories.any { testDir -> 
            path.endsWith("/$testDir") || path.contains("/$testDir/")
        }
    }
    
    /**
     * Checks if a file is in a test directory.
     */
    private fun isInTestDirectory(file: VirtualFile): Boolean {
        var current = file.parent
        while (current != null) {
            if (isTestDirectory(current)) return true
            current = current.parent
        }
        return false
    }
    
    /**
     * Gets the relative path of a file in the project.
     */
    private fun getRelativePath(file: VirtualFile, project: Project): String {
        val projectDir = project.basePath ?: return file.path
        return if (file.path.startsWith(projectDir)) {
            file.path.substring(projectDir.length + 1)
        } else {
            file.path
        }
    }
} 