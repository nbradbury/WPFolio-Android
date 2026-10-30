package com.nbradbury.wpfolio

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nbradbury.wpfolio.nav.FeedRoute
import com.nbradbury.wpfolio.nav.OnboardingRoute
import com.nbradbury.wpfolio.nav.WpFolioNavHost
import com.nbradbury.wpfolio.ui.CustomTabUriHandler
import com.nbradbury.wpfolio.ui.WebOnlyUriHandler
import com.nbradbury.wpfolio.ui.theme.WpFolioTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        viewModelFactory { initializer { MainViewModel(container) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { viewModel.start.value == null }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Content runs under the 3-button nav bar too; the floating pill handles its own contrast.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        if (savedInstanceState == null) handleOAuthRedirect(intent)

        setContent {
            val start by viewModel.start.collectAsStateWithLifecycle()
            WpFolioTheme {
                // Links open in a Custom Tab, and only web links open at all.
                val webOnly = remember { WebOnlyUriHandler(CustomTabUriHandler(this@MainActivity)) }
                CompositionLocalProvider(LocalUriHandler provides webOnly) {
                    start?.let { WpFolioNavHost(startDestination = it) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthRedirect(intent)
    }

    private fun handleOAuthRedirect(intent: Intent?) {
        val uri = intent?.data
        if (!container.auth.isRedirect(uri)) return
        lifecycleScope.launch {
            try {
                container.repository.signIn(container.auth.tokenFrom(uri!!))
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, e.message ?: "Couldn't sign in", Toast.LENGTH_LONG).show()
            }
        }
    }
}

class MainViewModel(container: AppContainer) : ViewModel() {
    /** Onboarding until the user has an account or at least one subscription; null while loading. */
    val start = MutableStateFlow<Any?>(null)

    init {
        viewModelScope.launch {
            val repo = container.repository
            repo.pruneOrphanedPosts()
            val hasContent = combine(repo.account, repo.siteCount) { account, count -> account != null || count > 0 }
                .first()
            start.value = if (hasContent) FeedRoute else OnboardingRoute
        }
    }
}
