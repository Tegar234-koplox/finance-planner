package com.tegar.finance.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tegar.finance.R
import com.tegar.finance.domain.model.ThemeMode
import com.tegar.finance.domain.model.AppLanguage

@Composable
fun SettingsScreen(state: AppUiState, onTheme: (ThemeMode) -> Unit, onLanguage: (AppLanguage) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.appearance), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.appearance_description), style = MaterialTheme.typography.bodyLarge)
        Column(Modifier.selectableGroup()) {
            ThemeMode.entries.forEach { mode ->
                val label = stringResource(when (mode) {
                    ThemeMode.SYSTEM -> R.string.theme_system
                    ThemeMode.LIGHT -> R.string.theme_light
                    ThemeMode.DARK -> R.string.theme_dark
                })
                Row(Modifier.fillMaxWidth().selectable(selected = state.theme == mode,
                    onClick = { onTheme(mode) }, role = Role.RadioButton).padding(vertical = 12.dp)) {
                    RadioButton(selected = state.theme == mode, onClick = null)
                    Text(label, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (state.themeSaveFailed) Text(stringResource(R.string.preference_error), color = MaterialTheme.colorScheme.error)
        HorizontalDivider()
        Text(stringResource(R.string.language), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.language_description))
        Column(Modifier.selectableGroup()) {
            AppLanguage.entries.forEach { language ->
                Row(Modifier.fillMaxWidth().testTag("language_${language.tag}").selectable(selected = state.language == language,
                    onClick = { onLanguage(language) }, role = Role.RadioButton).padding(vertical = 12.dp)) {
                    RadioButton(selected = state.language == language, onClick = null)
                    Text(if (language == AppLanguage.ENGLISH) stringResource(R.string.ui_english_ba118b) else stringResource(R.string.ui_bahasa_indonesia_11b871), Modifier.padding(start = 16.dp))
                }
            }
        }
        if (state.languageSaveFailed) Text(stringResource(R.string.language_error), color = MaterialTheme.colorScheme.error)
        HorizontalDivider()
        Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.currency_default), style = MaterialTheme.typography.bodyMedium)
    }
}
