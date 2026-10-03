package com.galandras12.unofficialhandbrake

import com.galandras12.unofficialhandbrake.i18n.AutoTranslation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoTranslationTest {
    @Test fun placeholdersSurviveRoundTrip() {
        val (p, tokens) = AutoTranslation.protect("Encoding %1\$d/%2\$d: %3\$s")
        assertEquals(listOf("%1\$d", "%2\$d", "%3\$s"), tokens)
        assertEquals("Kodowanie %1\$d/%2\$d: %3\$s", AutoTranslation.restore("Kodowanie QZAQZ/QZBQZ: QZCQZ", tokens))
    }

    @Test fun translatorChangingCaseOrSpacingInsideTokenIsTolerated() {
        assertEquals("a %1\$d b", AutoTranslation.restore("a qz a qz b", listOf("%1\$d")))
    }

    @Test fun damagedTokenFallsBack() {
        assertNull(AutoTranslation.restore("Kodowanie", listOf("%1\$d")))
    }
}
