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

        // 1. Копируем заголовки от экстрактора NewPipe
        request.headers().forEach { (key, values) ->
            connection.setRequestProperty(key, values.joinToString(","))
        }

        // 2. Типичный Android User-Agent
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UP1A.231005.007) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Mobile Safari/537.36"
        )

        // 3. КРИТИЧНО: Принудительно запрашиваем ТОЛЬКО gzip!
        // Если оставить дефолтный "gzip, deflate, br", YouTube пришлет Brotli,
        // который встроенный коннектор Android не умеет распаковывать.
        connection.setRequestProperty("Accept-Encoding", "gzip")

        // 4. КРИТИЧНО: Добавляем Cookie для обхода окна согласия (Consent Wall).
        // Если ютуб отдает страницу согласия, парсер ломается на поиске visitorData.
        val existingCookie = connection.getRequestProperty("Cookie") ?: ""
        if (!existingCookie.contains("CONSENT=")) {
            val bypassCookie = "CONSENT=YES+cb.20210328-17-p0.en+FX+478"
            connection.setRequestProperty(
                "Cookie",
                if (existingCookie.isEmpty()) bypassCookie else "$existingCookie; $bypassCookie"
            )
        }

        // 5. Локализация (чтобы не было региональных редиректов)
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")

        // 6. КРИТИЧНО: Правильная отправка тела запроса (Payload) для POST/PUT
        // Без этого внутренние API запросы YouTube (youtubei/v1) будут возвращать ошибку 400
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

        // Получаем поток данных в зависимости от успешности ответа
        var inputStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream

        // 7. Распаковываем GZIP на лету
        if ("gzip".equals(connection.contentEncoding, ignoreCase = true) && inputStream != null) {
            inputStream = GZIPInputStream(inputStream)
        }

        // Читаем чистый раскодированный текст (HTML или JSON)
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