package com.saturnribbon.multifilecopyplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.saturnribbon.multifilecopyplugin.util.FileContentUtils
import com.saturnribbon.multifilecopyplugin.util.GitDiffUtils
import java.io.File

class CopyGitDiffAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    
    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.text = "Copy Git Diff to Clipboard"
        e.presentation.description = "Copies git diff with full context between the current branch and master"
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return
        val baseDir = File(basePath)

        try {
            val modifiedFiles = GitDiffUtils.getModifiedFiles(baseDir)

            if (modifiedFiles.isEmpty()) {
                Notifications.Bus.notify(
                    Notification("FileProcessingUtil", "Copy Git Diff", "No modified files found between the current branch and master.", NotificationType.INFORMATION),
                    project
                )
                return
            }

            val outputBuilder = StringBuilder()

            // Process each modified file with dynamic context
            for (filePath in modifiedFiles) {
                val file = File(baseDir, filePath)
                val diffOutput = GitDiffUtils.getDiff(file, baseDir, filePath)
                
                if (diffOutput.isNotBlank()) {
                    outputBuilder.append(diffOutput)
                    outputBuilder.append("\n\n")
                }
            }

            val finalOutput = outputBuilder.toString().trim()
            
            if (finalOutput.isBlank()) {
                Notifications.Bus.notify(
                    Notification("FileProcessingUtil", "Copy Git Diff", "No differences found.", NotificationType.INFORMATION),
                    project
                )
            } else {
                FileContentUtils.copyToClipboard(finalOutput)
                Notifications.Bus.notify(
                    Notification("FileProcessingUtil", "Copy Git Diff", "Git diff with full context copied to clipboard.", NotificationType.INFORMATION),
                    project
                )
            }
        } catch (ex: Exception) {
            Notifications.Bus.notify(
                Notification("FileProcessingUtil", "Copy Git Diff", "Error occurred: ${ex.message}", NotificationType.ERROR),
                project
            )
        }
    }
}
