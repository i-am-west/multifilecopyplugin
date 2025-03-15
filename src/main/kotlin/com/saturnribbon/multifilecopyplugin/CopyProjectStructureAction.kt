package com.saturnribbon.multifilecopyplugin

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.saturnribbon.multifilecopyplugin.constants.Exclusions
import com.saturnribbon.multifilecopyplugin.util.FileContentUtils
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty

class CopyProjectStructureAction : AnAction() {
    init {
        templatePresentation.text = "Copy Project Structure to Clipboard"
        templatePresentation.description = "Copies the structure of the project (classes, methods, fields) without implementation details"
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val projectDir = project.guessProjectDir() ?: return

        val content = StringBuilder()
        content.append("----- Project Structure: ${project.name} -----\n\n")
        
        val psiManager = PsiManager.getInstance(project)
        processDirectory(project, projectDir, psiManager, content, 0)
        
        FileContentUtils.copyToClipboard(content.toString())
    }

    private fun processDirectory(
        project: Project,
        directory: VirtualFile,
        psiManager: PsiManager,
        content: StringBuilder,
        depth: Int
    ) {
        // Skip excluded directories
        if (directory.name in Exclusions.EXCLUDED_DIRECTORIES && depth > 0) {
            return
        }
        
        // Skip hidden directories (starting with .)
        if (directory.name.startsWith(".") && depth > 0) {
            return
        }
        
        for (child in directory.children) {
            if (child.isDirectory) {
                processDirectory(project, child, psiManager, content, depth + 1)
            } else {
                processFile(project, child, psiManager, content)
            }
        }
    }

    private fun processFile(
        project: Project,
        file: VirtualFile,
        psiManager: PsiManager,
        content: StringBuilder
    ) {
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
        
        try {
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
            // Silently skip files that can't be processed
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
        
        content.append("$indent}\n\n")
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
} 