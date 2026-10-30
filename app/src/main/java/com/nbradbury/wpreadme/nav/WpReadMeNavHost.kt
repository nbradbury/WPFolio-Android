package com.nbradbury.wpreadme.nav

import android.widget.Toast
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
import com.nbradbury.wpreadme.container
import com.nbradbury.wpreadme.ui.add.AddScreen
import com.nbradbury.wpreadme.ui.article.ArticleScreen
import com.nbradbury.wpreadme.ui.components.Tab
import com.nbradbury.wpreadme.ui.feed.FeedScreen
import com.nbradbury.wpreadme.ui.onboarding.OnboardingScreen
import com.nbradbury.wpreadme.ui.saved.SavedScreen
import com.nbradbury.wpreadme.ui.site.SiteScreen
import com.nbradbury.wpreadme.ui.sites.SitesScreen
import kotlinx.coroutines.launch

@Composable
fun WpReadMeNavHost(startDestination: Any) {
    val nav = rememberNavController()
    val signIn = rememberSignIn()
    val onTab: (Tab) -> Unit = { nav.selectTab(it) }
    val openPost: (String) -> Unit = { nav.navigate(ArticleRoute(it)) }
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
                onOpenSaved = { nav.navigate(SavedRoute) },
            )
        }
        composable<SitesRoute> {
            SitesScreen(onOpenSite = openSite, onTab = onTab, onAdd = { nav.navigate(AddRoute()) })
        }
        composable<AddRoute> { entry ->
            val fromOnboarding = entry.toRoute<AddRoute>().fromOnboarding
            AddScreen(
                onClose = { nav.popBackStack() },
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
            ArticleScreen(
                postId = entry.toRoute<ArticleRoute>().postId,
                onBack = { nav.popBackStack() },
                onOpenSite = openSite,
            )
        }
        composable<SiteRoute> { entry ->
            SiteScreen(
                siteId = entry.toRoute<SiteRoute>().siteId,
                onBack = { nav.popBackStack() },
                onOpenPost = openPost,
            )
        }
        composable<SavedRoute> {
            SavedScreen(onBack = { nav.popBackStack() }, onOpenPost = openPost)
        }
    }
}

private fun NavHostController.selectTab(tab: Tab) {
    when (tab) {
        Tab.HOME -> navigate(FeedRoute) {
            popUpTo<FeedRoute>()
            launchSingleTop = true
        }
        Tab.SITES -> navigate(SitesRoute) {
            popUpTo<FeedRoute>()
            launchSingleTop = true
        }
        Tab.ADD -> navigate(AddRoute())
    }
}

/** Starts WordPress.com OAuth in a Custom Tab; MainActivity receives the redirect. */
@Composable
fun rememberSignIn(): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return {
        val auth = context.container.auth
        if (!auth.isConfigured) {
            Toast.makeText(context, "Add WPCOM_CLIENT_ID to local.properties to enable sign-in", Toast.LENGTH_LONG).show()
        } else {
            scope.launch {
                CustomTabsIntent.Builder().build().launchUrl(context, auth.authorizeUri())
            }
        }
    }
}
