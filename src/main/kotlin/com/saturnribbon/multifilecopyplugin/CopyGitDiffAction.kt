package com.saturnribbon.multifilecopyplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.saturnribbon.multifilecopyplugin.util.FileContentUtils
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class CopyGitDiffAction : AnAction("Copy Git Diff to Clipboard", "Copies git diff with full context between the current branch and master", null) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return
        val baseDir = File(basePath)

        try {
            // Get list of modified files between the current branch and master
            val modifiedFilesProcess = ProcessBuilder("git", "diff", "--name-only", "master")
                .directory(baseDir)
                .redirectErrorStream(true)
                .start()
            val modifiedFilesReader = BufferedReader(InputStreamReader(modifiedFilesProcess.inputStream))
            val modifiedFiles = modifiedFilesReader.readLines().filter { it.isNotBlank() }
            modifiedFilesProcess.waitFor()

            if (modifiedFiles.isEmpty()) {
                Notifications.Bus.notify(
                    Notification("GitDiff", "Copy Git Diff", "No modified files found between the current branch and master.", NotificationType.INFORMATION),
                    project
                )
                return
            }

            val outputBuilder = StringBuilder()

            // Process each modified file with dynamic context
            for (filePath in modifiedFiles) {
                val file = File(baseDir, filePath)
                
                // Determine context size based on file existence
                val contextParam = if (file.exists()) {
                    // Count lines in the file using Java/Kotlin (cross-platform)
                    val lineCount = try {
                        file.bufferedReader().use { reader ->
                            reader.lines().count().toInt()
                        }
                    } catch (e: Exception) {
                        // Default to 1000 if we can't count lines
                        1000
                    }
                    lineCount.toString()
                } else {
                    // For deleted files, use a large fixed context
                    "1000"
                }
                
                // Get diff with appropriate context
                val fileDiffProcess = ProcessBuilder("git", "diff", "master", "-U$contextParam", "--", filePath)
                    .directory(baseDir)
                    .redirectErrorStream(true)
                    .start()
                val fileDiffReader = BufferedReader(InputStreamReader(fileDiffProcess.inputStream))
                val diffOutput = fileDiffReader.readText()
                fileDiffProcess.waitFor()
                
                if (diffOutput.isNotBlank()) {
                    outputBuilder.append(diffOutput)
                    outputBuilder.append("\n\n")
                }
            }

            val finalOutput = outputBuilder.toString().trim()
            
            if (finalOutput.isBlank()) {
                Notifications.Bus.notify(
                    Notification("GitDiff", "Copy Git Diff", "No differences found.", NotificationType.INFORMATION),
                    project
                )
            } else {
                FileContentUtils.copyToClipboard(finalOutput)
                Notifications.Bus.notify(
                    Notification("GitDiff", "Copy Git Diff", "Git diff with full context copied to clipboard.", NotificationType.INFORMATION),
                    project
                )
            }
        } catch (ex: Exception) {
            Notifications.Bus.notify(
                Notification("GitDiff", "Copy Git Diff", "Error occurred: ${ex.message}", NotificationType.ERROR),
                project
            )
        }
    }
}
