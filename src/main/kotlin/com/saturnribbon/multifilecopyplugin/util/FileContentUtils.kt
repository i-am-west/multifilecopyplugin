package com.saturnribbon.multifilecopyplugin.util

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.StringSelection

object FileContentUtils {
    fun copyToClipboard(content: String) {
        val stringSelection = StringSelection(content)
        CopyPasteManager.getInstance().setContents(stringSelection)
    }

    fun fileToString(file: VirtualFile): String {
        return "----- ${file.path} -----\n" + String(file.contentsToByteArray(), file.charset)
    }
    
    /**
     * Gets the selected files from an AnActionEvent.
     * If no files are selected, returns an empty array.
     */
    fun getSelectedFiles(e: AnActionEvent): Array<VirtualFile> {
        return e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: emptyArray()
    }
}
