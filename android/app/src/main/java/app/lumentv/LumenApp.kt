package app.lumentv

import android.app.Application
import app.lumentv.data.Prefs
import app.lumentv.data.Repository
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class LumenApp : Application(), SingletonImageLoader.Factory {
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followSslRedirects(true)
            .cache(Cache(File(cacheDir, "http"), 64L * 1024 * 1024))
            .addInterceptor { chain ->
                val req = chain.request()
                chain.proceed(
                    if (req.header("User-Agent") == null) req.newBuilder().header("User-Agent", Repository.USER_AGENT).build() else req,
                )
            }
            .build()
    }
    val repository by lazy { Repository(this, http) }
    val prefs by lazy { Prefs(this) }

    /** App-wide scope for the player (it outlives screens when playing in the background). */
    val appScope = kotlinx.coroutines.MainScope()

    /**
     * The player lives in the app, not a screen, so the background playback service and
     * the UI share it. Created on first use with the saved decoder/buffer settings.
     */
    @get:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    val playerController by lazy {
        val initial = kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeoutOrNull(1_500) { prefs.app.first() }
        } ?: app.lumentv.data.AppPrefs()
        app.lumentv.ui.player.PlayerController(this, http, appScope, initial.buffer, initial.decoder) { ch, ok ->
            appScope.launch { prefs.setHealth(ch.id, ok) }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .diskCache { DiskCache.Builder().directory(File(cacheDir, "logos")).maxSizeBytes(80L * 1024 * 1024).build() }
            .crossfade(true)
            .build()
}
