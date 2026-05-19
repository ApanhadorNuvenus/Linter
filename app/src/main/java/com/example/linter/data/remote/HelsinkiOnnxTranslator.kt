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
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null

    private lateinit var sourceModel: Model
    private val spAlgorithm = SentencePieceAlgorithm(false, Scoring.HIGHEST_SCORE)

    private val pieceToId = mutableMapOf<String, Long>()
    private val idToPiece = mutableMapOf<Long, String>()

    private var padTokenId: Long = 59513L
    private var eosTokenId: Long = 0L
    private var unkTokenId: Long = 2L

    init {
        initModels()
    }

    private fun getAssetFilePath(fileName: String): String {
        val file = File(context.cacheDir, fileName.substringAfterLast('/'))
        if (!file.exists()) {
            context.assets.open(fileName).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return file.absolutePath
    }

    private fun initModels() {
        try {
            val opts = OrtSession.SessionOptions().apply {
                addConfigEntry("session.load_model_format", "ONNX")
            }

            val encoderBytes = context.assets.open("onnx/encoder_model_quantized.onnx").readBytes()
            encoderSession = env.createSession(encoderBytes, opts)

            val decoderBytes = context.assets.open("onnx/decoder_model_quantized.onnx").readBytes()
            decoderSession = env.createSession(decoderBytes, opts)

            val sourceSpmPath = getAssetFilePath("onnx/source.spm")
            sourceModel = Model.parseFrom(Paths.get(sourceSpmPath))

            val vocabBytes = context.assets.open("onnx/vocab.json").readBytes()
            val vocabJson = JSONObject(String(vocabBytes))

            vocabJson.keys().forEach { key ->
                val id = vocabJson.getLong(key)
                pieceToId[key] = id
                idToPiece[id] = key
            }

            padTokenId = pieceToId["<pad>"] ?: 59513L
            eosTokenId = pieceToId["</s>"] ?: 0L
            unkTokenId = pieceToId["<unk>"] ?: 2L

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Универсальный метод, который с помощью рефлексии достает строку из SentencePiece4J
    private fun getPieceStrFromModel(model: Model, spId: Int): String {
        try {
            // 1. Пытаемся вытащить скрытый массив или список (обычно называется pieces)
            for (field in model::class.java.declaredFields) {
                field.isAccessible = true
                val obj = field.get(model)
                if (obj is Array<*>) {
                    if (obj.isNotEmpty() && obj[0] is String) {
                        return obj.getOrNull(spId)?.toString() ?: "<unk>"
                    }
                } else if (obj is List<*>) {
                    if (obj.isNotEmpty() && obj[0] is String) {
                        return obj.getOrNull(spId)?.toString() ?: "<unk>"
                    }
                }
            }

            // 2. Если поля нет, ищем скрытый метод-геттер
            for (method in model::class.java.methods) {
                if (method.returnType == String::class.java && method.parameterCount == 1) {
                    val pType = method.parameterTypes[0]
                    if ((pType == Int::class.java || pType == Integer::class.java) && !method.name.contains("decode", true)) {
                        return method.invoke(model, spId) as String
                    }
                }
            }
        } catch (e: Exception) {
            // Игнорируем ошибки рефлексии
        }
        return "<unk>"
    }

    override suspend fun translate(text: String, sourceLang: String, targetLang: String): Result<String> = withContext(Dispatchers.Default) {
        try {
            if (encoderSession == null || decoderSession == null) {
                return@withContext Result.failure(Exception("ONNX модели не загрузились"))
            }

            if (sourceLang != "en") {
                return@withContext Result.success("[$sourceLang -> ru не поддерживается ONNX]")
            }

            val cleanText = text.replace("\n", " ").trim()
            if (cleanText.isEmpty()) return@withContext Result.success("")

            // ==========================================
            // 1. ТОКЕНИЗАЦИЯ (Source) + ВОССТАНОВЛЕНИЕ ID
            // ==========================================
            val spIds = sourceModel.encodeNormalized(cleanText, spAlgorithm)

            val inputIdsList = mutableListOf<Long>()
            for (spId in spIds) {
                // Достаем оригинальную строку сабворда через рефлексию
                val pieceStr = getPieceStrFromModel(sourceModel, spId)

                // Превращаем строку в настоящий ONNX-ID через vocab.json
                val onnxId = pieceToId[pieceStr] ?: unkTokenId
                inputIdsList.add(onnxId)
            }

            inputIdsList.add(eosTokenId) // </s>

            val inputIdsArray = inputIdsList.toLongArray()
            val seqLen = inputIdsArray.size.toLong()
            val attentionMaskArray = LongArray(inputIdsArray.size) { 1L }

            val inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIdsArray), longArrayOf(1, seqLen))
            val maskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMaskArray), longArrayOf(1, seqLen))

            // ==========================================
            // 2. ВЫПОЛНЕНИЕ ЭНКОДЕРА
            // ==========================================
            val encoderOutputs = encoderSession!!.run(mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to maskTensor
            ))
            val encoderHiddenStates = encoderOutputs[0] as OnnxTensor

            // ==========================================
            // 3. ГЕНЕРАЦИЯ ДЕКОДЕРОМ (Greedy Search)
            // ==========================================
            val generatedOnnxIds = mutableListOf<Long>()
            var decoderInputIds = longArrayOf(padTokenId)
            val maxOutputLength = 80

            for (step in 0 until maxOutputLength) {
                val decSeqLen = decoderInputIds.size.toLong()
                val decInputTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(decoderInputIds), longArrayOf(1, decSeqLen))

                val decoderOutputs = decoderSession!!.run(mapOf(
                    "input_ids" to decInputTensor,
                    "encoder_hidden_states" to encoderHiddenStates,
                    "encoder_attention_mask" to maskTensor
                ))

                val logitsTensor = decoderOutputs[0] as OnnxTensor
                val floatBuffer = logitsTensor.floatBuffer

                val shape = logitsTensor.info.shape
                val seqLenOut = shape[1].toInt()
                val vocabSize = shape[2].toInt()
                val lastTokenOffset = (seqLenOut - 1) * vocabSize

                var maxVal = Float.NEGATIVE_INFINITY
                var maxIdx = 0L
                for (i in 0 until vocabSize) {
                    val v = floatBuffer.get(lastTokenOffset + i)
                    if (v > maxVal) {
                        maxVal = v
                        maxIdx = i.toLong()
                    }
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

            // ==========================================
            // 4. ДЕТОКЕНИЗАЦИЯ (СБОРКА ТЕКСТА ИЗ ID)
            // ==========================================
            val sb = StringBuilder()
            for (id in generatedOnnxIds) {
                if (id == padTokenId || id == eosTokenId || id == unkTokenId) continue
                val pieceStr = idToPiece[id] ?: ""
                sb.append(pieceStr)
            }

            // Метасимвол U+2581 ( ) означает начало нового слова. Меняем его на обычный пробел.
            val resultText = sb.toString().replace("\u2581", " ").trim()

            Result.success(resultText)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}