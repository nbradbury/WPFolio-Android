package com.nbradbury.wpfolio.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nbradbury.wpfolio.data.wpcom.SiteSearchResult
import com.nbradbury.wpfolio.ui.components.SiteAvatar
import com.nbradbury.wpfolio.ui.components.accentFor
import com.nbradbury.wpfolio.ui.components.displayUrl
import com.nbradbury.wpfolio.ui.theme.WpFolio
import com.nbradbury.wpfolio.ui.theme.body
import com.nbradbury.wpfolio.ui.theme.ui

/**
 * WordPress.com's suggestions, shown on Add before a search and on an empty feed. [subscribing]
 * is the feed URL being subscribed to, if any.
 */
@Composable
internal fun SuggestedSites(
    sites: List<SiteSearchResult>,
    subscribing: String?,
    onOpen: (SiteSearchResult) -> Unit,
    onSubscribe: (SiteSearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sites.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Suggested sites", style = ui(16f, 600), color = WpFolio.colors.text)
        sites.forEach { site ->
            SiteResultRow(
                result = site,
                subscribing = subscribing == site.feedUrl,
                enabled = subscribing == null,
                onOpen = { onOpen(site) },
                onSubscribe = { onSubscribe(site) },
            )
        }
    }
}

@Composable
internal fun SiteResultRow(
    result: SiteSearchResult,
    subscribing: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onSubscribe: () -> Unit,
) {
    val colors = WpFolio.colors
    Row(
        Modifier.clickable(onClickLabel = "See posts", onClick = onOpen),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteAvatar(result.iconUrl, result.name, accentFor(null, result.feedUrl), 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(result.name, style = ui(15f, 600), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                displayUrl(result.siteUrl),
                style = ui(12.5f, 500),
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (result.description.isNotBlank()) {
                Text(
                    result.description,
                    style = body(13f, lineHeight = 1.35f),
                    color = colors.bodyMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            result.subscribedSiteId != null -> Icon(
                Icons.Rounded.Check,
                contentDescription = "Subscribed",
                tint = colors.textSecondary,
                modifier = Modifier.size(22.dp),
            )
            subscribing -> CircularProgressIndicator(
                Modifier.size(22.dp),
                color = colors.textSecondary,
                strokeWidth = 2.dp,
            )
            else -> Box(
                Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(colors.inverse)
                    .clickable(enabled = enabled, onClickLabel = "Subscribe to ${result.name}", onClick = onSubscribe)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Subscribe", style = ui(13f, 600), color = colors.onInverse)
            }
        }
    }
}
