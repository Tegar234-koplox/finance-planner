package com.tegar.finance

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class LanguageResourcesTest {
    @Test fun indonesianAndEnglishResolveOnTheMinimumAndroidVersionWithoutChangingDeviceLocale() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = context.resources.configuration.locales.toLanguageTags()
        fun localized(tag: String) = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(tag))
        }).resources
        val idn = localized("id"); val english = localized("en")
        assertEquals("Pengaturan", idn.getString(R.string.settings))
        assertEquals("Kembali", idn.getString(R.string.back))
        assertEquals("Bukti: SEDANG", idn.getString(R.string.ui_evidence_value_a6f3ef, idn.getString(R.string.evidence_medium)))
        assertEquals("Settings", english.getString(R.string.settings))
        assertEquals("Navigate back", english.getString(R.string.back))
        assertEquals("Evidence: MEDIUM", english.getString(R.string.ui_evidence_value_a6f3ef, english.getString(R.string.evidence_medium)))
        assertEquals("Assets and tracked net worth", english.getString(R.string.wealth_title))
        assertEquals("Aset dan kekayaan bersih tercatat", idn.getString(R.string.wealth_title))
        assertTrue(idn.getString(R.string.wealth_missing, "1", "2").contains("1"))
        assertEquals(original, context.resources.configuration.locales.toLanguageTags())
    }
}
