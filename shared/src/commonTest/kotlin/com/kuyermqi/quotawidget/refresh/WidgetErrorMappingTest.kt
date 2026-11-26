package com.kuyermqi.quotawidget.refresh

import com.kuyermqi.quotawidget.domain.HttpStatusException
import com.kuyermqi.quotawidget.domain.QuotaUnavailableException
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.domain.WidgetErrorKind
import com.kuyermqi.quotawidget.network.InvalidBaseUrlException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetErrorMappingTest {
    @Test
    fun mapsEachFailureToItsKind() {
        assertEquals(
            WidgetErrorKind.SessionExpired,
            SessionExpiredException().toWidgetErrorKind(),
        )
        assertEquals(
            WidgetErrorKind.QuotaUnavailable,
            QuotaUnavailableException("网关未返回额度窗口").toWidgetErrorKind(),
        )
        assertEquals(
            WidgetErrorKind.HttpStatus(404),
            HttpStatusException(404, "接口不存在").toWidgetErrorKind(),
        )
        assertEquals(
            WidgetErrorKind.HttpStatus(503),
            HttpStatusException(503, "upstream down").toWidgetErrorKind(),
        )
        assertEquals(WidgetErrorKind.Network, IOException("network down").toWidgetErrorKind())
        assertEquals(
            WidgetErrorKind.InvalidBaseUrl,
            InvalidBaseUrlException().toWidgetErrorKind(),
        )
        assertEquals(
            WidgetErrorKind.ParseFailure,
            SerializationException("bad json").toWidgetErrorKind(),
        )
        assertEquals(
            WidgetErrorKind.Unknown(),
            IllegalStateException("unexpected payload").toWidgetErrorKind(),
        )
    }

    @Test
    fun walksTheCauseChain() {
        assertEquals(
            WidgetErrorKind.Network,
            IllegalStateException("wrapped", IOException("timeout")).toWidgetErrorKind(),
        )
        assertEquals(
            WidgetErrorKind.HttpStatus(500),
            IllegalStateException("wrapped", HttpStatusException(500, "boom")).toWidgetErrorKind(),
        )
        assertEquals(
            WidgetErrorKind.InvalidBaseUrl,
            IllegalStateException("wrapped", InvalidBaseUrlException()).toWidgetErrorKind(),
        )
    }

    @Test
    fun cyclicCauseChainsDoNotHang() {
        val cyclic = object : Exception("loop") {
            override val cause: Throwable get() = this
        }

        assertEquals(WidgetErrorKind.Unknown(), cyclic.toWidgetErrorKind())
    }

    @Test
    fun retrySemanticsStayIndependentOfTheKind() {
        assertEquals(true, isRetryableFailure(IOException("io")))
        assertEquals(true, isRetryableFailure(HttpStatusException(503, "boom")))
        assertEquals(false, isRetryableFailure(HttpStatusException(404, "missing")))
        assertEquals(false, isRetryableFailure(InvalidBaseUrlException()))
    }
}
