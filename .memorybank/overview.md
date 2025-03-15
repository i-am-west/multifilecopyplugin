# Multi-File Copy Plugin Overview

## Project Purpose
The Multi-File Copy Plugin is an IntelliJ IDEA plugin designed to make it easy for users to copy the contents of multiple files to the clipboard. This is particularly useful for sharing code with AI agents or other developers, as it preserves file paths and content in a structured format.

## Core Features
1. **Copy Selected Files** - Allows users to select multiple files in the Project view and copy their contents to the clipboard
2. **Copy Open Tabs** - Enables copying the contents of all currently open editor tabs
3. **Copy Project Structure** - Extracts and copies the structural elements (classes, methods, fields) of the entire project without implementation details
4. **Copy Simplified Structure** - Creates a token-efficient version of the project structure by omitting private members, annotations, and certain class types
5. **File Path Preservation** - Each file's content is preceded by its file path for context
6. **Smart File Filtering** - Automatically excludes binary files, large files, and certain system files

## Technical Architecture

### Main Components
- **Actions**:
  - `CopySelectedFilesAction.kt` - Handles copying selected files from the Project view
  - `CopyOpenTabsAction.kt` - Handles copying all open editor tabs
  - `CopyProjectStructureAction.kt` - Handles extracting and copying the project's code structure
  - `CopySimplifiedStructureAction.kt` - Handles extracting and copying a simplified version of the project structure

- **Utilities**:
  - `FileContentUtils.kt` - Provides clipboard operations and file content formatting
  - `FileProcessingUtil.kt` - Core logic for processing files, handling directories, and applying exclusion rules

- **Constants**:
  - `Exclusions.kt` - Defines excluded file types, directories, and size limits

### Plugin Configuration
- Registered in `plugin.xml` with actions added to context menus
- Keyboard shortcuts:
  - Copy Selected Files: `Shift+Ctrl+Alt+C`
  - Copy Open Tabs: `Shift+Ctrl+T`
  - Copy Project Structure: `Shift+Ctrl+Alt+S`
  - Copy Simplified Structure: `Shift+Ctrl+Alt+P`

## Technical Details
- Written in Kotlin
- Built with Gradle
- Compatible with IntelliJ IDEA 2024.1 (build 241) through 2024.3.x
- Uses IntelliJ Platform APIs for file access and clipboard operations
- Uses PSI (Program Structure Interface) for code structure analysis

## File Processing Logic
1. Files are processed recursively, handling directories and nested files
2. Smart filtering excludes:
   - Binary files (executables, archives, etc.)
   - Image files
   - Files larger than 1MB
   - Files in hidden directories (starting with `.`)
   - Specific excluded files like package-lock.json, build files, etc.
   - Test files and directories
3. VCS integration to respect ignored files

## PSI Processing Logic
1. For the project structure feature, the plugin:
   - Traverses the entire project directory structure
   - Uses PSI to parse code files (Java, Kotlin)
   - Extracts structural elements (classes, methods, fields) without implementation details
   - Formats the output with file paths and proper indentation
   - Excludes test classes and test directories
   - Handles both class and object declarations in Kotlin

## Simplified Structure Logic
1. The simplified structure feature further reduces token usage by:
   - Omitting all private members and methods
   - Removing annotations
   - Skipping Module, Component, Config, and Configuration classes
   - Removing redundant modifiers (e.g., "public" since it's the default)
   - Simplifying type declarations (e.g., "String" instead of "public static final String")
   - Focusing only on the public API of the codebase

## Test Exclusion Logic
1. The plugin identifies and excludes test-related code:
   - Skips common test directories (`test`, `tests`, `src/test`, etc.)
   - Skips files with test-related names (`*Test.java`, `Test*.kt`, etc.)
   - Checks both file names and directory paths to identify test code

## User Experience
- Right-click on selected files in Project view → "Copy Multiple Files to Clipboard"
- Access from Edit menu or editor tab context menu → "Copy Open Tabs to Clipboard"
- Right-click on project in Project view → "Copy Project Structure to Clipboard"
- Right-click on project in Project view → "Copy Simplified Structure to Clipboard"
- Keyboard shortcuts for quick access
- Notification system for error handling

## Development Notes
- Built with IntelliJ Platform Plugin Template
- Uses Kotlin 1.9.25
- Targets Java 17 