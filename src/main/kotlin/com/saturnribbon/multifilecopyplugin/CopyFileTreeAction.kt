package com.saturnribbon.multifilecopyplugin

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.saturnribbon.multifilecopyplugin.util.FileContentUtils

class CopyFileTreeAction : AnAction() {
    companion object {
        private val IGNORED_DIRECTORIES = setOf(".git")
    }

    init {
        templatePresentation.text = "Copy File Tree"
        templatePresentation.description = "Copy the directory and file tree structure"
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        if (selectedFiles.isEmpty()) return

        val treeContent = buildFileTree(selectedFiles, project)
        FileContentUtils.copyToClipboard(treeContent)
    }

    override fun update(e: AnActionEvent) {
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        e.presentation.isEnabled = selectedFiles != null && selectedFiles.isNotEmpty()
    }

    private fun buildFileTree(files: Array<VirtualFile>, project: Project): String {
        val rootPaths = findCommonParentPaths(files)
        val stringBuilder = StringBuilder()
        val fileStatusManager = FileStatusManager.getInstance(project)

        stringBuilder.append("# File Tree Structure\n\n")

        // For each root path, build its tree
        rootPaths.forEach { (rootFile, children) ->
            if (rootPaths.size > 1) {
                // If we have multiple roots, show the full path as a second-level header
                stringBuilder.append("## ").append(rootFile.path).append("\n\n")
            }
            buildTreeRecursively(stringBuilder, children.sortedBy { it.name }, "", rootPaths.size > 1, fileStatusManager)
            stringBuilder.append("\n")
        }

        return stringBuilder.toString().trim()
    }

    private fun buildTreeRecursively(
        builder: StringBuilder,
        files: List<VirtualFile>,
        indent: String,
        useFullPath: Boolean,
        fileStatusManager: FileStatusManager
    ) {
        files.forEach { file ->
            // Skip ignored files and directories
            if (fileStatusManager.getStatus(file) == FileStatus.IGNORED || 
                (file.isDirectory && file.name in IGNORED_DIRECTORIES)) {
                return@forEach
            }

            // Add the current file/directory with proper indentation
            builder.append(indent)
            builder.append("- ")
            
            if (file.isDirectory) {
                builder.append("📁 ")
            } else {
                builder.append("📄 ")
            }
            
            // Add the path or name with type information
            builder.append("`")
            builder.append(if (useFullPath) file.path else file.name)
            builder.append("`")
            
            // Add type and metadata
            builder.append(" *(")
            builder.append(if (file.isDirectory) "directory" else "file")
            if (!file.isDirectory) {
                builder.append(", extension: ${file.extension ?: "none"}")
            }
            
            // Add VCS status if not default
            val status = fileStatusManager.getStatus(file)
            if (status != FileStatus.NOT_CHANGED) {
                builder.append(", status: ${status.text}")
            }
            
            builder.append(")*")
            builder.append("\n")

            // Recursively process children if it's a directory
            if (file.isDirectory) {
                val children = file.children?.toList()?.sortedBy { it.name } ?: emptyList()
                buildTreeRecursively(builder, children, "$indent  ", useFullPath, fileStatusManager)
            }
        }
    }

    private fun findCommonParentPaths(files: Array<VirtualFile>): Map<VirtualFile, List<VirtualFile>> {
        // Group files by their parent directories
        return files.groupBy { file ->
            // Find the topmost selected parent
            var current = file
            var parent = current.parent
            while (parent != null && files.contains(parent)) {
                current = parent
                parent = current.parent
            }
            current
        }
    }
} 