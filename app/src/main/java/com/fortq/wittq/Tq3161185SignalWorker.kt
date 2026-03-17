package com.fortq.wittq

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.*
import java.util.concurrent.TimeUnit

class Tq3161185SignalWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Tq3161185SignalWidget().updateAll(context)
            Result.success()
        } catch (e: Exception) {
            Log.e("WITTQ_WORKER", "QqqqStrategy update failed: ${e.message}", e)
            Result.success() // 무한 루프 방지 - 실패해도 success 반환
        }
    }

    companion object {
        private const val WORK_NAME = "tq_3161185_signal_update_work"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // 즉시 1회 실행 (최초 배치 시 바로 데이터 로드)
            val oneTimeRequest = OneTimeWorkRequestBuilder<Tq3161185SignalWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueue(oneTimeRequest)

            val request = PeriodicWorkRequestBuilder<Tq3161185SignalWorker>(
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
