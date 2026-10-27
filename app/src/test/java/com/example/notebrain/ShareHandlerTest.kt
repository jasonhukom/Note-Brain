package com.example.notebrain

import com.example.notebrain.ui.ShareHandler
import com.example.notebrain.ui.ShareResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareHandlerTest {

    @Test
    fun testSharePlainTextWithoutSubject() {
        val result = ShareHandler.parseTextAndSubject("Hello world!", null)
        assertTrue(result is ShareResult.Success)
        val success = result as ShareResult.Success
        assertEquals("", success.title)
        assertEquals("Hello world!", success.content)
    }

    @Test
    fun testShareUrlAndSubject() {
        val result = ShareHandler.parseTextAndSubject("https://example.com/article", "Interesting Article")
        assertTrue(result is ShareResult.Success)
        val success = result as ShareResult.Success
        assertEquals("Interesting Article", success.title)
        assertEquals("https://example.com/article", success.content)
    }

    @Test
    fun testShareMultilineTextWithDerivedTitle() {
        val multiline = "My Shopping List\n- Milk\n- Eggs\n- Bread"
        val result = ShareHandler.parseTextAndSubject(multiline, null)
        assertTrue(result is ShareResult.Success)
        val success = result as ShareResult.Success
        assertEquals("My Shopping List", success.title)
        assertEquals(multiline, success.content)
    }

    @Test
    fun testEmptyTextPayloadReturnsEmptyPayloadResult() {
        val result = ShareHandler.parseTextAndSubject("   ", "  ")
        assertTrue(result is ShareResult.EmptyPayload)
    }
}
