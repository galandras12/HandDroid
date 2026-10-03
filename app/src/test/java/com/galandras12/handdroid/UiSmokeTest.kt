package com.galandras12.handdroid

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.activity.ComponentActivity
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.galandras12.handdroid.data.AudioStream
import com.galandras12.handdroid.data.SourceInfo
import com.galandras12.handdroid.data.SubtitleStream
import com.galandras12.handdroid.data.ThemeMode
import com.galandras12.handdroid.engine.JobStatus
import com.galandras12.handdroid.ui.HandDroidRoot
import com.galandras12.handdroid.ui.MainViewModel
import com.galandras12.handdroid.ui.theme.HandDroidTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the real screens on the JVM; screenshots land in app/build/shots for a visual check. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = HandDroidApp::class)
class UiSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val source = SourceInfo(
        input = "x", uri = "content://x/1", name = "Kon-Tiki - NORSWE_DAN.mkv", sizeBytes = 8_500_000_000, durationMs = 7_128_000,
        width = 1920, height = 1080, fps = 23.976, videoCodec = "h264", videoBitrateKbps = 9000, chapters = 16,
        audio = listOf(AudioStream(0, "dts", 6, 48000, 1509, "eng", null), AudioStream(1, "ac3", 2, 48000, 192, "nor", "Commentary")),
        subtitles = listOf(SubtitleStream(0, "subrip", "eng", null), SubtitleStream(1, "hdmv_pgs_subtitle", "swe", null)),
    )

    @Suppress("UNCHECKED_CAST")
    private fun vm(): MainViewModel {
        val app = ApplicationProvider.getApplicationContext<HandDroidApp>()
        val vm = MainViewModel(app)
        MainViewModel::class.java.getDeclaredField("_sources").apply { isAccessible = true }
            .get(vm).let { (it as MutableStateFlow<List<SourceInfo>>).value = listOf(source) }
        return vm
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val dir = File("build/shots").apply { mkdirs() }
        val v = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun launch(theme: ThemeMode, vm: MainViewModel) {
        rule.setContent { HandDroidTheme(theme) { Surface { HandDroidRoot(vm) } } }
        rule.waitForIdle()
    }

    @Test fun everyScreenRenders() {
        val vm = vm()
        launch(ThemeMode.LIGHT, vm)
        rule.mainClock.advanceTimeBy(500)
        shot("1_convert_summary")
        for (tab in listOf("Video", "Audio", "Subtitles", "Picture", "Filters")) {
            rule.onNodeWithText(tab).performClick(); rule.waitForIdle()
            shot("2_tab_$tab")
        }
        rule.onNodeWithText("Presets").performClick(); rule.waitForIdle(); shot("3_presets")

        val app = ApplicationProvider.getApplicationContext<HandDroidApp>()
        val id1 = app.engine.add(source, vm.settings.value, "Fast 1080p30", "Kon-Tiki")
        app.engine.add(source, vm.settings.value, "HQ 1080p30 Surround", "Kon-Tiki 2")
        rule.onNodeWithText("Queue").performClick(); rule.waitForIdle(); shot("4_queue")
        rule.onNodeWithText("Settings").performClick(); rule.waitForIdle(); shot("5_settings")
        rule.onNodeWithText("About HandDroid").performClick(); rule.waitForIdle(); shot("6_about")
    }

    @Test fun darkTheme() {
        val vm = vm()
        launch(ThemeMode.DARK, vm)
        shot("7_dark_convert")
        rule.onNodeWithText("Video").performClick(); rule.waitForIdle(); shot("8_dark_video")
    }

    @Test fun emptyConvertScreen() {
        val app = ApplicationProvider.getApplicationContext<HandDroidApp>()
        launch(ThemeMode.LIGHT, MainViewModel(app))
        shot("0_empty")
    }

    @Test @Config(qualifiers = "de-w411dp-h891dp-xxhdpi")
    fun germanLayout() {
        val vm = vm()
        launch(ThemeMode.LIGHT, vm)
        shot("9_de_convert")
        rule.onNodeWithText("Audio").performClick(); rule.waitForIdle(); shot("9_de_audio")
        rule.onNodeWithText("Einstellungen").performClick(); rule.waitForIdle(); shot("9_de_settings")
    }
}
