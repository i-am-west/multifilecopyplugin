package com.saturnribbon.multifilecopyplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTreeUtil
import com.saturnribbon.multifilecopyplugin.util.FileContentUtils
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtClass
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.regex.Pattern

class CopyGitDiffWithContextAction : AnAction("Copy Git Diff with Context", "Copies git diff with context of methods called in modified lines", null) {
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
                    Notification("GitDiff", "Copy Git Diff with Context", "No modified files found between the current branch and master.", NotificationType.INFORMATION),
                    project
                )
                return
            }

            val outputBuilder = StringBuilder()
            val modifiedFilePaths = modifiedFiles.map { File(baseDir, it).absolutePath }.toSet()
            val methodCallsMap = mutableMapOf<String, MutableSet<String>>()

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
                    
                    // Extract method calls from modified lines
                    extractMethodCalls(diffOutput, filePath, methodCallsMap)
                }
            }

            val diffOutput = outputBuilder.toString().trim()
            
            if (diffOutput.isBlank()) {
                Notifications.Bus.notify(
                    Notification("GitDiff", "Copy Git Diff with Context", "No differences found.", NotificationType.INFORMATION),
                    project
                )
                return
            }
            
            // Find method implementations
            val referencedMethods = findMethodImplementations(project, methodCallsMap, modifiedFilePaths)
            
            // Build final output with diff and referenced methods
            val finalOutput = buildFinalOutput(diffOutput, referencedMethods)
            
            FileContentUtils.copyToClipboard(finalOutput)
            Notifications.Bus.notify(
                Notification("GitDiff", "Copy Git Diff with Context", "Git diff with method context copied to clipboard.", NotificationType.INFORMATION),
                project
            )
        } catch (ex: Exception) {
            Notifications.Bus.notify(
                Notification("GitDiff", "Copy Git Diff with Context", "Error occurred: ${ex.message}", NotificationType.ERROR),
                project
            )
        }
    }
    
    /**
     * Extracts method calls from modified lines in the diff output
     */
    private fun extractMethodCalls(diffOutput: String, filePath: String, methodCallsMap: MutableMap<String, MutableSet<String>>) {
        // Pattern to match added lines in the diff (starting with +)
        val addedLinePattern = Pattern.compile("^\\+(?!\\+\\+)(.*)$", Pattern.MULTILINE)
        val matcher = addedLinePattern.matcher(diffOutput)
        
        // Pattern to match method calls - handles both object.method() and method() patterns
        // Also handles Kotlin extension functions like string.extension() and extension(string)
        val methodCallPattern = Pattern.compile(
            "(?:\\b([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)*)\\s*\\()|" + // object.method() pattern
            "(?:\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\()"                                   // method() pattern
        )
        
        // Process added lines (starting with +)
        processModifiedLines(diffOutput, "^\\+(?!\\+\\+)(.*)$", methodCallPattern, filePath, methodCallsMap)
        
        // Also process modified context lines (those not starting with + or -)
        // These are lines that provide context but might contain method calls we want to include
        processModifiedLines(diffOutput, "^(?![+\\-])(.*)$", methodCallPattern, filePath, methodCallsMap)
    }
    
    /**
     * Processes modified lines in the diff output to extract method calls
     */
    private fun processModifiedLines(
        diffOutput: String,
        linePattern: String,
        methodCallPattern: Pattern,
        filePath: String,
        methodCallsMap: MutableMap<String, MutableSet<String>>
    ) {
        val pattern = Pattern.compile(linePattern, Pattern.MULTILINE)
        val matcher = pattern.matcher(diffOutput)
        
        while (matcher.find()) {
            val line = matcher.group(1)
            if (line.isNullOrBlank() || line.startsWith("@@") || line.startsWith("diff ") || 
                line.startsWith("index ") || line.startsWith("--- ") || line.startsWith("+++ ")) {
                continue // Skip diff metadata lines
            }
            
            val methodMatcher = methodCallPattern.matcher(line)
            
            while (methodMatcher.find()) {
                // Group 1 is for object.method pattern, Group 2 is for simple method pattern
                val fullMethodCall = methodMatcher.group(1) ?: methodMatcher.group(2)
                
                if (fullMethodCall != null) {
                    // For object.method pattern, extract just the method name
                    val methodName = if (fullMethodCall.contains('.')) {
                        fullMethodCall.substringAfterLast('.')
                    } else {
                        fullMethodCall
                    }
                    
                    // Skip common keywords and constructors
                    if (!isCommonKeywordOrConstructor(methodName)) {
                        if (!methodCallsMap.containsKey(filePath)) {
                            methodCallsMap[filePath] = mutableSetOf()
                        }
                        methodCallsMap[filePath]?.add(methodName)
                    }
                }
            }
        }
    }
    
    /**
     * Checks if a method name is a common keyword or constructor
     */
    private fun isCommonKeywordOrConstructor(methodName: String): Boolean {
        val commonKeywords = setOf(
            "if", "for", "while", "switch", "catch", "super", "this",
            "equals", "hashCode", "toString", "valueOf", "values"
        )
        
        return commonKeywords.contains(methodName) || 
               methodName.startsWith("get") || 
               methodName.startsWith("set") || 
               methodName.startsWith("is") ||
               methodName[0].isUpperCase() // Likely a constructor
    }
    
    /**
     * Finds method implementations using PSI
     */
    private fun findMethodImplementations(
        project: Project, 
        methodCallsMap: Map<String, Set<String>>, 
        modifiedFilePaths: Set<String>
    ): Map<String, String> {
        val psiManager = PsiManager.getInstance(project)
        val referencedMethods = mutableMapOf<String, String>()
        
        for ((filePath, methodNames) in methodCallsMap) {
            val virtualFile = LocalFileSystem.getInstance().findFileByPath(File(project.basePath, filePath).absolutePath)
                ?: continue
            
            val psiFile = psiManager.findFile(virtualFile) ?: continue
            
            for (methodName in methodNames) {
                findMethodsInProject(project, methodName).forEach { method ->
                    val containingFile = method.containingFile.virtualFile
                    val containingFilePath = containingFile.path
                    
                    // Skip methods from files that are already in the diff
                    if (!modifiedFilePaths.contains(containingFilePath)) {
                        val methodKey = "${containingFilePath}#${method.name}"
                        if (!referencedMethods.containsKey(methodKey)) {
                            referencedMethods[methodKey] = formatMethodReference(method, containingFilePath)
                        }
                    }
                }
            }
        }
        
        return referencedMethods
    }
    
    /**
     * Finds methods with the given name in the project
     */
    private fun findMethodsInProject(project: Project, methodName: String): List<PsiMethod> {
        val foundMethods = mutableListOf<PsiMethod>()
        val scope = GlobalSearchScope.projectScope(project)
        val psiManager = PsiManager.getInstance(project)
        
        try {
            // Use PsiShortNamesCache for efficient method lookup by name
            val shortNamesCache = PsiShortNamesCache.getInstance(project)
            val javaMethods = shortNamesCache.getMethodsByName(methodName, scope)
            foundMethods.addAll(javaMethods)
            
            // If we still need more methods, scan the project files
            if (foundMethods.isEmpty()) {
                val fileIndex = ProjectFileIndex.getInstance(project)
                
                fileIndex.iterateContent({ virtualFile ->
                    if (!virtualFile.isDirectory) {
                        when (virtualFile.extension) {
                            "java" -> {
                                // Process Java files
                                val psiFile = psiManager.findFile(virtualFile)
                                if (psiFile != null) {
                                    PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java).forEach { method ->
                                        if (method.name == methodName && !foundMethods.contains(method)) {
                                            foundMethods.add(method)
                                        }
                                    }
                                }
                            }
                            "kt" -> {
                                // Process Kotlin files
                                val psiFile = psiManager.findFile(virtualFile)
                                if (psiFile != null && psiFile.language == KotlinLanguage.INSTANCE) {
                                    // Handle Kotlin files specifically
                                    processKotlinFile(psiFile as? KtFile, methodName, foundMethods)
                                }
                            }
                        }
                    }
                    true
                }, scope)
            }
        } catch (e: Exception) {
            // Log the error but continue with what we have
            Notifications.Bus.notify(
                Notification("GitDiff", "Method Search", "Error searching for methods: ${e.message}", NotificationType.WARNING),
                project
            )
        }
        
        return foundMethods
    }
    
    /**
     * Process a Kotlin file to find methods with the given name
     */
    private fun processKotlinFile(ktFile: KtFile?, methodName: String, foundMethods: MutableList<PsiMethod>) {
        if (ktFile == null) return
        
        try {
            // Find top-level functions
            ktFile.declarations.forEach { declaration ->
                if (declaration is KtNamedFunction && declaration.name == methodName) {
                    // Convert KtNamedFunction to PsiMethod if possible
                    (declaration.navigationElement as? PsiMethod)?.let { 
                        if (!foundMethods.contains(it)) {
                            foundMethods.add(it)
                        }
                    }
                } else if (declaration is KtClass) {
                    // Find methods in classes
                    declaration.declarations.forEach { classMember ->
                        if (classMember is KtNamedFunction && classMember.name == methodName) {
                            // Convert KtNamedFunction to PsiMethod if possible
                            (classMember.navigationElement as? PsiMethod)?.let {
                                if (!foundMethods.contains(it)) {
                                    foundMethods.add(it)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore errors in Kotlin-specific processing
        }
    }
    
    /**
     * Formats a method reference for inclusion in the output
     */
    private fun formatMethodReference(method: PsiMethod, filePath: String): String {
        val className = (method.containingClass?.qualifiedName ?: method.containingClass?.name) ?: "Unknown"
        val methodText = method.text
        val packageName = method.containingClass?.qualifiedName?.substringBeforeLast('.', "") ?: ""
        
        val builder = StringBuilder()
        builder.append("File: $filePath\n")
        
        if (packageName.isNotEmpty()) {
            builder.append("Package: $packageName\n")
        }
        
        builder.append("Class: $className\n")
        builder.append("Method: ${method.name}\n")
        
        // Add parameter information for better context
        builder.append("Signature: ${method.name}(")
        builder.append(method.parameterList.parameters.joinToString(", ") { 
            "${it.name}: ${it.type.presentableText}" 
        })
        builder.append(")")
        
        // Add return type if available
        method.returnType?.let {
            builder.append(": ${it.presentableText}")
        }
        
        builder.append("\n\n")
        builder.append(methodText)
        builder.append("\n")
        
        return builder.toString()
    }
    
    /**
     * Builds the final output with diff and referenced methods
     */
    private fun buildFinalOutput(diffOutput: String, referencedMethods: Map<String, String>): String {
        val builder = StringBuilder()
        
        // Add the diff output
        builder.append("----- Git Diff -----\n")
        builder.append(diffOutput)
        
        // Add referenced methods if any
        if (referencedMethods.isNotEmpty()) {
            builder.append("\n\n----- Referenced Methods -----\n")
            for (methodText in referencedMethods.values) {
                builder.append("\n")
                builder.append(methodText)
            }
        }
        
        return builder.toString()
    }
}
