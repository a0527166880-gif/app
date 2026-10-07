package com.a0527166880.offlinelens

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream

class OfflineOcr(private val context: Context) {
    private var api: TessBaseAPI? = null
    private var activeLang: String? = null
    private val lock = Any()

    fun recognize(bitmap: Bitmap, langCode: String): String = synchronized(lock) {
        val language = when (langCode) {
            "ערבית" -> "ara"
            "עברית" -> "heb"
            "אנגלית" -> "eng"
            "סינית" -> "chi_sim"
            "רוסית" -> "rus"
            else -> "eng"
        }

        ensureLanguage(language)
        val engine = api ?: return ""
        engine.setImage(bitmap)
        val result = engine.utF8Text?.trim().orEmpty()
        engine.clear()
        return result
    }

    fun close() = synchronized(lock) {
        api?.clear()
        api?.recycle()
        api = null
        activeLang = null
    }

    private fun ensureLanguage(language: String) {
        if (activeLang == language && api != null) return

        api?.clear()
        api?.recycle()
        api = null
        activeLang = null

        val dataDir = File(context.filesDir, "tesseract")
        val tessdata = File(dataDir, "tessdata")
        if (!tessdata.exists()) tessdata.mkdirs()

        val trained = File(tessdata, "$language.traineddata")
        if (!trained.exists()) {
            context.assets.open("tessdata/$language.traineddata").use { input ->
                FileOutputStream(trained).use { output ->
                    input.copyTo(output)
                }
            }
        }

        val fresh = TessBaseAPI()
        check(fresh.init(dataDir.absolutePath, language)) {
            "Tesseract init failed for $language"
        }
        fresh.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
        api = fresh
        activeLang = language
    }
}

// CI artifact retrieval trigger.
