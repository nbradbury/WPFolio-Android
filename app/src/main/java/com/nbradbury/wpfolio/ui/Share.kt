package com.nbradbury.wpfolio.ui

import android.content.Context
import android.content.Intent

/**
 * Opens Android's share sheet for a link, with [title] as the subject and leading line, or with
 * [quote] (a passage the reader selected) in quotes instead of the title.
 */
fun Context.shareLink(title: String, url: String, quote: String = "") {
    val lead = if (quote.isNotBlank()) "\u201c$quote\u201d" else title
    val text = listOf(lead, url).filter { it.isNotBlank() }.joinToString("\n")
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        if (title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(send, null))
}
