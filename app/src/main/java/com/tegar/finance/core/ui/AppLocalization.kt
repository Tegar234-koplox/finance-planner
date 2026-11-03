package com.tegar.finance.core.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.tegar.finance.domain.model.AppLanguage
import java.util.Locale

/** Override UI resources only: stored dates, exact money and user text keep their existing contracts. */
@Composable
fun AppLocalization(language: AppLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val deviceConfiguration = LocalConfiguration.current
    val localized = remember(context, deviceConfiguration, language) {
        val configuration = Configuration(deviceConfiguration).apply { setLocale(Locale.forLanguageTag(language.tag)) }
        context.createConfigurationContext(configuration)
    }
    CompositionLocalProvider(LocalConfiguration provides localized.resources.configuration,
        LocalResources provides localized.resources, content = content)
}
