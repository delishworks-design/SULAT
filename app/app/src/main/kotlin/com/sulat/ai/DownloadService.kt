package com.sulat.ai

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.JobIntentService

class DownloadService : JobIntentService() {
    companion object {
        const val CHANNEL_ID = "sulat_downloads"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.sulat.ai.action.START_DOWNLOAD"
        const val ACTION_STOP = "com.sulat.ai.action.STOP_DOWNLOAD"
        const val EXTRA_MODEL_ID = "model_id"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_DISPLAY_NAME = "display_name"
        const val BROADCAST_PAUSE = "com.sulat.ai.broadcast.PAUSE_DOWNLOAD"
        const val BROADCAST_STOP = "com.sulat.ai.broadcast.STOP_DOWNLOAD"

        fun enqueueWork(context: Context, modelId: String, fileName: String, displayName: String) {
            enqueueWork(context, DownloadService::class.java, 1,
                Intent(context, DownloadService::class.java).apply {
                    putExtra(EXTRA_MODEL_ID, modelId)
                    putExtra(EXTRA_FILE_NAME, fileName)
                    putExtra(EXTRA_DISPLAY_NAME, displayName)
                }
            )
        }

        fun stopWork(context: Context) {
            enqueueWork(context, DownloadService::class.java, 2, Intent(context, DownloadService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onHandleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_START -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID) ?: return
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: return
                val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME) ?: modelId
                // Handle download start
            }
            ACTION_STOP -> {
                // Handle stop
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}