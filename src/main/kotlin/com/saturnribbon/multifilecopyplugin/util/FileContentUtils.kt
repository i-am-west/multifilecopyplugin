package com.saturnribbon.multifilecopyplugin.util

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
}
