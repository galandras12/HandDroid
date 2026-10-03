package com.galandras12.unofficialhandbrake.i18n

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import com.galandras12.unofficialhandbrake.ui.Languages
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Languages that ship with the app (see [Languages]) use ordinary Android resources.
 * For any other language the English texts are translated on the device by Google ML Kit
 * (a language pack is downloaded once), cached, and served through [TranslatedResources].
 */
object AutoTranslation {
    sealed interface Status {
        data object Idle : Status
        data class Translating(val language: String) : Status
        data class Ready(val language: String) : Status
        data object Failed : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    @Volatile var resources: TranslatedResources? = null
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: kotlinx.coroutines.Job? = null

    private val shipped: Set<String> by lazy { Languages.all.map { it.first }.toSet() }

    /** ML Kit language code to translate into, or null when the app already ships the user's language. */
    fun targetLanguage(): String? {
        val locale = AppCompatDelegate.getApplicationLocales().get(0) ?: Resources.getSystem().configuration.locales[0]
        val lang = locale.language
        if (lang in shipped) return null
        return TranslateLanguage.fromLanguageTag(lang)
    }

    /** Languages offered in the picker beyond the shipped ones, as BCP-47 codes. */
    fun extraLanguages(): List<String> =
        TranslateLanguage.getAllLanguages().filter { it !in shipped }.sortedBy { displayName(it) }

    fun displayName(tag: String): String {
        val l = Locale.forLanguageTag(tag)
        return l.getDisplayName(l).replaceFirstChar { it.titlecase(l) }
    }

    /** Called at start-up and after a language change: loads the cached translation or starts translating. */
    fun prepare(context: Context) {
        val app = context.applicationContext
        val target = targetLanguage()
        if (target == null) {
            resources = null
            _status.value = Status.Idle
            return
        }
        loadCache(app, target)?.let { cached ->
            val missing = missingKeys(cached)
            if (missing.isEmpty()) {
                install(app, target, cached)
                return
            }
        }
        if (job?.isActive == true) return
        _status.value = Status.Translating(target)
        job = scope.launch {
            val ok = runCatching { translateAll(app, target) }.getOrDefault(false)
            if (ok) loadCache(app, target)?.let { install(app, target, it) } else _status.value = Status.Failed
        }
    }

    private fun install(app: Context, target: String, map: Map<String, String>) {
        val strings = HashMap<Int, String>()
        TranslatableStrings.strings.forEach { (name, id) -> map[name]?.let { strings[id] = it } }
        val plurals = HashMap<Int, Pair<String, String>>()
        TranslatableStrings.plurals.forEach { (name, id) ->
            val one = map["$name#one"]; val other = map["$name#other"]
            if (one != null && other != null) plurals[id] = one to other
        }
        val base = app.resources
        resources = TranslatedResources(base, strings, plurals)
        _status.value = Status.Ready(target)
    }

    private fun missingKeys(map: Map<String, String>): List<String> =
        TranslatableStrings.strings.map { it.first }.filter { it !in map && it !in SKIP } +
            TranslatableStrings.plurals.flatMap { listOf("${it.first}#one", "${it.first}#other") }.filter { it !in map }

    // ------------------------------------------------------------------ translation

    private val SKIP = setOf("app_name", "notif_finished_title", "cat_matroska", "deint_yadif", "default_folder", "rot_180")

    private suspend fun translateAll(app: Context, target: String): Boolean = withContext(Dispatchers.IO) {
        val english = englishResources(app)
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(target).build()
        val translator = Translation.getClient(options)
        try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            val out = loadCache(app, target)?.toMutableMap() ?: mutableMapOf()
            suspend fun tr(key: String, text: String) {
                if (key in out) return
                val (protected, tokens) = protect(text)
                val translated = translator.translate(protected).await()
                out[key] = restore(translated, tokens) ?: text
            }
            for ((name, id) in TranslatableStrings.strings) {
                if (name in SKIP) { out[name] = english.getString(id); continue }
                tr(name, english.getString(id))
            }
            for ((name, id) in TranslatableStrings.plurals) {
                tr("$name#one", english.getQuantityString(id, 1, 1))
                tr("$name#other", english.getQuantityString(id, 2, 2))
            }
            saveCache(app, target, out)
            true
        } finally {
            translator.close()
        }
    }

    private fun englishResources(app: Context): Resources {
        val cfg = Configuration(app.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        return app.createConfigurationContext(cfg).resources
    }

    private val PLACEHOLDER = Regex("%\\d\\$[sd]")

    /** Swaps `%1$d`-style placeholders for neutral tokens the translator leaves alone. */
    fun protect(text: String): Pair<String, List<String>> {
        val tokens = mutableListOf<String>()
        val out = PLACEHOLDER.replace(text) { m ->
            tokens += m.value
            "QZ${'A' + tokens.size - 1}QZ"
        }
        return out to tokens
    }

    /** Puts the placeholders back; returns null when the translator damaged a token. */
    fun restore(translated: String, tokens: List<String>): String? {
        var s = translated
        tokens.forEachIndexed { i, original ->
            val re = Regex("QZ\\s?${'A' + i}\\s?QZ", RegexOption.IGNORE_CASE)
            if (!re.containsMatchIn(s)) return null
            s = re.replace(s) { original }
        }
        return s.trim()
    }

    private fun cacheFile(app: Context, lang: String) = File(app.filesDir, "autotranslate_$lang.json")

    private fun loadCache(app: Context, lang: String): Map<String, String>? = runCatching {
        val f = cacheFile(app, lang)
        if (!f.exists()) return null
        val o = JSONObject(f.readText())
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrNull()

    private fun saveCache(app: Context, lang: String, map: Map<String, String>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        cacheFile(app, lang).writeText(o.toString())
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
    }
}

/** Serves translated texts for the app's own string resources, everything else comes from [base]. */
@Suppress("DEPRECATION")
class TranslatedResources(
    private val base: Resources,
    private val strings: Map<Int, String>,
    private val plurals: Map<Int, Pair<String, String>>,
) : Resources(base.assets, base.displayMetrics, base.configuration) {
    private val locale: Locale get() = Locale.getDefault()

    override fun getString(id: Int): String = strings[id] ?: super.getString(id)
    override fun getString(id: Int, vararg formatArgs: Any?): String =
        strings[id]?.let { String.format(locale, it, *formatArgs) } ?: super.getString(id, *formatArgs)
    override fun getText(id: Int): CharSequence = strings[id] ?: super.getText(id)
    override fun getQuantityString(id: Int, quantity: Int): String =
        plurals[id]?.let { if (quantity == 1) it.first else it.second } ?: super.getQuantityString(id, quantity)
    override fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any?): String =
        plurals[id]?.let { String.format(locale, if (quantity == 1) it.first else it.second, *formatArgs) }
            ?: super.getQuantityString(id, quantity, *formatArgs)
}
