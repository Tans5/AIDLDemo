package com.example.aidldemo

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random

@FlowPreview
@ExperimentalCoroutinesApi
class MusicPlayingService : Service(), CoroutineScope by CoroutineScope(Dispatchers.Main) {

    val progressCallbacks: MutableList<MusicPlayingCallback> = mutableListOf()

    val playingTask: MusicPlayingTask by lazy { MusicPlayingTask(this) }

    val binder: IBinder = object : IPlayingMusicService.Stub() {

        override fun pause() { playingTask.updatePlayingState(MusicPlayingTask.PlayingState.Pause) }

        override fun newPlayingMusicModel(newMusic: PlayingMusicModel) {
            playingTask.startTask(newMusic)
        }

        override fun start() { playingTask.updatePlayingState(MusicPlayingTask.PlayingState.Running) }

        override fun stop() { playingTask.updatePlayingState(MusicPlayingTask.PlayingState.Stop) }

        override fun getPlayingMusicModel(): PlayingMusicModel? = playingTask.getCurrentPlayingMusic()

        override fun addProgressCallback(callback: MusicPlayingCallback?) {
            if (callback != null) {
                progressCallbacks.add(callback)
                launch(Dispatchers.IO) {
                    val playingMusic = playingTask.getCurrentPlayingMusic()
                    val playingSeconds = playingTask.getCurrentPlayingSeconds()
                    val playingState = playingTask.getCurrentPlayingState()
                    callback.musicPlaying(playingMusic)
                    if (playingSeconds != null) {
                        callback.musicPlayingSeconds(playingSeconds)
                    }
                    if (playingState != null) {
                        callback.currentPlayingState(playingState.ordinal)
                    }
                }
            }
        }

        override fun removeProgressCallback(callbackId: Long) {
            val iterator = progressCallbacks.iterator()
            while (iterator.hasNext()) {
                val callback = iterator.next()
                if (callback.callbackId() == callbackId) {
                    iterator.remove()
                    break
                }
            }
        }

    }

