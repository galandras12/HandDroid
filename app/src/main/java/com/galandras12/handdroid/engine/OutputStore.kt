package com.galandras12.handdroid.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.galandras12.handdroid.R
import com.galandras12.handdroid.data.Container
import java.io.File

/** Moves a finished temporary file to its final public location. */
object OutputStore {
    const val FOLDER = "HandDroid"

    data class Saved(val uri: String, val sizeBytes: Long)

    fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.').ifBlank { "video" }.take(120)

    fun save(context: Context, temp: File, baseName: String, container: Container, treeUri: String?): Saved {
        val fileName = sanitize(baseName) + "." + container.ext
        val size = temp.length()
        val resolver = context.contentResolver

        if (treeUri != null) {
            val tree = Uri.parse(treeUri)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val doc = DocumentsContract.createDocument(resolver, parent, container.mime, fileName)
                ?: error(context.getString(R.string.err_save_failed))
            resolver.openOutputStream(doc, "w")!!.use { out -> temp.inputStream().use { it.copyTo(out) } }
            return Saved(doc.toString(), size)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, container.mime)
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$FOLDER")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val item = resolver.insert(collection, values) ?: error(context.getString(R.string.err_save_failed))
            try {
                resolver.openOutputStream(item, "w")!!.use { out -> temp.inputStream().use { it.copyTo(out) } }
                resolver.update(item, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            } catch (t: Throwable) {
                resolver.delete(item, null, null)
                throw t
            }
            return Saved(item.toString(), size)
        }

        // Android 8 / 9: app specific external storage, no permission needed
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir, FOLDER).apply { mkdirs() }
        var target = File(dir, fileName)
        var n = 1
        while (target.exists()) target = File(dir, "${sanitize(baseName)} (${n++}).${container.ext}")
        temp.copyTo(target)
        return Saved(FileProvider.getUriForFile(context, "${context.packageName}.files", target).toString(), size)
    }
}
