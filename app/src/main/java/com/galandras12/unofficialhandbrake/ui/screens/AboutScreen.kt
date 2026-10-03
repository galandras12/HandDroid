package com.galandras12.unofficialhandbrake.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig
import com.galandras12.unofficialhandbrake.BuildConfig
import com.galandras12.unofficialhandbrake.R
import com.galandras12.unofficialhandbrake.ui.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val SOURCE_URL = "https://github.com/galandras12/HandDroid"
const val HANDBRAKE_URL = "https://handbrake.fr"
const val HANDBRAKE_SOURCE_URL = "https://github.com/HandBrake/HandBrake"

@Composable
fun AboutScreen(onLicense: () -> Unit) {
    val uri = LocalUriHandler.current
    val ffmpeg = runCatching { FFmpegKitConfig.getFFmpegVersion() }.getOrNull()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(R.drawable.logo_handdroid), null,
            Modifier.size(132.dp).clip(RoundedCornerShape(30.dp)),
        )
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.version_fmt, BuildConfig.VERSION_NAME) + (ffmpeg?.let { " · FFmpeg $it" } ?: ""),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.based_on_handbrake), style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )

        SectionCard {
            Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.about_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard(title = stringResource(R.string.license)) {
            Text(stringResource(R.string.license_body), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onLicense) { Text(stringResource(R.string.view_license)) }
        }
        SectionCard(title = stringResource(R.string.links)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { uri.openUri(SOURCE_URL) }) { Text(stringResource(R.string.source_code)) }
                OutlinedButton(onClick = { uri.openUri(HANDBRAKE_URL) }) { Text("HandBrake") }
            }
            OutlinedButton(onClick = { uri.openUri(HANDBRAKE_SOURCE_URL) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.handbrake_source)) }
        }
        SectionCard(title = stringResource(R.string.third_party)) {
            Text(stringResource(R.string.third_party_body), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun LicenseScreen() {
    val context = LocalContext.current
    val text = produceState("") {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("LICENSE_GPL-2.0.txt").bufferedReader().use { it.readText() } }.getOrDefault("")
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SelectionContainer {
            Text(text.value, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}
