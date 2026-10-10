package com.grandsphere.fiche.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.grandsphere.fiche.data.repository.LibraryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RecalcProgressWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val library: LibraryRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            library.persistAllOverviews { progress ->
                setProgress(workDataOf(KEY_CURRENT to progress.current, KEY_TOTAL to progress.total))
            }
            Result.success(workDataOf(KEY_MESSAGE to "Progress recalculated"))
        }.getOrElse {
            Result.failure(workDataOf(KEY_MESSAGE to (it.message ?: "Could not recalculate")))
        }
    }

    companion object {
        const val KEY_MESSAGE = "message"
        const val KEY_CURRENT = "current"
        const val KEY_TOTAL = "total"
        const val UNIQUE_NAME = "recalc-progress"
    }
}
