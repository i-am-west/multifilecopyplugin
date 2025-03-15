package com.saturnribbon.multifilecopyplugin.util

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.saturnribbon.multifilecopyplugin.constants.Exclusions

object FileProcessingUtil {
    fun processFiles(
        files: Array<VirtualFile>,
        project: Project,
        directlySelectedFiles: Set<VirtualFile> = emptySet()
    ): String {
        val content = StringBuilder()
        val processedFiles = mutableSetOf<VirtualFile>()

        processFilesRecursively(files, content, project, directlySelectedFiles, processedFiles)
        return content.toString()
    }

    private fun processFilesRecursively(
        files: Array<VirtualFile>,
        content: StringBuilder,
        project: Project,
        directlySelectedFiles: Set<VirtualFile>,
        processedFiles: MutableSet<VirtualFile>
    ) {
        for (file in files) {
            when {
                file.isDirectory -> handleDirectory(file, content, project, directlySelectedFiles, processedFiles)
                else -> handleFile(file, content, project, directlySelectedFiles, processedFiles)
            }
        }
    }

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

    private fun handleFile(
        file: VirtualFile,
        content: StringBuilder,
        project: Project,
        directlySelectedFiles: Set<VirtualFile>,
        processedFiles: MutableSet<VirtualFile>
    ) {
        if (!processedFiles.contains(file) && shouldProcessFile(file, directlySelectedFiles, project)) {
            try {
                content.append(FileContentUtils.fileToString(file, project))
                processedFiles.add(file)
            } catch (ex: Exception) {
                notifyError(project, file, ex)
            }
        }
    }

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

    private fun isInHiddenDirectory(file: VirtualFile): Boolean {
        var current: VirtualFile? = file
        while (current != null) {
            if (current.name.startsWith(".")) return true
            current = current.parent
        }
        return false
    }

    private fun notifyError(project: Project, file: VirtualFile, ex: Exception) {
        Notifications.Bus.notify(
            Notification(
                "FileProcessingUtil",
                "Error",
                "Failed to process ${file.path}: ${ex.message}",
                NotificationType.ERROR
            ),
            project
        )
    }
}
