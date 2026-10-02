package com.galandras12.handdroid.ui.screens

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galandras12.handdroid.R
import com.galandras12.handdroid.data.ThemeMode
import com.galandras12.handdroid.ui.MainViewModel
import com.galandras12.handdroid.ui.SectionCard
import com.galandras12.handdroid.ui.SegmentedChoice
import androidx.compose.foundation.clickable

@Composable
fun SettingsScreen(vm: MainViewModel, onAbout: () -> Unit) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val tree by vm.outputTree.collectAsStateWithLifecycle()
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { vm.setOutputTree(it) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = stringResource(R.string.appearance)) {
            SegmentedChoice(
                ThemeMode.entries, theme,
                { stringResource(when (it) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark }) },
                vm::setTheme,
            )
        }
        SectionCard(title = stringResource(R.string.output_folder)) {
            Text(
                tree?.let { folderName(it) } ?: stringResource(R.string.default_folder),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(stringResource(R.string.output_folder_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { folderPicker.launch(null) }) { Text(stringResource(R.string.choose_folder)) }
                if (tree != null) TextButton(onClick = { vm.setOutputTree(null) }) { Text(stringResource(R.string.use_default)) }
            }
        }
        SectionCard {
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_handdroid)) },
                supportingContent = { Text(stringResource(R.string.about_summary)) },
                leadingContent = { Icon(Icons.Rounded.Info, null) },
                trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier.clickable(onClick = onAbout),
            )
        }
    }
}

private fun folderName(tree: String): String {
    val id = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(tree)) }.getOrNull() ?: return tree
    return id.substringAfter(':', id).ifBlank { id }
}
