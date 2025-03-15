package com.saturnribbon.multifilecopyplugin.util

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.StringSelection

object FileContentUtils {
    fun copyToClipboard(content: String) {
        val stringSelection = StringSelection(content)
        CopyPasteManager.getInstance().setContents(stringSelection)
    }

    fun fileToString(file: VirtualFile, project: Project): String {
        val relativePath = getRelativePath(file, project)
        val fileContent = String(file.contentsToByteArray(), file.charset)
        val trimmedContent = fileContent.trimEnd()
        return "----- $relativePath -----\n$trimmedContent\n\n"
    }
    
    /**
     * Gets the selected files from an AnActionEvent.
     * If no files are selected, returns an empty array.
     */
    fun getSelectedFiles(e: AnActionEvent): Array<VirtualFile> {
        return e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: emptyArray()
    }
    
    /**
     * Gets the relative path of a file in the project.
     */
    fun getRelativePath(file: VirtualFile, project: Project): String {
        val projectDir = project.basePath ?: return file.path
        return if (file.path.startsWith(projectDir)) {
            file.path.substring(projectDir.length + 1)
        } else {
            file.path
        }
    }
}
