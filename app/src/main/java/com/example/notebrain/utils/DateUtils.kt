package com.example.notebrain.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateUtils {
    private const val ISO_8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

    fun currentUtcIsoString(): String {
        val sdf = SimpleDateFormat(ISO_8601_FORMAT, Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    fun formatDisplayDate(isoString: String?): String {
        if (isoString.isNullOrBlank()) return ""
        return try {
            val sdf = SimpleDateFormat(ISO_8601_FORMAT, Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(isoString) ?: return isoString
            val displayFormat = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
            displayFormat.format(date)
        } catch (e: Exception) {
            isoString
        }
    }
}
