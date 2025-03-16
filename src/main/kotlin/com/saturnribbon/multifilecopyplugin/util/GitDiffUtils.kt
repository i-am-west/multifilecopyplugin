package com.saturnribbon.multifilecopyplugin.util

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object GitDiffUtils {
    fun getModifiedFiles(baseDir: File): List<String> {
        val modifiedFilesProcess = ProcessBuilder("git", "diff", "--name-only", "master")
            .directory(baseDir)
            .redirectErrorStream(true)
            .start()
        val modifiedFilesReader = BufferedReader(InputStreamReader(modifiedFilesProcess.inputStream))
        val modifiedFiles = modifiedFilesReader.readLines().filter { it.isNotBlank() }
        modifiedFilesProcess.waitFor()

        return modifiedFiles
    }

    fun getDiff(file: File, baseDir: File, filePath: String): String {
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

        return diffOutput
    }
}