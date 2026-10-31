package com.nbradbury.wpfolio.nav

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.nbradbury.wpfolio.container
import com.nbradbury.wpfolio.data.PostList
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

    NavHost(
        navController = nav,
        startDestination = startDestination,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onSignIn = signIn,
                onStartWithRss = { nav.navigate(AddRoute(fromOnboarding = true)) },
                onSignedIn = { nav.navigate(FeedRoute) { popUpTo<OnboardingRoute> { inclusive = true } } },
            )
        }
        composable<FeedRoute> {
            FeedScreen(
                onOpenPost = openPost,
                onTab = onTab,
                onSignIn = signIn,
                onOpenList = { nav.navigate(PostListRoute(it)) },
                // Back to the first-run screen with nothing behind it. If on-device RSS
                // subscriptions remain, onboarding forwards straight back to the feed.
                onSignedOut = {
                    nav.navigate(OnboardingRoute) { popUpTo(nav.graph.id) { inclusive = true } }
                },
            )
        }
        composable<SitesRoute> {
            SitesScreen(onOpenSite = openSite, onTab = onTab, onAdd = { nav.navigate(AddRoute()) })
        }
        composable<AddRoute> { entry ->
            val fromOnboarding = entry.toRoute<AddRoute>().fromOnboarding
            AddScreen(
                onClose = { nav.popBackStack() },
                onOpenSite = { result ->
                    val subscribedId = result.subscribedSiteId
                    // Sites already followed open as usual; others open as a preview.
                    nav.navigate(if (subscribedId != null) SiteRoute(subscribedId) else result.toPreviewRoute())
                },
                onSubscribed = {
                    if (fromOnboarding) {
                        nav.navigate(FeedRoute) { popUpTo<OnboardingRoute> { inclusive = true } }
                    } else {
                        nav.popBackStack()
                    }
                },
            )
        }
        composable<ArticleRoute> { entry ->
            val route = entry.toRoute<ArticleRoute>()
            ArticleScreen(
                postId = route.postId,
                list = route.list,
                onBack = { nav.popBackStack() },
                onOpenSite = openSite,
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
    when (tab) {
        Tab.ADD -> navigate(AddRoute())
        Tab.HOME, Tab.SITES -> navigate(if (tab == Tab.HOME) FeedRoute else SitesRoute) {
            popUpTo<FeedRoute>()
            launchSingleTop = true
        }
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
