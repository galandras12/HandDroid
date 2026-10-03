package com.galandras12.unofficialhandbrake.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.galandras12.unofficialhandbrake.R
import com.galandras12.unofficialhandbrake.engine.JobStatus
import com.galandras12.unofficialhandbrake.ui.screens.AboutScreen
import com.galandras12.unofficialhandbrake.ui.screens.ConvertScreen
import com.galandras12.unofficialhandbrake.ui.screens.LicenseScreen
import com.galandras12.unofficialhandbrake.ui.screens.LogScreen
import com.galandras12.unofficialhandbrake.ui.screens.PresetsScreen
import com.galandras12.unofficialhandbrake.ui.screens.QueueScreen
import com.galandras12.unofficialhandbrake.ui.screens.SettingsScreen

private enum class Dest(val route: String, val title: Int, val icon: ImageVector) {
    CONVERT("convert", R.string.nav_convert, Icons.Rounded.Movie),
    PRESETS("presets", R.string.nav_presets, Icons.Rounded.Tune),
    QUEUE("queue", R.string.nav_queue, Icons.AutoMirrored.Rounded.PlaylistPlay),
    SETTINGS("settings", R.string.nav_settings, Icons.Rounded.Settings),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HandDroidRoot(vm: MainViewModel, nav: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val jobs by vm.jobs.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            snackbar.showSnackbar(
                when (m) {
                    is UiMessage.Res -> context.getString(m.id, *m.args.toTypedArray())
                    is UiMessage.Text -> m.text
                }
            )
        }
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val requestNotifications = {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun go(d: Dest) = nav.navigate(d.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    val top = Dest.entries.firstOrNull { it.route == route }
    val title = when {
        top != null -> stringResource(top.title)
        route == "about" -> stringResource(R.string.about_handdroid)
        route == "license" -> stringResource(R.string.license)
        route?.startsWith("log") == true -> stringResource(R.string.activity_log)
        else -> stringResource(R.string.app_name)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (top == Dest.CONVERT) stringResource(R.string.app_name) else title) },
                navigationIcon = {
                    if (top == null) IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            if (top != null) ShortNavigationBar {
                Dest.entries.forEach { d ->
                    ShortNavigationBarItem(
                        selected = route == d.route,
                        onClick = { go(d) },
                        icon = {
                            val pending = jobs.count { it.status == JobStatus.PENDING || it.status == JobStatus.RUNNING }
                            if (d == Dest.QUEUE && pending > 0) BadgedBox({ Badge { Text("$pending") } }) { Icon(d.icon, null) }
                            else Icon(d.icon, null)
                        },
                        label = { Text(stringResource(d.title)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Dest.CONVERT.route, modifier = Modifier.padding(padding)) {
            composable(Dest.CONVERT.route) {
                ConvertScreen(
                    vm,
                    onChoosePreset = { go(Dest.PRESETS) },
                    onQueued = { go(Dest.QUEUE) },
                    requestNotifications = requestNotifications,
                )
            }
            composable(Dest.PRESETS.route) { PresetsScreen(vm, onPicked = { go(Dest.CONVERT) }) }
            composable(Dest.QUEUE.route) {
                QueueScreen(vm, onOpenLog = { nav.navigate("log/$it") }, requestNotifications = requestNotifications)
            }
            composable(Dest.SETTINGS.route) { SettingsScreen(vm, onAbout = { nav.navigate("about") }) }
            composable("about") { AboutScreen(onLicense = { nav.navigate("license") }) }
            composable("license") { LicenseScreen() }
            composable("log/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                LogScreen(vm, it.arguments?.getLong("id") ?: -1L)
            }
        }
    }
}
