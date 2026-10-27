package com.example.notebrain.ui

import android.content.Intent

sealed class ShareResult {
    data class Success(val title: String, val content: String) : ShareResult()
    data class EmptyPayload(val message: String) : ShareResult()
    object Ignored : ShareResult()
}

object ShareHandler {
    const val EXTRA_HANDLED = "com.example.notebrain.EXTRA_SHARE_HANDLED"

    fun extractShareContent(intent: Intent?): ShareResult {
        if (intent == null || intent.action != Intent.ACTION_SEND) {
            return ShareResult.Ignored
        }

        if (intent.getBooleanExtra(EXTRA_HANDLED, false)) {
            return ShareResult.Ignored
        }

        val type = intent.type
        if (type == null || !type.startsWith("text/")) {
            return ShareResult.Ignored
        }

        intent.putExtra(EXTRA_HANDLED, true)

        val rawText = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()

        val rawSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()

        return parseTextAndSubject(rawText, rawSubject)
    }

    fun parseTextAndSubject(rawText: String?, rawSubject: String?): ShareResult {
        val textToUse = rawText?.trim() ?: ""
        val subjectToUse = rawSubject?.trim() ?: ""

        if (textToUse.isBlank() && subjectToUse.isBlank()) {
            return ShareResult.EmptyPayload("No text found to save to JustNote.")
        }

        val derivedTitle: String
        val derivedContent: String

        if (subjectToUse.isNotBlank()) {
            derivedTitle = subjectToUse
            derivedContent = if (textToUse.isBlank()) subjectToUse else textToUse
        } else {
            val lines = textToUse.lines().filter { it.isNotBlank() }
            if (lines.isNotEmpty()) {
                val firstLine = lines.first().trim()
                val isUrl = firstLine.startsWith("http://", ignoreCase = true) ||
                        firstLine.startsWith("https://", ignoreCase = true)

                if (!isUrl && firstLine.length <= 80 && lines.size > 1) {
                    derivedTitle = firstLine
                    derivedContent = textToUse
                } else {
                    derivedTitle = ""
                    derivedContent = textToUse
                }
            } else {
                derivedTitle = ""
                derivedContent = textToUse
            }
        }

        return ShareResult.Success(
            title = derivedTitle,
            content = derivedContent
        )
    }
}
