package com.example.linter.data.youtube

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.net.URL
import java.util.zip.GZIPInputStream
import javax.net.ssl.HttpsURLConnection

class NewPipeDownloader private constructor() : Downloader() {

    override fun execute(request: Request): Response {
        val url = URL(request.url())
        val connection = url.openConnection() as HttpsURLConnection

        connection.requestMethod = request.httpMethod()
        connection.readTimeout = 30000
        connection.connectTimeout = 30000

        // 1. Копируем заголовки, сформированные NewPipeExtractor
        request.headers().forEach { (key, values) ->
            connection.setRequestProperty(key, values.joinToString(","))
        }

        // 2. Стабильный Android User-Agent
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UP1A.231005.007) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Mobile Safari/537.36"
        )

        // 3. Запрашиваем gzip сжатие (позволяет избежать Brotli, который не поддерживается из коробки)
        connection.setRequestProperty("Accept-Encoding", "gzip")

        // 4. ИСПРАВЛЕНИЕ: Обход Consent Wall Google/YouTube в ЕС (GDPR)
        // Добавляем современные куки согласия, актуальные на 2025–2026 годы.
        // SOCS=CAI сообщает YouTube, что настройки кук были подтверждены, предотвращая редирект.
        val existingCookie = connection.getRequestProperty("Cookie") ?: ""
        val cookiesToAdd = mutableListOf<String>()

        if (!existingCookie.contains("CONSENT=")) {
            cookiesToAdd.add("CONSENT=YES+cb.20230308-07-p0.en+FX+910")
        }
        if (!existingCookie.contains("SOCS=")) {
            // CAI или CAISAiAD - стандартные значения для обхода согласия в NewPipeExtractor
            cookiesToAdd.add("SOCS=CAI")
        }

        if (cookiesToAdd.isNotEmpty()) {
            val bypassCookieString = cookiesToAdd.joinToString("; ")
            connection.setRequestProperty(
                "Cookie",
                if (existingCookie.isEmpty()) bypassCookieString else "$existingCookie; $bypassCookieString"
            )
        }

        // 5. Локализация для стабильного ответа без региональных редиректов
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")

        // 6. Корректная отправка payloads для POST/PUT запросов (Innertube API)
        if (request.httpMethod() == "POST" || request.httpMethod() == "PUT") {
            val data = request.dataToSend()
            if (data != null) {
                connection.doOutput = true
                connection.outputStream.use { os ->
                    os.write(data)
                    os.flush()
                }
            }
        }

        val responseCode = connection.responseCode
        val responseMessage = connection.responseMessage

        var inputStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream

        // 7. Распаковка GZIP
        if ("gzip".equals(connection.contentEncoding, ignoreCase = true) && inputStream != null) {
            inputStream = GZIPInputStream(inputStream)
        }

        val responseBody = inputStream?.bufferedReader()?.use { it.readText() } ?: ""

        val responseHeaders = mutableMapOf<String, List<String>>()
        connection.headerFields.forEach { (key, value) ->
            if (key != null) responseHeaders[key] = value
        }

        return Response(responseCode, responseMessage, responseHeaders, responseBody, request.url())
    }

    companion object {
        private var instance: NewPipeDownloader? = null
        fun getInstance(): NewPipeDownloader {
            if (instance == null) {
                instance = NewPipeDownloader()
            }
            return instance!!
        }
    }
}