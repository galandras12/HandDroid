package com.galandras12.unofficialhandbrake.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galandras12.unofficialhandbrake.R
import com.galandras12.unofficialhandbrake.data.Preset
import com.galandras12.unofficialhandbrake.data.PresetRepository
import com.galandras12.unofficialhandbrake.ui.MainViewModel

@Composable
fun PresetsScreen(vm: MainViewModel, onPicked: () -> Unit) {
    val presets by vm.presets.collectAsStateWithLifecycle()
    val current by vm.presetName.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val collapsed = remember { mutableStateListOf<String>() }

    val filtered = presets.filter { query.isBlank() || it.name.contains(query, true) || it.description.contains(query, true) }
    // Custom presets first, then the categories in file order
    val categories = filtered.map { it.category }.distinct().sortedBy { if (it == PresetRepository.USER_CATEGORY) 0 else 1 }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_presets)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, null) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { cat ->
                val open = query.isNotBlank() || cat !in collapsed
                item(key = "cat-$cat") {
                    Row(
                        Modifier.fillMaxWidth().clickable { if (cat in collapsed) collapsed.remove(cat) else collapsed.add(cat) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(categoryTitle(cat), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (open) {
                    items(filtered.filter { it.category == cat }, key = { "p-${it.category}-${it.name}" }) { p ->
                        PresetRow(p, selected = p.name == current, onClick = { vm.applyPreset(p); onPicked() }, onDelete = { vm.deletePreset(p) })
                    }
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp)) }
        }
    }
}

@Composable
private fun categoryTitle(cat: String): String = when (cat) {
    PresetRepository.USER_CATEGORY -> stringResource(R.string.cat_custom)
    "General" -> stringResource(R.string.cat_general)
    "Web" -> stringResource(R.string.cat_web)
    "Devices" -> stringResource(R.string.cat_devices)
    "Matroska" -> stringResource(R.string.cat_matroska)
    "Professional" -> stringResource(R.string.cat_professional)
    "Hardware (Android)" -> stringResource(R.string.cat_hardware)
    else -> cat
}

@Composable
private fun PresetRow(p: Preset, selected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleSmall)
                val d = p.description.ifBlank { "${p.settings.videoEncoder.label} · ${p.settings.container.label}" }
                Text(d, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
            }
            if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            if (!p.builtIn) IconButton(onDelete) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete)) }
        }
    }
}
