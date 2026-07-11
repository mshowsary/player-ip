package com.novaplay.tv.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.novaplay.tv.BuildConfig
import com.novaplay.tv.data.db.DatabaseMigrations
import com.novaplay.tv.data.db.NovaDatabase
import com.novaplay.tv.data.remote.PortalApi
import com.novaplay.tv.data.remote.XtreamRawApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

// Scope that outlives any screen: background sync must survive navigation.
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Process-wide infrastructure: coroutine scope, JSON codec, HTTP stack, and Room. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provides the process-lifetime scope. SupervisorJob keeps one failed child (e.g. a
     * sync) from cancelling unrelated background work; Default suits CPU-bound parsing.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Provides the shared Json codec, configured to survive dirty Xtream panel output:
     * unknown keys, quoted numbers, and wrong-typed values must not fail a whole response.
     */
    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Provides the single OkHttp client shared by both Retrofit APIs (one connection
     * pool). The 45s read timeout accommodates slow panels serving large catalogues.
     */
    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    /**
     * Provides the first-party portal API at the build-time base URL; the trailing-slash
     * normalization keeps Retrofit's relative-path resolution correct for any config value.
     */
    @Provides
    @Singleton
    fun portalApi(client: OkHttpClient, json: Json): PortalApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.PORTAL_BASE_URL.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PortalApi::class.java)

    // Xtream servers are per-playlist; every call passes a full @Url, so the
    // base URL here is a never-used placeholder.
    @Provides
    @Singleton
    fun xtreamRawApi(client: OkHttpClient): XtreamRawApi =
        Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .build()
            .create(XtreamRawApi::class.java)

    /**
     * Provides the Room database. WAL lets catalogue writes proceed without blocking
     * readers during sync; migrations are explicit — no destructive fallback, so a
     * missing migration fails fast instead of silently wiping user data.
     */
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): NovaDatabase =
        Room.databaseBuilder(context, NovaDatabase::class.java, "novaplay.db")
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(DatabaseMigrations.MIGRATION_2_3)
            .build()
}
