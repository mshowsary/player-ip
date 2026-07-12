package com.novaplay.tv.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.novaplay.tv.BuildConfig
import com.novaplay.tv.core.PortalEndpointPolicy
import com.novaplay.tv.core.PortalRequestGuard
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

/** Process-wide infrastructure: coroutine scope, JSON codec, HTTP stacks, and Room. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** SupervisorJob prevents one failed background task from cancelling the rest. */
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Shared lenient JSON codec for inconsistent provider payloads. */
    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** General client for provider streams and user-supplied playlist sources. */
    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    /**
     * Dedicated portal client bound to the configured authority. Redirects are
     * disabled so bearer tokens cannot move to another host or unsafe scheme.
     */
    @Provides
    @Singleton
    fun portalApi(client: OkHttpClient, json: Json): PortalApi {
        val configuredBase = BuildConfig.PORTAL_BASE_URL.trimEnd('/')
        val assessment = PortalEndpointPolicy.assess(configuredBase, BuildConfig.DEBUG)
        // Invalid configuration must not crash personal-playlist mode. Repository
        // calls fail with a readable configuration message before network I/O.
        val safeBase = if (assessment.transportAllowed) configuredBase else "https://portal.invalid"
        val portalClient = client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(PortalRequestGuard(safeBase, BuildConfig.DEBUG))
            .build()

        return Retrofit.Builder()
            .baseUrl(safeBase.trimEnd('/') + "/")
            .client(portalClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PortalApi::class.java)
    }

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

    /** WAL keeps catalogue reads responsive during sync; migrations are non-destructive. */
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): NovaDatabase =
        Room.databaseBuilder(context, NovaDatabase::class.java, "novaplay.db")
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(DatabaseMigrations.MIGRATION_2_3, DatabaseMigrations.MIGRATION_3_4)
            .build()
}
