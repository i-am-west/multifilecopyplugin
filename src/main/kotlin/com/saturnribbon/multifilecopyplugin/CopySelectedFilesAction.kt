package com.saturnribbon.multifilecopyplugin

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.saturnribbon.multifilecopyplugin.constants.Exclusions
import com.saturnribbon.multifilecopyplugin.util.FileContentUtils

class CopySelectedFilesAction : AnAction("Copy Multiple Files to Clipboard") {

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project? = e.project
        if (project == null) {
            return
        }
        val selectedFiles: Array<VirtualFile>? = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (selectedFiles.isNullOrEmpty()) {
            return
        }
        val combinedContent = StringBuilder()
        // Create a set of directly selected files for checking
        val directlySelectedFiles = selectedFiles.toSet()
        val processedFiles = mutableSetOf<VirtualFile>()

        processFiles(selectedFiles, combinedContent, project, directlySelectedFiles, processedFiles)
        FileContentUtils.copyToClipboard(combinedContent.toString())
    }

    private fun processFiles(
        files: Array<VirtualFile>,
        content: StringBuilder,
        project: Project,
        directlySelectedFiles: Set<VirtualFile>,
        processedFiles: MutableSet<VirtualFile>
    ) {
        for (file in files) {
            when {
                file.isDirectory -> {
                    // Skip hidden directories unless directly selected
                    if (!file.name.startsWith(".") || directlySelectedFiles.contains(file)) {
                        file.children?.let {
                            processFiles(it, content, project, directlySelectedFiles, processedFiles)
                        }
                    }
                }
                else -> {
                    // Check if file is directly selected or not ignored
                    if (!processedFiles.contains(file) && shouldProcessFile(file, directlySelectedFiles, project)) {
                        try {
                            content.append(FileContentUtils.fileToString(file))
                            processedFiles.add(file)
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                            Notifications.Bus.notify(
                                Notification("com.saturnribbon", "Error", "Failed to process ${file.path}: ${ex.message}", NotificationType.ERROR),
                                project
                            )
                        }
                    }
                }
            }
        }
    }

    private fun shouldProcessFile(
        file: VirtualFile,
        directlySelectedFiles: Set<VirtualFile>,
        project: Project
    ): Boolean {
        // Always process directly selected files regardless of type
        if (directlySelectedFiles.contains(file)) {
            return true
        }

        // Skip hidden directories and their contents
        if (isInHiddenDirectory(file) && !directlySelectedFiles.contains(file.parent)) {
            return false
        }

        // Check if file is in excluded files list
        if (file.name in Exclusions.EXCLUDED_FILES) {
            return false
        }

        // Check file extension
        val extension = file.extension?.lowercase() ?: ""

        // Skip binary and image files
        if (extension in Exclusions.BINARY_EXTENSIONS || extension in Exclusions.IMAGE_EXTENSIONS) {
            return false
        }
        // Skip files that are ignored by git
        val fileStatusManager = FileStatusManager.getInstance(project)
        if (fileStatusManager.getStatus(file) == FileStatus.IGNORED) {
            return false
        }
        // Skip files larger than 1MB
        if (file.length > Exclusions.MAX_FILE_SIZE_BYTES) {
            return false
        }
        return true
    }

    private fun isInHiddenDirectory(file: VirtualFile): Boolean {
        var current: VirtualFile? = file
        while (current != null) {
            if (current.name.startsWith(".")) {
                return true
            }
            current = current.parent
        }
        return false
    }

    override fun update(e: AnActionEvent) {
//        val selectedFiles: Array<VirtualFile>? = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
//        e.presentation.isEnabled = !selectedFiles.isNullOrEmpty()
        e.presentation.isEnabled = true
    }
}