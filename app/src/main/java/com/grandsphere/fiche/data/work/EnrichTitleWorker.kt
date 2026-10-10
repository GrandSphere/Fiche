package com.grandsphere.fiche.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.grandsphere.fiche.data.repository.LibraryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class EnrichTitleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val library: LibraryRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val titleId = inputData.getLong(KEY_TITLE_ID, -1L)
        if (titleId < 0L) return Result.failure()
        return runCatching {
            library.enrichTitle(titleId)
            Result.success()
        }.getOrElse {
            // Leave busyLoading=1 so Storage "Reload incomplete" can retry.
            Result.retry()
        }
    }

    companion object {
        const val KEY_TITLE_ID = "titleId"
        const val UNIQUE_PREFIX = "enrich-title-"

        fun uniqueName(titleId: Long) = "$UNIQUE_PREFIX$titleId"
    }
}
