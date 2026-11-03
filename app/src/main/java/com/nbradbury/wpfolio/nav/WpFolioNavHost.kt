package com.nbradbury.wpfolio.nav

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.nbradbury.wpfolio.container
import com.nbradbury.wpfolio.data.PostList
import com.nbradbury.wpfolio.data.wpcom.SiteSearchResult
import com.nbradbury.wpfolio.ui.add.AddScreen
import com.nbradbury.wpfolio.ui.article.ArticleScreen
import com.nbradbury.wpfolio.ui.components.Tab
import com.nbradbury.wpfolio.ui.feed.FeedScreen
import com.nbradbury.wpfolio.ui.onboarding.OnboardingScreen
import com.nbradbury.wpfolio.ui.postlist.PostListScreen
import com.nbradbury.wpfolio.ui.site.SitePreviewScreen
import com.nbradbury.wpfolio.ui.site.SiteScreen
import com.nbradbury.wpfolio.ui.sites.SitesScreen
import kotlinx.coroutines.launch

@Composable
fun WpFolioNavHost(startDestination: Any) {
    val nav = rememberNavController()
    val signIn = rememberSignIn()
    val onTab: (Tab) -> Unit = nav::selectTab
    val openPost: (String) -> Unit = { nav.navigate(ArticleRoute(it)) }
    val openSitePost: (String) -> Unit = { nav.navigate(ArticleRoute(it, PostList.SITE)) }
    val openSite: (String) -> Unit = { nav.navigate(SiteRoute(it)) }
    // Sites already followed open as usual; others open as a preview.
    val openSearchResult: (SiteSearchResult) -> Unit = { result ->
        nav.navigate(result.subscribedSiteId?.let(::SiteRoute) ?: result.toPreviewRoute())
    }

    NavHost(
        navController = nav,
        startDestination = startDestination,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onSignIn = signIn,
                // The empty feed offers suggestions, sign-in and Add, so the reader chooses from there.
                onContinue = { nav.navigate(FeedRoute) { popUpTo<OnboardingRoute> { inclusive = true } } },
                onSignedIn = { nav.navigate(FeedRoute) { popUpTo<OnboardingRoute> { inclusive = true } } },
            )
        }
        composable<FeedRoute> {
            FeedScreen(
                onOpenPost = openPost,
                onTab = onTab,
                onSignIn = signIn,
                onOpenList = { nav.navigate(PostListRoute(it)) },
            )
        }
        composable<SitesRoute> {
            SitesScreen(onOpenSite = openSite, onTab = onTab, onAdd = { nav.navigate(AddRoute) })
        }
        composable<AddRoute> {
            AddScreen(
                onClose = { nav.popBackStack() },
                onOpenSite = openSearchResult,
                // The reader may have opened a site from Add while the subscribe ran; leave that open.
                onSubscribed = { if (nav.currentDestination?.hasRoute<AddRoute>() == true) nav.popBackStack() },
            )
        }
        composable<ArticleRoute> { entry ->
            val route = entry.toRoute<ArticleRoute>()
            ArticleScreen(
                postId = route.postId,
                list = route.list,
                onBack = { nav.popBackStack() },
                onOpenSite = openSite,
                onSignIn = signIn,
            )
        }
        composable<SiteRoute> { entry ->
            SiteScreen(
                siteId = entry.toRoute<SiteRoute>().siteId,
                onBack = { nav.popBackStack() },
                onOpenPost = openSitePost,
            )
        }
        composable<SitePreviewRoute> { entry ->
            SitePreviewScreen(
                target = entry.toRoute<SitePreviewRoute>().toResult(),
                onBack = { nav.popBackStack() },
                onOpenPost = openSitePost,
            )
        }
        composable<PostListRoute> { entry ->
            val list = entry.toRoute<PostListRoute>().list
            PostListScreen(
                list = list,
                onBack = { nav.popBackStack() },
                onOpenPost = { nav.navigate(ArticleRoute(it, list)) },
            )
        }
    }
}

private fun NavHostController.selectTab(tab: Tab) {
    val route: Any = when (tab) {
        Tab.ADD -> return navigate(AddRoute)
        Tab.HOME -> FeedRoute
        Tab.SITES -> SitesRoute
    }
    navigate(route) {
        popUpTo<FeedRoute>()
        launchSingleTop = true
    }
}

/** Starts WordPress.com OAuth in a Custom Tab; MainActivity receives the redirect. */
@Composable
fun rememberSignIn(): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            CustomTabsIntent.Builder().build().launchUrl(context, context.container.auth.authorizeUri())
        }
    }
}
