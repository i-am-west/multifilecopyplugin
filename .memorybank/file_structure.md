# Multi-File Copy Plugin - File Structure

## Project Structure Overview

```
multifilecopyplugin/
├── .idea/                  # IntelliJ IDEA project settings
├── .run/                   # Run configurations
├── gradle/                 # Gradle wrapper files
├── src/                    # Source code
│   └── main/
│       ├── kotlin/         # Kotlin source files
│       │   └── com/saturnribbon/multifilecopyplugin/
│       │       ├── constants/
│       │       │   └── Exclusions.kt
│       │       ├── util/
│       │       │   ├── FileContentUtils.kt
│       │       │   └── FileProcessingUtil.kt
│       │       ├── CopyOpenTabsAction.kt
│       │       └── CopySelectedFilesAction.kt
│       └── resources/
│           ├── META-INF/
│           │   └── plugin.xml
│           └── messages.properties
├── .gitignore
├── build.gradle.kts        # Gradle build configuration
├── gradle.properties       # Gradle properties
├── gradlew                 # Gradle wrapper script (Unix)
├── gradlew.bat             # Gradle wrapper script (Windows)
├── LICENSE                 # MIT License
├── README.md               # Project documentation
└── settings.gradle.kts     # Gradle settings
```

## Key Files and Their Purposes

### Plugin Configuration
- **plugin.xml**: Defines plugin metadata, dependencies, and registers actions
- **build.gradle.kts**: Configures the build process, dependencies, and plugin packaging

### Core Functionality
- **CopySelectedFilesAction.kt**: Action for copying selected files from Project view
- **CopyOpenTabsAction.kt**: Action for copying all open editor tabs

### Utilities
- **FileContentUtils.kt**: Handles clipboard operations and file content formatting
- **FileProcessingUtil.kt**: Core logic for processing files and directories

### Constants
- **Exclusions.kt**: Defines file types, directories, and size limits to exclude

### Resources
- **messages.properties**: Internationalization strings
- **plugin.xml**: Plugin configuration and action registration

### Build Configuration
- **build.gradle.kts**: Gradle build script
- **gradle.properties**: Gradle and plugin properties
- **settings.gradle.kts**: Gradle settings 