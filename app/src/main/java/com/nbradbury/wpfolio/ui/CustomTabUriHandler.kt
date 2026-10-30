package com.nbradbury.wpfolio.ui

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri

/**
 * Opens links in a Custom Tab, so reading a link keeps you in the app with one tap back to the
 * post. Without a Custom Tabs browser it falls back to a normal browser intent.
 */
class CustomTabUriHandler(private val context: Context) : UriHandler {
    override fun openUri(uri: String) {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .build()
            .launchUrl(context, uri.toUri())
    }
}

/** Opens [url] in the user's browser app rather than a Custom Tab, for an explicit "Open in browser". */
fun Context.openInBrowser(url: String) {
    if (!WebOnlyUriHandler.isWebUrl(url)) return
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)) }
}
