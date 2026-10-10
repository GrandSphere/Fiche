package com.grandsphere.fiche.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.grandsphere.fiche.data.backup.BackupRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class ImportBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backup: BackupRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val path = inputData.getString(KEY_PATH)
        if (path.isNullOrBlank()) {
            return Result.failure(workDataOf(KEY_MESSAGE to "Import failed"))
        }
        return runCatching {
            val message = backup.importFromFile(File(path)) { current, total ->
                setProgress(workDataOf(KEY_CURRENT to current, KEY_TOTAL to total))
            }
            Result.success(workDataOf(KEY_MESSAGE to message))
        }.getOrElse {
            Result.failure(workDataOf(KEY_MESSAGE to (it.message ?: "Import failed")))
        }
    }

    companion object {
        const val KEY_PATH = "path"
        const val KEY_MESSAGE = "message"
        const val KEY_CURRENT = "current"
        const val KEY_TOTAL = "total"
        const val UNIQUE_NAME = "import-backup"
    }
}
