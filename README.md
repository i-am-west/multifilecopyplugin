# Multi-File Copy Plugin

An IntelliJ IDEA plugin that enhances your workflow by providing advanced file content copying capabilities. Perfect for sharing code with AI agents, team collaboration, or documentation purposes.

## Features

- **Multiple File Selection**: Copy contents of multiple selected files from the Project view
- **Open Tabs Copy**: Copy contents of all currently open editor tabs
- **Project Structure Copy**: Extract and copy structural elements of selected files without implementation details
- **Simplified Structure Copy**: Create token-efficient versions of code structure by omitting private members and unnecessary details
- **Smart Filtering**: Automatically excludes binary files, large files (>1MB), and system files
- **Path Preservation**: Each file's content is preceded by its file path for better context
- **Git Diff**: Copy git diff with full context between branches
- **Git Diff with Context**: Copy git diff and automatically include implementations of methods that are called in the modified code
- **Keyboard Shortcuts**:
  - Copy Selected Files: `Shift+Ctrl+Alt+C`
  - Copy Open Tabs: `Shift+Ctrl+T`
  - Copy Project Structure: `Shift+Ctrl+Alt+S`
  - Copy Simplified Structure: `Shift+Ctrl+Alt+P`
  - Copy Git Diff: `Shift+Ctrl+Alt+D`
  - Copy Git Diff with Context: `Shift+Ctrl+Alt+M`

## Installation

1. Download the plugin JAR from the [Releases](../../releases) page
2. In IntelliJ IDEA, go to **File > Settings > Plugins**
3. Click the gear icon (⚙️) and select **Install Plugin from Disk...**
4. Select the downloaded JAR file
5. Click **OK** and restart IntelliJ IDEA when prompted

## Usage

### Copy Selected Files
1. Select one or more files in the Project view
2. Right-click and choose **Copy Multiple Files to Clipboard**
   - Or use keyboard shortcut: `Shift+Ctrl+Alt+C`

### Copy Open Tabs
1. Access via **Edit > Copy Open Tabs to Clipboard**
   - Or use keyboard shortcut: `Shift+Ctrl+T`

### Copy Project Structure
1. Select files or directories in the Project view
2. Right-click and choose **Copy Project Structure to Clipboard**
   - Or use keyboard shortcut: `Shift+Ctrl+Alt+S`

### Copy Simplified Structure
1. Select files or directories in the Project view
2. Right-click and choose **Copy Simplified Structure to Clipboard**
   - Or use keyboard shortcut: `Shift+Ctrl+Alt+P`

### Copy Git Diff
1. Right-click in the Project view and choose **Copy Git Diff to Clipboard**
   - Or use keyboard shortcut: `Shift+Ctrl+Alt+D`

### Copy Git Diff with Context
1. Right-click in the Project view and choose **Copy Git Diff with Context**
   - Or use keyboard shortcut: `Shift+Ctrl+Alt+M`
   - This feature copies the diff and adds implementation of methods called in modified code

## Requirements

- IntelliJ IDEA 2024.1 (build 241) or later
- Java 17 or later

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
