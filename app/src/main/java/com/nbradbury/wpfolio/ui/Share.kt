package com.nbradbury.wpfolio.ui

import android.content.Context
import android.content.Intent

/** Opens Android's share sheet for a link, with [title] as the subject and leading line. */
fun Context.shareLink(title: String, url: String) {
    val text = listOf(title, url).filter { it.isNotBlank() }.joinToString("\n")
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        if (title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(send, null))
}
