package com.grandsphere.fiche.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.grandsphere.fiche.data.repository.LibraryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class ReloadCatalogWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val library: LibraryRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val summary = library.reloadFromSelectedCatalogs { progress ->
                setProgress(workDataOf(KEY_CURRENT to progress.current, KEY_TOTAL to progress.total))
            }
            CatalogReloadLog.write(applicationContext, summary.logText)
            if (isStopped) {
                Result.success()
            } else {
                val updated = summary.replaced + summary.refreshed
                val message = "Updated $updated, skipped ${summary.skipped}"
                Result.success(workDataOf(KEY_MESSAGE to message))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_MESSAGE to (e.message ?: "Could not update catalogue")))
        }
    }

    companion object {
        const val KEY_MESSAGE = "message"
        const val KEY_CURRENT = "current"
        const val KEY_TOTAL = "total"
        const val UNIQUE_NAME = "reload-catalog"
    }
}
