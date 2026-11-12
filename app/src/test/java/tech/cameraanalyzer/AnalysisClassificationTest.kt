package tech.cameraanalyzer

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisClassificationTest {
    @Test fun cameraReferenceIsPossibleStaticEvidence() {
        assertEquals("Possible camera implementation", AnalysisClassification.classify(0, 2, listOf("Camera2")))
    }

    @Test fun noMatchesAreReportedAsUnknownIndicators() {
        assertEquals("No recognizable camera indicators", AnalysisClassification.classify(0, 3, emptyList()))
    }

    @Test fun unreadablePackagesWithoutDexAreUnableToAnalyze() {
        assertEquals("Unable to analyze", AnalysisClassification.classify(1, 0, emptyList()))
    }
}
