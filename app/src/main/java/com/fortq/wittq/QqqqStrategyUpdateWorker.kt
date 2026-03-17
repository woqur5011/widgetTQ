package com.fortq.wittq

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.*
import java.util.concurrent.TimeUnit

class QqqqStrategyUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            QqqqStrategyWidget().updateAll(context)
            Result.success()
        } catch (e: Exception) {
            Log.e("WITTQ_WORKER", "QqqqStrategy update failed: ${e.message}", e)
            Result.success() // 무한 루프 방지 - 실패해도 success 반환
        }
    }

    companion object {
        private const val WORK_NAME = "qqq_strategy_update_work"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<QqqqStrategyUpdateWorker>(
                60, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
