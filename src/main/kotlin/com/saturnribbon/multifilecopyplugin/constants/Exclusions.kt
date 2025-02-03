package com.saturnribbon.multifilecopyplugin.constants

object Exclusions {
    val EXCLUDED_DIRECTORIES = setOf(
        "build", "bin", "out", "target", "node_modules"
    )
    val EXCLUDED_FILES = setOf(
        "package-lock.json",
        "yarn.lock",
        "pom.xml",
        "build.gradle",
        "settings.gradle",
        "gradlew",
        "gradlew.properties",
        ".gitignore",
        "gradlew.bat",
        "LICENSE"
    )
    val BINARY_EXTENSIONS = setOf(
        "exe", "dll", "so", "dylib", "bin", "jar", "war",
        "class", "o", "obj", "zip", "tar", "gz", "7z", "rar"
    )
    val IMAGE_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "gif", "bmp", "ico", "svg",
        "webp", "tiff", "psd"
    )
    const val MAX_FILE_SIZE_BYTES: Long = 1_048_576 // 1MB
}