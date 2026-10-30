package com.nbradbury.wpreadme.nav

import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute
@Serializable data object FeedRoute
@Serializable data object SitesRoute
@Serializable data object SavedRoute
@Serializable data class AddRoute(val fromOnboarding: Boolean = false)
@Serializable data class ArticleRoute(val postId: String)
@Serializable data class SiteRoute(val siteId: String)
