package com.kuyermqi.quotawidget.deepseek.peakvalley

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fixed HTTPS source; timeouts are explicit so a weekly worker cannot hang. */
class HttpHolidayCalendarFetcher : HolidayCalendarFetcher {
    override suspend fun fetch(year: Int): HolidayFetchResult = withContext(Dispatchers.IO) {
        val url = URL("$SOURCE_BASE$year.json")
        var connection: HttpURLConnection? = null
        try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "quota-widget-holiday/1.0")
            }
            val code = connection.responseCode
            if (connection.url.protocol != "https") {
                return@withContext HolidayFetchResult.Failure("insecure redirect")
            }
            when (code) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                        it.readText()
                    }
                    if (body.isBlank()) {
                        HolidayFetchResult.Failure("empty response")
                    } else {
                        HolidayFetchResult.Success(body)
                    }
                }
                HttpURLConnection.HTTP_NOT_FOUND -> HolidayFetchResult.NotPublished
                else -> HolidayFetchResult.Failure("http $code")
            }
        } catch (error: IOException) {
            HolidayFetchResult.Failure(error.message.orEmpty())
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val SOURCE_BASE =
            "https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
    }
}
