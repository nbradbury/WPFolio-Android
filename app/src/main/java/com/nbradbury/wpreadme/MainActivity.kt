package com.nbradbury.wpreadme

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nbradbury.wpreadme.data.prefs.ReaderSettings
import com.nbradbury.wpreadme.nav.FeedRoute
import com.nbradbury.wpreadme.nav.WpReadMeNavHost
import com.nbradbury.wpreadme.nav.OnboardingRoute
import com.nbradbury.wpreadme.ui.theme.WpReadMeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        viewModelFactory { initializer { MainViewModel(container) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition {
            viewModel.start.value == null || viewModel.settings.value == null
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Content runs under the 3-button nav bar too; the floating pill handles its own contrast.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        if (savedInstanceState == null) handleOAuthRedirect(intent)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val start by viewModel.start.collectAsStateWithLifecycle()
            val theme = settings?.theme ?: return@setContent
            WpReadMeTheme(theme) {
                start?.let { WpReadMeNavHost(startDestination = it) }
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
                val token = container.auth.exchange(uri!!)
                container.repository.signIn(token)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, e.message ?: "Couldn't sign in", Toast.LENGTH_LONG).show()
            }
        }
    }
}

class MainViewModel(container: AppContainer) : ViewModel() {
    /** Null until loaded, so the first frame uses the right theme. */
    val settings: StateFlow<ReaderSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Onboarding until the user has an account or at least one subscription; null while loading. */
    val start = MutableStateFlow<Any?>(null)

    init {
        viewModelScope.launch {
            val repo = container.repository
            val hasContent = combine(repo.account, repo.siteCount) { account, count -> account != null || count > 0 }
                .first()
            start.value = if (hasContent) FeedRoute else OnboardingRoute
        }
    }
}
