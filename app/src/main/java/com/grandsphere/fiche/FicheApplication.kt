package com.grandsphere.fiche

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.grandsphere.fiche.data.prefs.ApiKeyRepository
import com.grandsphere.fiche.data.repository.LibraryRepository
import com.grandsphere.fiche.data.work.ReloadCatalogWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class FicheApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var apiKeys: ApiKeyRepository

    @Inject
    lateinit var library: LibraryRepository

    @Inject
    lateinit var http: OkHttpClient

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        WorkManager.getInstance(this).cancelUniqueWork(ReloadCatalogWorker.UNIQUE_NAME)
        appScope.launch {
            runCatching { apiKeys.ensureMigrated() }
            runCatching { library.libraryPrefs() }
            if (BuildConfig.DEBUG) {
                runCatching { library.seedStaleCatalogCheckExamples() }
            }
        }
    }

    fun shutdown() {
        WorkManager.getInstance(this).cancelAllWork()
        http.dispatcher.cancelAll()
        appScope.cancel()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
