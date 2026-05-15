package com.example.linter.data.youtube

import com.example.linter.data.local.ObjectBox
import com.example.linter.data.local.entity.YoutubeVideoEntity
import com.example.linter.domain.model.SubtitleBlock
import com.example.linter.domain.model.TextTranslator
import com.example.linter.domain.model.VideoPlaybackInfo
import com.example.linter.domain.model.YoutubeVideo
import com.example.linter.domain.repository.YoutubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.regex.Pattern

class YoutubeRepositoryImpl(
    private val translator: TextTranslator
) : YoutubeRepository {

    private val box get() = ObjectBox.store.boxFor(YoutubeVideoEntity::class.java)

    override suspend fun extractAndSaveVideo(url: String): Result<YoutubeVideo> = withContext(Dispatchers.IO) {
        try {
            val info = StreamInfo.getInfo(url)

            val allSubs = info.subtitles.orEmpty()
            val enSubs = allSubs.find { it.locale.language.startsWith("en", ignoreCase = true) }

            if (enSubs == null) {
                return@withContext Result.failure(Exception("У этого видео нет английских субтитров."))
            }

            val entity = YoutubeVideoEntity(
                videoUrl = info.url,
                title = info.name,
                thumbnailUrl = info.thumbnails.firstOrNull()?.url ?: "",
                durationMs = info.duration * 1000L
            )

            val existing = box.all.find { it.videoUrl == entity.videoUrl }
            val id = existing?.id ?: box.put(entity)
            entity.id = id

            Result.success(entity.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSavedVideos(): List<YoutubeVideo> = withContext(Dispatchers.IO) {
        box.all.map { it.toDomain() }
    }

    override suspend fun getVideoById(id: Long): YoutubeVideo? = withContext(Dispatchers.IO) {
        box[id]?.toDomain()
    }

    override suspend fun updateProgress(id: Long, progressMs: Long) = withContext(Dispatchers.IO) {
        val entity = box[id] ?: return@withContext
        entity.progressMs = progressMs
        box.put(entity)
    }

    override suspend fun fetchPlaybackInfo(url: String): Result<VideoPlaybackInfo> = withContext(Dispatchers.IO) {
        try {
            val info = StreamInfo.getInfo(url)

            val videoStreams = info.videoOnlyStreams ?: info.videoStreams
            val videoStream = videoStreams?.maxByOrNull { it.resolution.replace("p", "").toIntOrNull() ?: 0 }
                ?: videoStreams?.firstOrNull()
                ?: throw Exception("Не найден видеопоток")

            val audioStream = info.audioStreams?.maxByOrNull { it.averageBitrate }
                ?: info.audioStreams?.firstOrNull()
                ?: throw Exception("Не найден аудиопоток")

            val allSubs = info.subtitles.orEmpty()
            val enSubInfo = allSubs.find { it.locale.language.startsWith("en", ignoreCase = true) }
                ?: allSubs.firstOrNull()
                ?: throw Exception("Субтитры отсутствуют.")

            val enUrl = enSubInfo.content

            // 1. Извлекаем английские субтитры (наш пуленепробиваемый парсер)
            val enBlocks = fetchAndParseBlocks(enUrl)
            if (enBlocks.isEmpty()) {
                throw Exception("Не удалось распарсить английские субтитры")
            }

            // 2. Пытаемся получить автоперевод Ютуба
            var ruBlocks = emptyList<SubtitleBlock>()
            try {
                val ruUrl = if (enUrl.contains("tlang=")) enUrl else "$enUrl&tlang=ru"
                ruBlocks = fetchAndParseBlocks(ruUrl)
            } catch (e: Exception) {
                println("YouTube заблокировал автоперевод (429). Включаем локальный ML Kit.")
            }

            // 3. Слияние субтитров
            val mergedBlocks = if (ruBlocks.isNotEmpty()) {
                enBlocks.map { enBlock ->
                    val matchingRu = ruBlocks.find { it.startTimeMs == enBlock.startTimeMs }?.sourceText
                        ?: ruBlocks.filter { it.startTimeMs < enBlock.endTimeMs && it.endTimeMs > enBlock.startTimeMs }
                            .joinToString(" ") { it.sourceText }

                    enBlock.copy(translatedText = matchingRu.takeIf { it.isNotBlank() })
                }
            } else {
                enBlocks
            }

            Result.success(
                VideoPlaybackInfo(
                    videoUrl = videoStream.content,
                    audioUrl = audioStream.content,
                    subtitles = mergedBlocks,
                    // Блокируем кнопку Ютуб-перевода, если пришла ошибка 429
                    hasYoutubeTranslation = ruBlocks.isNotEmpty()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun translateBlockLocally(block: SubtitleBlock): SubtitleBlock {
        val translation = translator.translate(block.sourceText, "en", "ru").getOrNull()
        return block.copy(translatedText = translation ?: "Ошибка перевода")
    }

    // Универсальный извлекатель (пробует JSON, при неудаче пробует XML)
    private fun fetchAndParseBlocks(url: String): List<SubtitleBlock> {
        // Заменяем vtt/xml на json3
        val jsonUrl = url.replace(Regex("fmt=[a-zA-Z0-9]+"), "fmt=json3").let {
            if (!it.contains("fmt=json3")) "$it&fmt=json3" else it
        }

        try {
            val jsonRaw = downloadString(jsonUrl)
            val blocks = parseJson3(jsonRaw)
            if (blocks.isNotEmpty()) return blocks
        } catch (e: Exception) { /* Игнорируем и пробуем резервный вариант */ }

        // Резервный план: XML формат (srv1)
        val xmlUrl = url.replace(Regex("fmt=[a-zA-Z0-9]+"), "fmt=srv1").let {
            if (!it.contains("fmt=srv1")) "$it&fmt=srv1" else it
        }

        try {
            val xmlRaw = downloadString(xmlUrl)
            val blocks = parseSubtitles(xmlRaw)
            if (blocks.isNotEmpty()) return blocks
        } catch (e: Exception) { /* Ничего не вышло */ }

        return emptyList()
    }

    private fun downloadString(urlStr: String): String {
        val request = Request("GET", urlStr, emptyMap(), null, null, false)
        val response = NewPipeDownloader.getInstance().execute(request)

        val responseCode = response.responseCode()
        val responseBody = response.responseBody()

        if (responseCode in 200..299) {
            return responseBody
        } else {
            throw Exception("Ошибка загрузки сабов: HTTP $responseCode")
        }
    }

    private fun parseJson3(json: String): List<SubtitleBlock> {
        val blocks = mutableListOf<SubtitleBlock>()
        try {
            val root = JSONObject(json)
            val events = root.optJSONArray("events") ?: return blocks
            var id = 0
            for (i in 0 until events.length()) {
                val event = events.optJSONObject(i) ?: continue
                val startMs = event.optLong("tStartMs", 0L)
                val durMs = event.optLong("dDurationMs", 0L)

                val segs = event.optJSONArray("segs") ?: continue
                val textBuilder = StringBuilder()
                for (j in 0 until segs.length()) {
                    val seg = segs.optJSONObject(j) ?: continue
                    textBuilder.append(seg.optString("utf8", ""))
                }
                val text = textBuilder.toString().replace("\n", " ").trim()
                if (text.isBlank()) continue

                blocks.add(SubtitleBlock(
                    id = id++,
                    startTimeMs = startMs,
                    endTimeMs = startMs + durMs,
                    sourceText = text
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return blocks
    }

    private fun parseSubtitles(xml: String): List<SubtitleBlock> {
        val blocks = mutableListOf<SubtitleBlock>()
        // КРИТИЧНО: Флаг DOTALL заставляет ".*?" читать многострочный текст внутри тега!
        val srv1Pattern = Pattern.compile("<text start=\"([\\d.]+)\"(?: dur=\"([\\d.]+)\")?[^>]*>(.*?)</text>", Pattern.DOTALL)
        val srv1Matcher = srv1Pattern.matcher(xml)

        var id = 0
        while (srv1Matcher.find()) {
            val startSec = srv1Matcher.group(1)?.toFloatOrNull() ?: continue
            val durSec = srv1Matcher.group(2)?.toFloatOrNull() ?: 2.0f
            var text = srv1Matcher.group(3) ?: continue

            text = text.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("\n", " ")
            text = text.replace(Regex("<[^>]*>"), "")

            if (text.isBlank()) continue

            blocks.add(
                SubtitleBlock(
                    id = id++,
                    startTimeMs = (startSec * 1000).toLong(),
                    endTimeMs = ((startSec + durSec) * 1000).toLong(),
                    sourceText = text.trim()
                )
            )
        }
        return blocks
    }

    private fun YoutubeVideoEntity.toDomain() = YoutubeVideo(id, videoUrl, title, thumbnailUrl, progressMs, durationMs)
}