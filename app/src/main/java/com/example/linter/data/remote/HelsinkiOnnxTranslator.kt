package com.example.linter.data.remote

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.linter.domain.model.TextTranslator
import com.sentencepiece.Model
import com.sentencepiece.Scoring
import com.sentencepiece.SentencePieceAlgorithm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.LongBuffer
import java.nio.file.Paths

class HelsinkiOnnxTranslator(private val context: Context) : TextTranslator {
    private val env = OrtEnvironment.getEnvironment()

    private var currentLang: String? = null
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private var sourceModel: Model? = null

    private val spAlgorithm = SentencePieceAlgorithm(false, Scoring.HIGHEST_SCORE)
    private val pieceToId = mutableMapOf<String, Long>()
    private val idToPiece = mutableMapOf<Long, String>()

    private var padTokenId: Long = 59513L
    private var eosTokenId: Long = 0L
    private var unkTokenId: Long = 2L

    companion object {
        @Volatile
        private var cachedField: java.lang.reflect.Field? = null
        @Volatile
        private var cachedMethod: java.lang.reflect.Method? = null
        @Volatile
        private var isReflectionInspected = false
        private val reflectionLock = Any()
    }

    private fun getAssetFilePath(lang: String, fileName: String): String {
        val assetPath = "onnx/$lang/$fileName"
        val file = File(context.cacheDir, "${lang}_$fileName")
        if (!file.exists()) {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
        }
        return file.absolutePath
    }

    @Synchronized
    private fun loadModelsForLang(lang: String) {
        if (currentLang == lang) return

        encoderSession?.close()
        decoderSession?.close()
        pieceToId.clear()
        idToPiece.clear()

        val opts = OrtSession.SessionOptions().apply { addConfigEntry("session.load_model_format", "ONNX") }

        val encoderBytes = context.assets.open("onnx/$lang/encoder_model_quantized.onnx").readBytes()
        encoderSession = env.createSession(encoderBytes, opts)

        val decoderBytes = context.assets.open("onnx/$lang/decoder_model_quantized.onnx").readBytes()
        decoderSession = env.createSession(decoderBytes, opts)

        val sourceSpmPath = getAssetFilePath(lang, "source.spm")
        sourceModel = Model.parseFrom(Paths.get(sourceSpmPath))

        val vocabBytes = context.assets.open("onnx/$lang/vocab.json").readBytes()
        val vocabJson = JSONObject(String(vocabBytes))
        vocabJson.keys().forEach { key ->
            val id = vocabJson.getLong(key)
            pieceToId[key] = id
            idToPiece[id] = key
        }

        padTokenId = pieceToId["<pad>"] ?: 59513L
        eosTokenId = pieceToId["</s>"] ?: 0L
        unkTokenId = pieceToId["<unk>"] ?: 2L

        currentLang = lang
    }

    private fun getPieceStrFromModel(model: Model, spId: Int): String {
        try {
            if (!isReflectionInspected) {
                synchronized(reflectionLock) {
                    if (!isReflectionInspected) {
                        for (field in model::class.java.declaredFields) {
                            try {
                                field.isAccessible = true
                                val obj = field.get(model)
                                if (obj is Array<*> && obj.isNotEmpty() && obj[0] is String) {
                                    cachedField = field
                                    break
                                }
                                if (obj is List<*> && obj.isNotEmpty() && obj[0] is String) {
                                    cachedField = field
                                    break
                                }
                            } catch (e: Exception) {
                                // Игнорируем ошибки доступа к отдельным полям и продолжаем поиск
                            }
                        }
                        if (cachedField == null) {
                            for (method in model::class.java.methods) {
                                if (method.returnType == String::class.java && method.parameterCount == 1 && !method.name.contains("decode", true)) {
                                    cachedMethod = method
                                    break
                                }
                            }
                        }
                        isReflectionInspected = true
                    }
                }
            }

            cachedField?.let { field ->
                val obj = field.get(model)
                if (obj is Array<*>) return obj.getOrNull(spId)?.toString() ?: "<unk>"
                if (obj is List<*>) return obj.getOrNull(spId)?.toString() ?: "<unk>"
            }

            cachedMethod?.let { method ->
                return method.invoke(model, spId) as String
            }
        } catch (e: Exception) {
            // Безопасный перехват непредвиденных исключений вызова
        }
        return "<unk>"
    }

    override suspend fun translate(text: String, sourceLang: String, targetLang: String): Result<String> = withContext(Dispatchers.Default) {
        try {
            withContext(Dispatchers.IO) {
                loadModelsForLang(sourceLang)
            }

            val cleanText = text.replace("\n", " ").trim()
            if (cleanText.isEmpty()) return@withContext Result.success("")

            val spIds = sourceModel!!.encodeNormalized(cleanText, spAlgorithm)
            val inputIdsList = mutableListOf<Long>()
            for (spId in spIds) {
                val pieceStr = getPieceStrFromModel(sourceModel!!, spId)
                inputIdsList.add(pieceToId[pieceStr] ?: unkTokenId)
            }
            inputIdsList.add(eosTokenId)
            val inputIdsArray = inputIdsList.toLongArray()

            val seqLen = inputIdsArray.size.toLong()
            val attentionMaskArray = LongArray(inputIdsArray.size) { 1L }

            val inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIdsArray), longArrayOf(1, seqLen))
            val maskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMaskArray), longArrayOf(1, seqLen))

            val encoderOutputs = encoderSession!!.run(mapOf("input_ids" to inputIdsTensor, "attention_mask" to maskTensor))
            val encoderHiddenStates = encoderOutputs[0] as OnnxTensor

            val generatedOnnxIds = mutableListOf<Long>()
            var decoderInputIds = longArrayOf(padTokenId)

            for (step in 0 until 80) {
                val decSeqLen = decoderInputIds.size.toLong()
                val decInputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(decoderInputIds), longArrayOf(1, decSeqLen))
                val decoderOutputs = decoderSession!!.run(mapOf("input_ids" to decInputTensor, "encoder_hidden_states" to encoderHiddenStates, "encoder_attention_mask" to maskTensor))
                val logitsTensor = decoderOutputs[0] as OnnxTensor
                val floatBuffer = logitsTensor.floatBuffer

                val vocabSize = logitsTensor.info.shape[2].toInt()
                val lastTokenOffset = (decSeqLen.toInt() - 1) * vocabSize

                var maxVal = Float.NEGATIVE_INFINITY
                var maxIdx = 0L
                for (i in 0 until vocabSize) {
                    val v = floatBuffer.get(lastTokenOffset + i)
                    if (v > maxVal) { maxVal = v; maxIdx = i.toLong() }
                }

                decInputTensor.close()
                decoderOutputs.close()
                if (maxIdx == eosTokenId) break
                generatedOnnxIds.add(maxIdx)
                decoderInputIds = decoderInputIds + maxIdx
            }

            inputIdsTensor.close()
            maskTensor.close()
            encoderHiddenStates.close()
            encoderOutputs.close()

            val sb = StringBuilder()
            for (id in generatedOnnxIds) {
                if (id == padTokenId || id == eosTokenId || id == unkTokenId) continue
                sb.append(idToPiece[id] ?: "")
            }
            Result.success(sb.toString().replace("\u2581", " ").trim())

        } catch (e: Exception) {
            Result.success("❌ Офлайн-модель для '$sourceLang' не установлена")
        }
    }
}