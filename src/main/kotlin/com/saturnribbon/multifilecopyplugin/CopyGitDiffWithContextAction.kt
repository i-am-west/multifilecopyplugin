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
import org.jetbrains.kotlin.psi.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.regex.Pattern

// Represents a method call with its class information
data class MethodCallInfo(
    val className: String?,
    val methodName: String,
    val sourceFile: String,
    val sourceLine: Int
)

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
            val methodCalls = mutableListOf<MethodCallInfo>()

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
                    val modifiedLineNumbers = extractModifiedLineNumbers(diffOutput)
                    
                    if (modifiedLineNumbers.isNotEmpty() && file.exists()) {
                        // Use PSI to analyze the file for method calls
                        analyzeFileForMethodCalls(project, filePath, modifiedLineNumbers, methodCalls)
                    }
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
            
            // Find method implementations for the identified method calls
            val referencedMethods = findMethodImplementations(project, methodCalls, modifiedFilePaths)
            
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
     * Extracts the line numbers of modified lines from diff output
     */
    private fun extractModifiedLineNumbers(diffOutput: String): Map<Int, String> {
        val modifiedLines = mutableMapOf<Int, String>()
        val linePattern = Pattern.compile("^@@ -(\\d+),\\d+ \\+(\\d+),\\d+ @@.*$", Pattern.MULTILINE)
        
        var currentLine = 0
        
        val lines = diffOutput.lines()
        var lineIndex = 0
        
        while (lineIndex < lines.size) {
            val line = lines[lineIndex]
            
            if (line.startsWith("@@")) {
                // Parse hunk header
                val matcher = linePattern.matcher(line)
                if (matcher.matches()) {
                    currentLine = matcher.group(2).toInt()
                }
            } else if (line.startsWith("+") && !line.startsWith("+++")) {
                // This is an added line (not the file header)
                val content = line.substring(1)
                modifiedLines[currentLine] = content
                currentLine++
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                // This is a removed line, don't increment current line
            } else if (!line.startsWith("\\")) {
                // Line with context, increment counter
                currentLine++
            }
            
            lineIndex++
        }
        
        return modifiedLines
    }
    
    /**
     * Uses PSI to analyze a file for method calls in the modified lines
     */
    private fun analyzeFileForMethodCalls(
        project: Project,
        filePath: String,
        modifiedLineNumbers: Map<Int, String>,
        methodCalls: MutableList<MethodCallInfo>
    ) {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(File(project.basePath, filePath).absolutePath)
            ?: return
        
        val psiManager = PsiManager.getInstance(project)
        val psiFile = psiManager.findFile(virtualFile) ?: return
        
        when {
            psiFile is PsiJavaFile -> {
                analyzeJavaFile(psiFile, modifiedLineNumbers, methodCalls, filePath)
            }
            psiFile is KtFile -> {
                analyzeKotlinFile(psiFile, modifiedLineNumbers, methodCalls, filePath)
            }
        }
    }
    
    /**
     * Analyzes a Java file for method calls in the modified lines
     */
    private fun analyzeJavaFile(
        psiFile: PsiJavaFile,
        modifiedLineNumbers: Map<Int, String>,
        methodCalls: MutableList<MethodCallInfo>,
        filePath: String
    ) {
        // Find all method call expressions in the file
        val methodCallExpressions = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethodCallExpression::class.java)
        
        for (methodCall in methodCallExpressions) {
            val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile) ?: continue
            val lineNumber = document.getLineNumber(methodCall.textOffset) + 1 // +1 because line numbers are 0-based
            
            // Check if this method call is in a modified line
            if (modifiedLineNumbers.containsKey(lineNumber)) {
                val methodName = methodCall.methodExpression.referenceName ?: continue
                
                // Skip common methods
                if (isCommonMethod(methodName)) continue
                
                // Try to determine the class name
                var className: String? = null
                
                // Check if the method is called on a qualifier
                val qualifier = methodCall.methodExpression.qualifierExpression
                if (qualifier != null) {
                    // Try to resolve the type of the qualifier
                    when (qualifier) {
                        is PsiReferenceExpression -> {
                            // This might be a direct class reference (StaticClass.method()) or a variable
                            val resolved = qualifier.resolve()
                            when (resolved) {
                                is PsiClass -> className = resolved.qualifiedName
                                is PsiVariable -> className = resolved.type.canonicalText
                            }
                        }
                        is PsiLiteralExpression -> {
                            // For something like "string".equals(), get the type of the literal
                            className = qualifier.type?.canonicalText
                        }
                        is PsiMethodCallExpression -> {
                            // For something like obj.getList().add(), get the return type of the method
                            className = qualifier.type?.canonicalText
                        }
                    }
                } else {
                    // No qualifier, might be a method in the same class or a static import
                    // Try to get the containing class
                    val containingClass = PsiTreeUtil.getParentOfType(methodCall, PsiClass::class.java)
                    className = containingClass?.qualifiedName
                }
                
                // If we found both a class and a method name, add to our list
                if (methodName.isNotEmpty()) {
                    methodCalls.add(MethodCallInfo(className, methodName, filePath, lineNumber))
                }
            }
        }
    }
    
    /**
     * Analyzes a Kotlin file for method calls in the modified lines
     */
    private fun analyzeKotlinFile(
        psiFile: KtFile,
        modifiedLineNumbers: Map<Int, String>,
        methodCalls: MutableList<MethodCallInfo>,
        filePath: String
    ) {
        // Find all call expressions in the file
        val callExpressions = PsiTreeUtil.findChildrenOfType(psiFile, KtCallExpression::class.java)
        
        for (callExpr in callExpressions) {
            val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile) ?: continue
            val lineNumber = document.getLineNumber(callExpr.textOffset) + 1 // +1 because line numbers are 0-based
            
            // Check if this method call is in a modified line
            if (modifiedLineNumbers.containsKey(lineNumber)) {
                val methodName = callExpr.calleeExpression?.text ?: continue
                
                // Skip common methods
                if (isCommonMethod(methodName)) continue
                
                // Try to determine the class name
                var className: String? = null
                
                // Check if this is a qualified call (obj.method())
                val parent = callExpr.parent
                if (parent is KtDotQualifiedExpression) {
                    // This is a call like "obj.method()"
                    val receiver = parent.receiverExpression
                    // Try to find a reference to a class or variable
                    if (receiver is KtNameReferenceExpression) {
                        // Look for variable declarations or parameters
                        val reference = receiver.references.firstOrNull()
                        val resolved = reference?.resolve()
                        
                        when (resolved) {
                            is KtProperty -> {
                                // For a property, try to get the type
                                className = resolved.typeReference?.text
                                
                                // If the type is not explicitly stated, try to infer it from initializer
                                if (className == null && resolved.initializer != null) {
                                    // This is just a basic check - real type inference would be more complex
                                    when (val initializer = resolved.initializer) {
                                        is KtCallExpression -> {
                                            // Check for constructor calls like "val x = SomeClass()"
                                            className = initializer.calleeExpression?.text
                                        }
                                    }
                                }
                            }
                            is KtParameter -> className = resolved.typeReference?.text
                            is KtClass -> className = resolved.fqName?.asString()
                            is PsiClass -> className = resolved.qualifiedName
                            is PsiMethod -> {
                                // For method return types
                                className = (resolved as? PsiMethod)?.returnType?.canonicalText
                            }
                            is PsiField -> {
                                // For fields
                                className = (resolved as? PsiField)?.type?.canonicalText
                            }
                        }
                    }
                    
                    // If we still don't have a class name, try to get more info about the receiver
                    if (className == null) {
                        when (receiver) {
                            is KtStringTemplateExpression -> {
                                // String literals call methods like "hello".length
                                className = "java.lang.String"
                            }
                            is KtConstantExpression -> {
                                // Numeric literals like 42.toString()
                                when (receiver.text) {
                                    "true", "false" -> className = "java.lang.Boolean"
                                    else -> {
                                        if (receiver.text.contains(".")) {
                                            className = "java.lang.Double"
                                        } else {
                                            className = "java.lang.Integer"
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // This could be a local method call or a top-level function
                    // Try to find the containing class
                    val containingClass = PsiTreeUtil.getParentOfType(callExpr, KtClass::class.java)
                    className = containingClass?.fqName?.asString()
                    
                    // If no containing class, this might be a file-level function or an imported function
                    if (className == null) {
                        // Check if this is a call to a function in the same file
                        val fileDeclarations = psiFile.declarations
                        for (declaration in fileDeclarations) {
                            if (declaration is KtNamedFunction && declaration.name == methodName) {
                                // Use the file's package name as the "class" name for file-level functions
                                className = psiFile.packageFqName.asString()
                                break
                            }
                        }
                    }
                }
                
                // If we found a method name, add to our list (even if class name is null)
                if (methodName.isNotEmpty()) {
                    methodCalls.add(MethodCallInfo(className, methodName, filePath, lineNumber))
                }
            }
        }
    }
    
    /**
     * Checks if a method name is a common method to be ignored
     */
    private fun isCommonMethod(methodName: String): Boolean {
        val commonMethods = setOf(
            "equals", "hashCode", "toString", "valueOf", "values",
            "get", "set", "add", "remove", "contains", "size", "isEmpty",
            "println", "print"
        )
        
        return commonMethods.contains(methodName) || 
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
        methodCalls: List<MethodCallInfo>, 
        modifiedFilePaths: Set<String>
    ): Map<String, String> {
        val referencedMethods = mutableMapOf<String, String>()
        val shortNamesCache = PsiShortNamesCache.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)
        
        // Process each method call individually rather than by class
        for (methodCall in methodCalls) {
            val methodName = methodCall.methodName
            
            // Skip common methods
            if (isCommonMethod(methodName)) continue
            
            // First try using the specific class if we have one
            if (methodCall.className != null) {
                val psiClass = findClassInProject(project, methodCall.className)
                
                if (psiClass != null) {
                    // Skip if class's file is in the modified files
                    val classFile = psiClass.containingFile?.virtualFile
                    if (classFile != null) {
                        val classFilePath = classFile.path
                        
                        // Only skip if the class's file is in the modified files
                        if (modifiedFilePaths.contains(classFilePath)) {
                            continue
                        }
                        
                        // Add methods from this class
                        val methods = psiClass.findMethodsByName(methodName, false)
                        addMethodsToResults(methods, referencedMethods, classFilePath)
                        
                        // If we found methods, continue to the next call
                        if (methods.isNotEmpty()) continue
                    }
                }
            }
            
            // If we get here, either:
            // 1. We didn't have a class name
            // 2. We couldn't find the class
            // 3. The class didn't have the method
            
            // Try the short names cache for efficiency
            val methodsByName = shortNamesCache.getMethodsByName(methodName, scope)
            
            for (method in methodsByName) {
                val containingClass = method.containingClass ?: continue
                val containingFile = containingClass.containingFile?.virtualFile ?: continue
                
                // Skip libraries and modified files
                if (!ProjectFileIndex.getInstance(project).isInContent(containingFile)) {
                    continue
                }
                
                val containingFilePath = containingFile.path
                if (modifiedFilePaths.contains(containingFilePath)) {
                    continue
                }
                
                // Add this method
                val methodKey = "${containingFilePath}#${method.name}#${containingClass.name}"
                if (!referencedMethods.containsKey(methodKey)) {
                    referencedMethods[methodKey] = formatMethodReference(method, containingFilePath)
                }
            }
        }
        
        return referencedMethods
    }
    
    /**
     * Adds methods to the results map
     */
    private fun addMethodsToResults(
        methods: Array<PsiMethod>,
        referencedMethods: MutableMap<String, String>,
        containingFilePath: String
    ) {
        for (method in methods) {
            val containingClass = method.containingClass ?: continue
            val methodKey = "${containingFilePath}#${method.name}#${containingClass.name}"
            if (!referencedMethods.containsKey(methodKey)) {
                referencedMethods[methodKey] = formatMethodReference(method, containingFilePath)
            }
        }
    }
    
    /**
     * Finds a class in the project by its qualified name
     */
    private fun findClassInProject(project: Project, className: String): PsiClass? {
        val scope = GlobalSearchScope.projectScope(project)
        return JavaPsiFacade.getInstance(project).findClass(className, scope)
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
            builder.append("\n\n----- Referenced Methods (from unmodified files) -----\n")
            builder.append("The following method implementations were called from the modified code:\n\n")
            
            // Group methods by class
            val methodsByClass = referencedMethods.values.groupBy { methodText ->
                val classLine = methodText.lines().find { it.startsWith("Class: ") }
                classLine?.substringAfter("Class: ") ?: "Unknown"
            }
            
            // Output methods grouped by class
            for ((className, methods) in methodsByClass.entries.sortedBy { it.key }) {
                builder.append("\n## Class: $className\n\n")
                for (methodText in methods) {
                    builder.append(methodText)
                    builder.append("\n")
                }
            }
        } else {
            builder.append("\n\n----- No Referenced Methods -----\n")
            builder.append("No methods from unmodified files were called in the modified code.\n")
        }
        
        return builder.toString()
    }
}
