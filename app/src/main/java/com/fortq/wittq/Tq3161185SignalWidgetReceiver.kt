package com.fortq.wittq

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class Tq3161185SignalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = Tq3161185SignalWidget()

    // 위젯이 처음 배치될 때 한 번만 Worker 등록
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Tq3161185SignalWorker.enqueue(context)
    }
}
