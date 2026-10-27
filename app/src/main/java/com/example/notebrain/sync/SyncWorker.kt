package com.example.notebrain.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.notebrain.repository.SyncRepository
import com.example.notebrain.utils.SyncStatus

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val syncRepository = SyncRepository(context)

    override suspend fun doWork(): Result {
        val result = syncRepository.performSync()
        return if (result.isSuccess) {
            val status = result.getOrNull()
            if (status == SyncStatus.SYNCED || status == SyncStatus.NOT_CONFIGURED) {
                Result.success()
            } else {
                Result.retry()
            }
        } else {
            Result.retry()
        }
    }
}
