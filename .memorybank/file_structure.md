# Multi-File Copy Plugin - File Structure

## Project Structure Overview

```
multifilecopyplugin/
├── .idea/                  # IntelliJ IDEA project settings
├── .memorybank/           # Project documentation and memory
│   ├── overview.md        # Project overview and technical details
│   ├── future_enhancements.md # Planned improvements
│   └── file_structure.md  # Project structure documentation
├── .run/                  # Run configurations
├── gradle/                # Gradle wrapper files
├── src/                   # Source code
│   └── main/
│       ├── kotlin/        # Kotlin source files
│       │   └── com/saturnribbon/multifilecopyplugin/
│       │       ├── actions/
│       │       │   ├── CopyOpenTabsAction.kt
│       │       │   ├── CopySelectedFilesAction.kt
│       │       │   ├── CopyProjectStructureAction.kt
│       │       │   └── CopySimplifiedStructureAction.kt
│       │       ├── constants/
│       │       │   └── Exclusions.kt
│       │       └── util/
│       │           ├── FileContentUtils.kt
│       │           └── FileProcessingUtil.kt
│       └── resources/
│           ├── META-INF/
│           │   └── plugin.xml
│           └── messages.properties
├── .gitignore
├── build.gradle.kts       # Gradle build configuration
├── gradle.properties      # Gradle properties
├── gradlew               # Gradle wrapper script (Unix)
├── gradlew.bat           # Gradle wrapper script (Windows)
├── LICENSE               # MIT License
├── README.md             # Project documentation
└── settings.gradle.kts   # Gradle settings
```

## Key Files and Their Purposes

### Plugin Actions
- **CopySelectedFilesAction.kt**: Handles copying selected files from Project view
- **CopyOpenTabsAction.kt**: Handles copying all open editor tabs
- **CopyProjectStructureAction.kt**: Handles extracting and copying project structure
- **CopySimplifiedStructureAction.kt**: Handles copying simplified project structure

### Utilities
- **FileContentUtils.kt**: Handles clipboard operations and content formatting
- **FileProcessingUtil.kt**: Core logic for file processing and filtering

### Constants
- **Exclusions.kt**: Defines exclusion rules for files and directories

### Configuration
- **plugin.xml**: Plugin metadata, dependencies, and action registration
- **build.gradle.kts**: Build configuration and dependencies
- **gradle.properties**: Gradle and plugin properties
- **settings.gradle.kts**: Gradle project settings

### Documentation
- **README.md**: User documentation and installation guide
- **overview.md**: Technical documentation and architecture
- **future_enhancements.md**: Planned features and improvements
- **file_structure.md**: Project structure documentation

### Resources
- **messages.properties**: Internationalization strings

### Build and Run
- **gradlew/gradlew.bat**: Gradle wrapper scripts
- **.run/**: IntelliJ IDEA run configurations 