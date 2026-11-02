package com.tegar.finance.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tegar.finance.R

/** Keep explanations available without making them the primary screen content. */
@Composable
internal fun ScreenHelp(tag: String, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.testTag(tag)) {
        Icon(Icons.Outlined.Info, stringResource(R.string.common_about_values))
    }
    if (open) AlertDialog(onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.common_about_values)) },
        text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()).testTag("${tag}_content"),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content) },
        confirmButton = { TextButton(onClick = { open = false }, modifier = Modifier.testTag("${tag}_close")) {
            Text(stringResource(R.string.ui_dismiss_48845b))
        } })
}
