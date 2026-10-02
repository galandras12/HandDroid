package com.galandras12.handdroid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galandras12.handdroid.R
import com.galandras12.handdroid.ui.MainViewModel

@Composable
fun LogScreen(vm: MainViewModel, jobId: Long) {
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val job = jobs.firstOrNull { it.id == jobId }
    val scroll = rememberScrollState()
    LaunchedEffect(job?.log?.length) { scroll.animateScrollTo(scroll.maxValue) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(scroll)) {
        SelectionContainer {
            Text(
                job?.log?.ifBlank { null } ?: stringResource(R.string.log_empty),
                fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
