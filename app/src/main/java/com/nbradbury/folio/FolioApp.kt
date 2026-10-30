package com.nbradbury.folio

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.nbradbury.folio.data.AccentSampler
import com.nbradbury.folio.data.ReaderRepository
import com.nbradbury.folio.data.db.FolioDatabase
import com.nbradbury.folio.data.prefs.SettingsStore
import com.nbradbury.folio.data.rss.OkHttpRssSource
import com.nbradbury.folio.data.wpcom.RetrofitWpcomSource
import com.nbradbury.folio.data.wpcom.WpcomApi
import com.nbradbury.folio.data.wpcom.WpcomAuth
import com.nbradbury.folio.data.wpcom.WpcomAuthInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class FolioApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = container.imageLoader
}

val Context.container: AppContainer get() = (applicationContext as FolioApp).container

class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob())
    val settings = SettingsStore(context)
    val database = FolioDatabase.create(context)

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    val imageLoader: ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
        .crossfade(true)
        .build()

    private val wpcomApi: WpcomApi = Retrofit.Builder()
        .baseUrl(WpcomApi.BASE_URL)
        .client(http.newBuilder().addInterceptor(WpcomAuthInterceptor(settings)).build())
        .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(WpcomApi::class.java)

    val auth = WpcomAuth(http, settings)

    val accentSampler = AccentSampler(context, imageLoader, database.postDao(), appScope)

    val repository = ReaderRepository(
        db = database,
        settings = settings,
        rss = OkHttpRssSource(http),
        wpcom = RetrofitWpcomSource(wpcomApi),
        onPostsChanged = accentSampler::requestSampling,
    )

    private companion object {
        const val USER_AGENT = "Folio/${BuildConfig.VERSION_NAME} (Android)"
    }
}
