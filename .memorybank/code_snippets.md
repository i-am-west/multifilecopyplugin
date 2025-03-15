# Multi-File Copy Plugin - Key Code Snippets

This document contains important code snippets from the Multi-File Copy Plugin to help understand its functionality.

## File Content Formatting

From `FileContentUtils.kt`:
```kotlin
fun fileToString(file: VirtualFile): String {
    return "----- ${file.path} -----\n" + String(file.contentsToByteArray(), file.charset)
}
```
This function formats a file's content by adding a header with the file path, making it clear which file each section of copied text comes from.

## Clipboard Operations

From `FileContentUtils.kt`:
```kotlin
fun copyToClipboard(content: String) {
    val stringSelection = StringSelection(content)
    CopyPasteManager.getInstance().setContents(stringSelection)
}
```
This function handles copying the formatted content to the system clipboard.

## Action Execution

From `CopySelectedFilesAction.kt`:
```kotlin
override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return

    val content = FileProcessingUtil.processFiles(
        selectedFiles,
        project,
        selectedFiles.toSet()
    )
    FileContentUtils.copyToClipboard(content)
}
```
This shows how the plugin processes the selected files and copies their content to the clipboard when the action is triggered.

## Project Structure Extraction

From `CopyProjectStructureAction.kt`:
```kotlin
override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val projectDir = project.guessProjectDir() ?: return

    val content = StringBuilder()
    content.append("----- Project Structure: ${project.name} -----\n\n")
    
    val psiManager = PsiManager.getInstance(project)
    processDirectory(project, projectDir, psiManager, content, 0)
    
    FileContentUtils.copyToClipboard(content.toString())
}
```
This shows how the plugin extracts the structure of the entire project and copies it to the clipboard.

## Java Structure Extraction

From `CopyProjectStructureAction.kt`:
```kotlin
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
```
This function shows how Java class structures are extracted using PSI, including class declarations, fields, and method signatures without implementation details.

## Kotlin Structure Extraction

From `CopyProjectStructureAction.kt`:
```kotlin
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
    
    content.append("$indent}\n\n")
}
```
This function shows how Kotlin class structures are extracted using PSI, including class declarations, properties, and function signatures without implementation details.

## File Processing Logic

From `FileProcessingUtil.kt`:
```kotlin
private fun shouldProcessFile(
    file: VirtualFile,
    directlySelectedFiles: Set<VirtualFile>,
    project: Project
): Boolean {
    if (directlySelectedFiles.contains(file)) return true
    if (isInHiddenDirectory(file) && !directlySelectedFiles.contains(file.parent)) return false
    if (file.name in Exclusions.EXCLUDED_FILES) return false

    val extension = file.extension?.lowercase() ?: ""
    if (extension in Exclusions.BINARY_EXTENSIONS ||
        extension in Exclusions.IMAGE_EXTENSIONS) return false

    val fileStatusManager = FileStatusManager.getInstance(project)
    if (fileStatusManager.getStatus(file) == FileStatus.IGNORED) return false
    if (file.length > Exclusions.MAX_FILE_SIZE_BYTES) return false

    return true
}
```
This function shows the filtering logic that determines which files should be processed and which should be excluded.

## Directory Handling

From `FileProcessingUtil.kt`:
```kotlin
private fun handleDirectory(
    directory: VirtualFile,
    content: StringBuilder,
    project: Project,
    directlySelectedFiles: Set<VirtualFile>,
    processedFiles: MutableSet<VirtualFile>
) {
    if (!directory.name.startsWith(".") || directlySelectedFiles.contains(directory)) {
        directory.children?.let {
            processFilesRecursively(it, content, project, directlySelectedFiles, processedFiles)
        }
    }
}
```
This function shows how the plugin recursively processes directories, skipping hidden directories unless explicitly selected.

## Action Registration

From `plugin.xml`:
```xml
<action id="CopyMultipleFilesContent"
        class="com.saturnribbon.multifilecopyplugin.CopySelectedFilesAction"
        text="Copy Multiple Files to Clipboard"
        description="Copies the contents of selected files to the clipboard">
  <add-to-group group-id="ProjectViewPopupMenu" anchor="after" relative-to-action="CopyReference"/>
  <add-to-group group-id="ProjectViewPopupMenuModifyGroup" anchor="after" relative-to-action="CopyReference"/>
  <keyboard-shortcut keymap="$default" first-keystroke="shift ctrl alt C"/>
</action>
```
This XML snippet shows how the action is registered in the plugin configuration, including menu placement and keyboard shortcut. 