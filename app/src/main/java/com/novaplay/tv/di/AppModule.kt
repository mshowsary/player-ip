package com.novaplay.tv.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.novaplay.tv.BuildConfig
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

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

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

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): NovaDatabase =
        Room.databaseBuilder(context, NovaDatabase::class.java, "novaplay.db")
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .fallbackToDestructiveMigration()
            .build()
}
