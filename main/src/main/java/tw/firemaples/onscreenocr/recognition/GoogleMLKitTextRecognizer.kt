package tw.firemaples.onscreenocr.recognition

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import tw.firemaples.onscreenocr.R
import tw.firemaples.onscreenocr.pref.AppPref
import tw.firemaples.onscreenocr.utils.Constraints
import tw.firemaples.onscreenocr.utils.Utils
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class GoogleMLKitTextRecognizer : TextRecognizer {
    companion object {
        private val devanagariLangCodes = arrayOf("hi", "mr", "ne", "sa")
    }

    private val context: Context by lazy { Utils.context }

    override val type: Recognizer
        get() = Recognizer.GoogleMLKit

    private val recognizerMap =
        mutableMapOf<ScriptType, com.google.mlkit.vision.text.TextRecognizer>()

    override suspend fun recognize(bitmap: Bitmap): RecognitionResult {
        val lang = AppPref.selectedOCRLang
        return doRecognize(bitmap, lang)
    }

    private suspend fun doRecognize(bitmap: Bitmap, lang: String): RecognitionResult =
        suspendCoroutine {
            val script = getScriptType(lang)

            val recognizer = recognizerMap.getOrPut(script) {
                val options = when (script) {
                    ScriptType.Chinese -> ChineseTextRecognizerOptions.Builder().build()
                    ScriptType.Devanagari -> DevanagariTextRecognizerOptions.Builder().build()
                    ScriptType.Japanese -> JapaneseTextRecognizerOptions.Builder().build()
                    ScriptType.Korean -> KoreanTextRecognizerOptions.Builder().build()
                    ScriptType.Latin -> TextRecognizerOptions.DEFAULT_OPTIONS
                }

                TextRecognition.getClient(options)
            }

            val image = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { result ->
                    it.resume(
                        RecognitionResult(
                            langCode = lang.toISO639(),
                            result = result.text,
                            boundingBoxes = result.textBlocks.mapNotNull { it.boundingBox }),
                    )
                }
                .addOnFailureListener { e ->
                    it.resumeWithException(e)
                }
        }

    override suspend fun parseToDisplayLangCode(langCode: String): String = langCode.toISO639()

    override suspend fun supportedLanguages(): List<RecognitionLanguage> {
        return getSupportedLanguageList()
    }

    private fun getSupportedLanguageList(): List<RecognitionLanguage> {
        val res = context.resources
        val langCodes = res.getStringArray(R.array.lang_ocr_google_mlkit_code_bcp_47)
        val langNames = res.getStringArray(R.array.lang_ocr_google_mlkit_name)
        val selected = AppPref.selectedOCRLang.let {
            if (langCodes.contains(it)) it else Constraints.DEFAULT_OCR_LANG
        }

        return langCodes.indices.map { i ->
            val code = langCodes[i]
            RecognitionLanguage(
                code = code,
                displayName = langNames[i],
                selected = code == selected,
            )
        }.sortedBy { it.displayName }.distinctBy { it.displayName }
    }

    private fun getSupportedScriptList(): List<RecognitionLanguage> {
        val res = context.resources
        val scriptCodes = res.getStringArray(R.array.google_MLKit_translationScriptCode)
        val scriptNames = res.getStringArray(R.array.google_MLKit_translationScriptName)
        val selected = AppPref.selectedOCRLang.let {
            if (scriptCodes.contains(it)) it else scriptCodes[0]
        }

        return scriptCodes.indices.map { i ->
            val code = scriptCodes[i]
            RecognitionLanguage(
                code = code,
                displayName = scriptNames[i],
                selected = code == selected,
            )
        }
    }

    private fun getScriptType(lang: String): ScriptType =
        when {
            ScriptType.Japanese.isJapanese(lang) -> ScriptType.Japanese
            ScriptType.Korean.isKorean(lang) -> ScriptType.Korean
            ScriptType.Chinese.isChinese(lang) -> ScriptType.Chinese
            ScriptType.Devanagari.isDevanagari(lang) -> ScriptType.Devanagari
            else -> ScriptType.Latin
        }

    private sealed class ScriptType {
        object Latin : ScriptType()
        object Chinese : ScriptType() {
            fun isChinese(lang: String): Boolean = lang.startsWith("zh")
        }

        object Devanagari : ScriptType() {
            fun isDevanagari(lang: String): Boolean = devanagariLangCodes.contains(lang)
        }

        object Japanese : ScriptType() {
            fun isJapanese(lang: String): Boolean = lang == "ja"
        }

        object Korean : ScriptType() {
            fun isKorean(lang: String): Boolean = lang == "ko"
        }
    }

    private fun String.toISO639(): String = split("-")[0]
}