    val broadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PAUSE_OR_START_BROADCAST_ACTION) {
                launch(Dispatchers.IO) {
                    val state = playingTask.stateChannel.asFlow().firstOrNull()
                    if (state?.playingMusic != null) {
                        when (state.playingState) {
                            MusicPlayingTask.PlayingState.Running -> {
                                playingTask.updatePlayingState(MusicPlayingTask.PlayingState.Pause)
                            }
                            MusicPlayingTask.PlayingState.Pause -> {
                                playingTask.updatePlayingState(MusicPlayingTask.PlayingState.Running)
                            }
                            MusicPlayingTask.PlayingState.Stop -> {
                                playingTask.updatePlayingState(MusicPlayingTask.PlayingState.Pause)
                            }
                        }
                    }
                }
            }
        }

    }
    val notificationManager: NotificationManager by lazy { getSystemService<NotificationManager>()!! }
    val notificationID: Int by lazy { Random(System.currentTimeMillis()).nextInt(1, 1000) }
    val remoteSmallViews by lazy { RemoteViews(this.packageName, R.layout.playing_music_notification_small_layout) }
    val remoteExpandViews by lazy {
        RemoteViews(this.packageName, R.layout.playing_music_notification_expanded_layout).apply {
            setOnClickPendingIntent(R.id.control_iv, PendingIntent.getBroadcast(this@MusicPlayingService, 0, Intent(
                PAUSE_OR_START_BROADCAST_ACTION), PendingIntent.FLAG_UPDATE_CURRENT))
        }
    }
    val notification by lazy { createNotificationBuilder(remoteSmallViews, remoteExpandViews).build() }
    val notificationBuilder by lazy { createNotificationBuilder(remoteSmallViews, remoteExpandViews) }

    override fun onCreate() {
        super.onCreate()

        registerReceiver(broadcastReceiver, IntentFilter(PAUSE_OR_START_BROADCAST_ACTION))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            startForeground(notificationID, notification)
        } else {
            startForeground(notificationID, notificationBuilder.build())
        }
        launch {
            // Seconds Change
            playingTask.stateChannel.asFlow()
                .filter { it.playingMusic != null }
                .distinctUntilChangedBy { it.playingSecond }
                .collect { state ->
                    remoteExpandViews.setProgressBar(R.id.song_playing_pb, state.playingMusic!!.length, state.playingSecond, false)
                    remoteExpandViews.setViewVisibility(R.id.song_playing_pb, View.VISIBLE)
                    remoteExpandViews.setTextViewText(R.id.playing_time_tv, "${(state.playingSecond / 60).toString()
                        .padStart(2, '0')}:${(state.playingSecond % 60).toString().padStart(2, '0')}")
                    remoteExpandViews.setTextViewText(R.id.song_duration_tv, "${(state.playingMusic.length / 60).toString()
                        .padStart(2, '0')}:${(state.playingMusic.length % 60).toString().padStart(2, '0')}")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        notificationManager.notify(notificationID, notification)
                    } else {
                        notificationManager.notify(notificationID, notificationBuilder.build())
                    }
                    val callback: List<MusicPlayingCallback> = progressCallbacks
                    callback.forEach { it.musicPlayingSeconds(state.playingSecond) }
                }
        }

        launch {
            // Playing State Change
            playingTask.stateChannel.asFlow()
                .filter { it.playingMusic != null }
                .distinctUntilChangedBy { it.playingState }
                .collect { state ->
                    remoteExpandViews.setImageViewResource(R.id.control_iv,
                        if (state.playingState == MusicPlayingTask.PlayingState.Running) R.drawable.pause else R.drawable.play)
                    remoteExpandViews.setViewVisibility(R.id.control_iv, View.VISIBLE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        notificationManager.notify(notificationID, notification)
                    } else {
                        notificationManager.notify(notificationID, notificationBuilder.build())
                    }
                    val callback: List<MusicPlayingCallback> = progressCallbacks
                    callback.forEach { it.currentPlayingState(state.playingState.ordinal) }
                }
        }

        launch {
            // Playing Music Change
            playingTask.stateChannel.asFlow()
                .filter { it.playingMusic != null }
                .distinctUntilChangedBy { it.playingMusic }
                .collect { state ->
                    remoteExpandViews.setTextViewText(R.id.song_name_tv, state.playingMusic?.musicName ?: "")
                    remoteExpandViews.setTextViewText(R.id.author_name_tv, state.playingMusic?.artist ?: "")
                    remoteSmallViews.setTextViewText(R.id.song_name_tv, state.playingMusic?.musicName ?: "")
                    remoteSmallViews.setTextViewText(R.id.author_name_tv, state.playingMusic?.artist ?: "")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        notificationManager.notify(notificationID, notification)
                    } else {
                        notificationManager.notify(notificationID, notificationBuilder.setCustomBigContentView(remoteExpandViews).build())
                    }
                    val callback: List<MusicPlayingCallback> = progressCallbacks
                    callback.forEach { it.musicPlaying(state.playingMusic) }
                }
        }

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
        cancel()
    }

    fun createNotificationBuilder(remoteSmallViews: RemoteViews, remoteExpandedViews: RemoteViews)
            : NotificationCompat.Builder {
        val notificationIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!notificationManager.notificationChannels.any { it.id == DEFAULT_NOTIFICATION_CHANNEL_ID }) {
                notificationManager.createNotificationChannel(
                    NotificationChannel(
                        DEFAULT_NOTIFICATION_CHANNEL_ID,
                        DEFAULT_NOTIFICATION_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            }
            NotificationCompat.Builder(this, DEFAULT_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.play)
                .setContentIntent(notificationIntent)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(remoteSmallViews)
                .setCustomBigContentView(remoteExpandedViews)
                .setNotificationSilent()
        } else {
            NotificationCompat.Builder(this, DEFAULT_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.play)
                .setContentIntent(notificationIntent)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(remoteSmallViews)
                .setCustomBigContentView(remoteExpandedViews)
                .setNotificationSilent()
        }
    }

    companion object {
        const val DEFAULT_NOTIFICATION_CHANNEL_ID = "music_playing_service_channel_id"
        const val DEFAULT_NOTIFICATION_CHANNEL_NAME = "music_playing_service_channel_name"
        const val PAUSE_OR_START_BROADCAST_ACTION = "pause_or_start_broadcast_action"
    }

}

