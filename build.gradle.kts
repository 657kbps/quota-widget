plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
}

// detekt has no rule for file length (LargeClass only counts a single class), so the
// per-file cap is enforced here and runs next to the detekt tasks in CI.
val maxKotlinFileLines = 800

tasks.register("verifyMaxFileLines") {
    group = "verification"
    description = "Fails when a Kotlin source file is longer than $maxKotlinFileLines lines"

    val sourceRoots = listOf("shared/src", "androidApp/src")
        .map { layout.projectDirectory.dir(it) }
    val baseDir = layout.projectDirectory.asFile

    inputs.files(sourceRoots.map { fileTree(it) { include("**/*.kt") } })
        .withPropertyName("kotlinSources")

    doLast {
        val limit = maxKotlinFileLines
        val oversized = sourceRoots.flatMap { root ->
            fileTree(root) { include("**/*.kt") }.files.mapNotNull { file ->
                val lineCount = file.readLines().size
                if (lineCount > limit) {
                    "${file.relativeTo(baseDir)} has $lineCount lines (max $limit)"
                } else {
                    null
                }
            }
        }
        if (oversized.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Kotlin files exceed $limit lines:")
                    oversized.forEach { appendLine(" - $it") }
                },
            )
        }
    }
}
