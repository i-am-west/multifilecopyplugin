package com.saturnribbon.multifilecopyplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection

class CopyOpenTabsAction : AnAction("Copy Open Tabs to Clipboard") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Get all open files
        val openFiles = FileEditorManager.getInstance(project).openFiles

        val combinedContent = StringBuilder()

        for (file in openFiles) {
            try {
                val fileContent = String(file.contentsToByteArray(), file.charset)
                combinedContent.append("----- ${file.path} -----\n")
                combinedContent.append(fileContent).append("\n\n")
            } catch (ex: Exception) {
                // Handle error if needed
            }
        }

        // Copy to clipboard
        val stringSelection = StringSelection(combinedContent.toString())
        CopyPasteManager.getInstance().setContents(stringSelection)
    }

    override fun update(e: AnActionEvent) {
        // Enable the action only if we have a project and there are open files
        val project = e.project
        e.presentation.isEnabled = project != null &&
            FileEditorManager.getInstance(project).openFiles.isNotEmpty()
    }
}
