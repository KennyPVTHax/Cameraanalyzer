package tech.cameraanalyzer

/** Conservative static-analysis outcome; no result asserts runtime camera behavior. */
object AnalysisClassification {
    fun classify(unreadableApks: Int, dexFilesScanned: Int, technologies: Collection<String>): String = when {
        unreadableApks > 0 && dexFilesScanned == 0 && technologies.isEmpty() -> "Unable to analyze"
        technologies.isNotEmpty() -> "Possible camera implementation"
        else -> "No recognizable camera indicators"
    }
}
