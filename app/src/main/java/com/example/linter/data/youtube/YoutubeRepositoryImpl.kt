package com.example.linter.data.youtube

import android.util.Log
import com.example.linter.data.local.ObjectBox
import com.example.linter.data.local.entity.SubtitleBlockEntity
import com.example.linter.data.local.entity.SubtitleBlockEntity_
import com.example.linter.data.local.entity.YoutubeVideoEntity
import com.example.linter.domain.model.SubtitleBlock
import com.example.linter.domain.model.TextTranslator
import com.example.linter.domain.model.VideoPlaybackInfo
import com.example.linter.domain.model.YoutubeVideo
import com.example.linter.domain.repository.YoutubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.regex.Pattern

class YoutubeRepositoryImpl(
    private val translator: TextTranslator
) : YoutubeRepository {

    private val videoBox get() = ObjectBox.store.boxFor(YoutubeVideoEntity::class.java)
    private val subtitleBox get() = ObjectBox.store.boxFor(SubtitleBlockEntity::class.java)

    private val TAG = "LINTER_DEBUG"

    override suspend fun extractAndSaveVideo(url: String): Result<YoutubeVideo> = withContext(Dispatchers.IO) {
        try {
            val info = StreamInfo.getInfo(url)

            val entity = YoutubeVideoEntity(
                videoUrl = info.url,
                title = info.name,
                thumbnailUrl = info.thumbnails.firstOrNull()?.url ?: "",
                durationMs = info.duration * 1000L
            )

            val existing = videoBox.all.find { it.videoUrl == entity.videoUrl }
            val id = existing?.id ?: videoBox.put(entity)
            entity.id = id

            Result.success(entity.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSavedVideos(): List<YoutubeVideo> = withContext(Dispatchers.IO) {
        videoBox.all.map { it.toDomain() }
    }

    override suspend fun getVideoById(id: Long): YoutubeVideo? = withContext(Dispatchers.IO) {
        videoBox[id]?.toDomain()
    }

    override suspend fun updateProgress(id: Long, progressMs: Long) = withContext(Dispatchers.IO) {
        val entity = videoBox[id] ?: return@withContext
        entity.progressMs = progressMs
        videoBox.put(entity)
    }

    override suspend fun deleteVideo(id: Long) {
        withContext(Dispatchers.IO) {
            val blocksToRemove = subtitleBox.query(SubtitleBlockEntity_.youtubeVideoId.equal(id)).build().find()
            subtitleBox.remove(blocksToRemove)
            videoBox.remove(id)
        }
    }

    override suspend fun fetchPlaybackInfo(videoId: Long, url: String): Result<VideoPlaybackInfo> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "===================================================")
            Log.i(TAG, "🎬 [SUBTITLE_SOURCE] ЗАПУСК ВИДЕО ID: $videoId")

            val info = StreamInfo.getInfo(url)

            // ИСПРАВЛЕНИЕ ОШИБКИ ПОТОКОВ: Объединяем списки, чтобы точно найти видео
            val allVideoStreams = info.videoOnlyStreams.orEmpty() + info.videoStreams.orEmpty()
            val videoStream = allVideoStreams.maxByOrNull { it.resolution.replace("p", "").toIntOrNull() ?: 0 }
                ?: allVideoStreams.firstOrNull() ?: throw Exception("Не найден видеопоток")

            val allAudioStreams = info.audioStreams.orEmpty()
            val audioStream = allAudioStreams.maxByOrNull { it.averageBitrate }
                ?: allAudioStreams.firstOrNull() ?: throw Exception("Не найден аудиопоток")

            // 1. ПРОВЕРКА КЭША
            val cachedEntities = subtitleBox.query(SubtitleBlockEntity_.youtubeVideoId.equal(videoId)).build().find()

            if (cachedEntities.isNotEmpty()) {
                Log.i(TAG, "🟢 [SUBTITLE_SOURCE] ВЗЯТО ИЗ КЭША (ObjectBox). Загружено ${cachedEntities.size} блоков. Сеть не используется.")
                Log.i(TAG, "===================================================")

                val domainBlocks = cachedEntities.map {
                    SubtitleBlock(it.id, it.startTimeMs, it.endTimeMs, it.sourceText, it.translatedText)
                }.sortedBy { it.startTimeMs }

                return@withContext Result.success(
                    VideoPlaybackInfo(videoStream.content, audioStream.content, domainBlocks, true)
                )
            }

            // Кэша нет, идем в сеть
            val ytVideoId = extractVideoId(url) ?: throw Exception("Не удалось извлечь videoId")

            // 2. ПОЛУЧАЕМ АНГЛИЙСКИЕ
            var enUrl: String? = info.subtitles?.find { it.languageTag.startsWith("en", ignoreCase = true) }?.content

            if (enUrl != null) {
                Log.i(TAG, "🟡 [SUBTITLE_SOURCE] АНГЛ: Найдена ссылка через стандартный NewPipeExtractor.")
            } else {
                Log.w(TAG, "🟠 [SUBTITLE_SOURCE] АНГЛ: NewPipe не нашел субтитры! Используем INNERTUBE API Fallback...")
                enUrl = getInnertubeCaptionUrl(ytVideoId, "en")
            }

            if (enUrl == null) throw Exception("Не удалось найти английские субтитры у этого видео.")

            val enBlocks = fetchAndParseBlocks(enUrl)
            if (enBlocks.isEmpty()) throw Exception("Не удалось распарсить субтитры.")
            Log.i(TAG, "✅ [SUBTITLE_SOURCE] АНГЛ: Успешно скачано и распарсено ${enBlocks.size} блоков.")

            // 3. ПОЛУЧАЕМ РУССКИЕ (ЮТУБ АВТОПЕРЕВОД)
            var ruBlocks = emptyList<SubtitleBlock>()
            try {
                var ruUrl: String? = info.subtitles?.find { it.languageTag.startsWith("ru", ignoreCase = true) }?.content

                if (ruUrl != null) {
                    Log.i(TAG, "🔵 [SUBTITLE_SOURCE] РУС: У видео есть встроенная русская дорожка! Скачиваем её.")
                } else {
                    ruUrl = if (enUrl.contains("tlang=")) enUrl else "$enUrl&tlang=ru"
                    Log.i(TAG, "🟣 [SUBTITLE_SOURCE] РУС: Встроенной дорожки нет. Запрашиваем Автоперевод Ютуба (&tlang=ru).")
                }

                ruBlocks = fetchAndParseBlocks(ruUrl)

                // ИСПРАВЛЕНИЕ: Если блоков 0, значит Ютуб отдал пустой файл. Бросаем ошибку!
                if (ruBlocks.isEmpty()) {
                    throw Exception("Ютуб вернул пустой текст (0 блоков).")
                }

                Log.i(TAG, "✅ [SUBTITLE_SOURCE] РУС: Успешно получен перевод Ютуба (${ruBlocks.size} блоков).")
            } catch (e: Exception) {
                Log.w(TAG, "🔴 [SUBTITLE_SOURCE] РУС: Автоперевод Ютуба недоступен (${e.message}). Включаем локальный ML Kit.")
            }

            val mergedBlocks = if (ruBlocks.isNotEmpty()) mergeSubtitles(enBlocks, ruBlocks) else enBlocks

            // 4. СОХРАНЯЕМ В КЭШ
            Log.i(TAG, "💾 [SUBTITLE_SOURCE] Сохраняем ${mergedBlocks.size} блоков в ObjectBox навсегда...")
            val entitiesToSave = mergedBlocks.map {
                SubtitleBlockEntity(
                    youtubeVideoId = videoId,
                    startTimeMs = it.startTimeMs,
                    endTimeMs = it.endTimeMs,
                    sourceText = it.sourceText,
                    translatedText = it.translatedText
                )
            }
            subtitleBox.put(entitiesToSave)

            val savedBlocks = subtitleBox.query(SubtitleBlockEntity_.youtubeVideoId.equal(videoId)).build().find()
                .map { SubtitleBlock(it.id, it.startTimeMs, it.endTimeMs, it.sourceText, it.translatedText) }
                .sortedBy { it.startTimeMs }

            Log.i(TAG, "===================================================")

            Result.success(
                VideoPlaybackInfo(
                    videoUrl = videoStream.content,
                    audioUrl = audioStream.content,
                    subtitles = savedBlocks,
                    hasYoutubeTranslation = ruBlocks.isNotEmpty()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ [SUBTITLE_SOURCE] КРИТИЧЕСКАЯ ОШИБКА: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun translateAndSaveBlockLocally(block: SubtitleBlock): SubtitleBlock = withContext(Dispatchers.IO) {
        val translation = translator.translate(block.sourceText, "en", "ru").getOrNull()
        val updatedBlock = block.copy(translatedText = translation ?: "Ошибка перевода")

        val entity = subtitleBox.get(block.id)
        if (entity != null) {
            entity.translatedText = updatedBlock.translatedText
            subtitleBox.put(entity)
        }
        updatedBlock
    }

    override suspend fun clearTranslationsForVideo(videoId: Long) = withContext(Dispatchers.IO) {
        val blocks = subtitleBox.query(SubtitleBlockEntity_.youtubeVideoId.equal(videoId)).build().find()
        blocks.forEach { it.translatedText = null }
        subtitleBox.put(blocks)
    }

    // ====================== INNERTUBE API FALLBACK ======================

    private fun getInnertubeCaptionUrl(videoId: String, lang: String): String? {
        try {
            val body = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20230728.00.00")
                    })
                })
            }

            val request = Request(
                "POST",
                "https://www.youtube.com/youtubei/v1/player",
                mapOf(
                    "Content-Type" to listOf("application/json"),
                    "User-Agent" to listOf("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                ),
                body.toString().toByteArray(Charsets.UTF_8),
                null,
                false
            )

            val response = NewPipeDownloader.getInstance().execute(request)
            if (response.responseCode() in 200..299) {
                val json = JSONObject(response.responseBody())
                val tracks = json.optJSONObject("captions")
                    ?.optJSONObject("playerCaptionsTracklistRenderer")
                    ?.optJSONArray("captionTracks") ?: return null

                for (i in 0 until tracks.length()) {
                    val t = tracks.optJSONObject(i) ?: continue
                    if (t.optString("languageCode").startsWith(lang, ignoreCase = true)) {
                        return t.optString("baseUrl")
                    }
                }
                return tracks.optJSONObject(0)?.optString("baseUrl")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // ====================== ПАРСИНГ И СЛИЯНИЕ ======================

    private fun fetchAndParseBlocks(url: String): List<SubtitleBlock> {
        val urlsToTry = listOf(
            url.replace(Regex("fmt=[^&]+"), "fmt=json3").let { if (!it.contains("fmt=json3")) "$it&fmt=json3" else it },
            url.replace(Regex("fmt=[^&]+"), "fmt=srv1").let { if (!it.contains("fmt=srv1")) "$it&fmt=srv1" else it }
        )

        for (u in urlsToTry) {
            try {
                val raw = downloadString(u)
                val jsonBlocks = parseJson3(raw)
                if (jsonBlocks.isNotEmpty()) return jsonBlocks

                val xmlBlocks = parseSubtitles(raw)
                if (xmlBlocks.isNotEmpty()) return xmlBlocks
            } catch (e: Exception) {
                continue
            }
        }
        return emptyList()
    }

    private fun mergeSubtitles(enBlocks: List<SubtitleBlock>, ruBlocks: List<SubtitleBlock>): List<SubtitleBlock> {
        return enBlocks.map { en ->
            val matching = ruBlocks.find { it.startTimeMs == en.startTimeMs }?.sourceText
                ?: ruBlocks.filter { it.startTimeMs < en.endTimeMs && it.endTimeMs > en.startTimeMs }
                    .joinToString(" ") { it.sourceText }

            en.copy(translatedText = matching.takeIf { it.isNotBlank() })
        }
    }

    private fun downloadString(urlStr: String): String {
        val request = Request("GET", urlStr, emptyMap(), null, null, false)
        val response = NewPipeDownloader.getInstance().execute(request)
        val code = response.responseCode()

        if (code in 200..299) {
            return response.responseBody()
        } else {
            throw Exception("HTTP $code при загрузке субтитров")
        }
    }

    private fun parseJson3(json: String): List<SubtitleBlock> {
        val blocks = mutableListOf<SubtitleBlock>()
        try {
            val root = JSONObject(json)
            val events = root.optJSONArray("events") ?: return blocks

            var id = 0L

            for (i in 0 until events.length()) {
                val event = events.optJSONObject(i) ?: continue
                val startMs = event.optLong("tStartMs", 0L)
                val durMs = event.optLong("dDurationMs", 0L)
                val segs = event.optJSONArray("segs") ?: continue

                val text = buildString {
                    for (j in 0 until segs.length()) {
                        append(segs.optJSONObject(j)?.optString("utf8", ""))
                    }
                }.replace("\n", " ").trim()

                if (text.isBlank()) continue

                blocks.add(
                    SubtitleBlock(
                        id = id++,
                        startTimeMs = startMs,
                        endTimeMs = startMs + durMs,
                        sourceText = text
                    )
                )
            }
        } catch (e: Exception) { }
        return blocks
    }

    private fun parseSubtitles(xml: String): List<SubtitleBlock> {
        val blocks = mutableListOf<SubtitleBlock>()
        val pattern = Pattern.compile(
            "<text start=\"([\\d.]+)\"(?: dur=\"([\\d.]+)\")?[^>]*>(.*?)</text>",
            Pattern.DOTALL
        )
        val matcher = pattern.matcher(xml)

        var id = 0L

        while (matcher.find()) {
            val startSec = matcher.group(1)?.toFloatOrNull() ?: continue
            val durSec = matcher.group(2)?.toFloatOrNull() ?: 2.0f
            var text = matcher.group(3) ?: continue

            text = text.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("\n", " ")
                .replace(Regex("<[^>]*>"), "")
                .trim()

            if (text.isBlank()) continue

            blocks.add(
                SubtitleBlock(
                    id = id++,
                    startTimeMs = (startSec * 1000).toLong(),
                    endTimeMs = ((startSec + durSec) * 1000).toLong(),
                    sourceText = text
                )
            )
        }
        return blocks
    }

    private fun extractVideoId(url: String): String? {
        val regex = Regex("(?:v=|youtu\\.be/|embed/|shorts/)([\\w-]{11})")
        return regex.find(url)?.groupValues?.get(1)
    }

    private fun YoutubeVideoEntity.toDomain() = YoutubeVideo(
        id, videoUrl, title, thumbnailUrl, progressMs, durationMs
    )
}