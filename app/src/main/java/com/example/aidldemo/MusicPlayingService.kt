package com.example.aidldemo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
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

    override fun onCreate() {
        super.onCreate()
        launch {
            // Seconds Change
            playingTask.stateChannel.asFlow()
                .distinctUntilChangedBy { it.playingSecond }
                .collect { state ->
                    val callback: List<MusicPlayingCallback> = progressCallbacks
                    callback.forEach { it.musicPlayingSeconds(state.playingSecond) }
                }
        }

        launch {
            // Playing State Change
            playingTask.stateChannel.asFlow()
                .distinctUntilChangedBy { it.playingState }
                .collect { state ->
                    val callback: List<MusicPlayingCallback> = progressCallbacks
                    callback.forEach { it.currentPlayingState(state.playingState.ordinal) }
                }
        }

        launch {
            // Playing Music Change
            playingTask.stateChannel.asFlow()
                .distinctUntilChangedBy { it.playingMusic }
                .collect { state ->
                    val callback: List<MusicPlayingCallback> = progressCallbacks
                    callback.forEach { it.musicPlaying(state.playingMusic) }
                }
        }


        val notificationManager = getSystemService<NotificationManager>()
        if (notificationManager != null) {
            val notificationID = Random(System.currentTimeMillis()).nextInt(1, 1000)

            val notificationIntent = PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT)

            val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!notificationManager.notificationChannels.any { it.id == DEFAULT_NOTIFICATION_CHANNEL_ID }) {
                    notificationManager.createNotificationChannel(NotificationChannel(DEFAULT_NOTIFICATION_CHANNEL_ID, DEFAULT_NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT))
                }
                NotificationCompat.Builder(this, DEFAULT_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Hello, World!!")
                    .setContentText("Hello, World!!")
                    .setContentIntent(notificationIntent)
                    .build()
            } else {
                NotificationCompat.Builder(this, DEFAULT_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Hello, World!!")
                    .setContentText("Hello, World!!")
                    .setContentIntent(notificationIntent)
                    .build()
            }
            startForeground(notificationID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }

    companion object {
        const val DEFAULT_NOTIFICATION_CHANNEL_ID = "music_playing_service_channel_id"
        const val DEFAULT_NOTIFICATION_CHANNEL_NAME = "music_playing_service_channel_name"
    }

}

@FlowPreview
@ExperimentalCoroutinesApi
class MusicPlayingTask(coroutineScope: CoroutineScope): CoroutineScope by coroutineScope {

    data class State(
        val playingMusic: PlayingMusicModel?,
        val playingState: PlayingState,
        val playingSecond: Int
    )

    enum class PlayingState {
        Running,
        Pause,
        Stop
    }

    val stateChannel = BroadcastChannel<State>(Channel.CONFLATED)

    init {

        // Init Params.
        launch {
            stateChannel.send(
                State(
                    playingMusic = null,
                    playingState = PlayingState.Stop,
                    playingSecond = 0
                )
            )
        }

        // Running
        launch {
            stateChannel.asFlow()
                .distinctUntilChanged()
                .filter { it.playingState == PlayingState.Running }
                .collect {
                    // pretend playing music.
                    delay(1000)
                    val latestState = stateChannel.asFlow().first()
                    if (latestState.playingMusic != null) {
                        if (latestState.playingState == PlayingState.Running) {
                            if (latestState.playingSecond + 1 == latestState.playingMusic.length) {
                                stateChannel.send(
                                    latestState.copy(
                                        playingSecond = 0,
                                        playingState = PlayingState.Stop
                                    )
                                )
                            } else {
                                stateChannel.send(latestState.copy(playingSecond = latestState.playingSecond + 1))
                            }
                        }
                    }
                }
        }

    }

    @FlowPreview
    fun startTask(newMusic: PlayingMusicModel) {
        launch {
            stateChannel.send(
                State(
                    playingMusic = newMusic,
                    playingState = PlayingState.Running,
                    playingSecond = 0
                )
            )
        }
    }

    @FlowPreview
    fun getCurrentPlayingState(): PlayingState? = runBlocking(coroutineContext) { stateChannel.asFlow().firstOrNull()?.playingState }

    @FlowPreview
    fun getCurrentPlayingSeconds(): Int? = runBlocking(coroutineContext) { stateChannel.asFlow().firstOrNull()?.playingSecond }

    @FlowPreview
    fun getCurrentPlayingMusic(): PlayingMusicModel? = runBlocking(coroutineContext) { stateChannel.asFlow().firstOrNull()?.playingMusic }

    @FlowPreview
    fun updatePlayingState(playingState: PlayingState) = runBlocking(coroutineContext) {
        val oldState = stateChannel.asFlow().first()
        if (playingState == PlayingState.Stop) {
            stateChannel.send(oldState.copy(playingState = playingState, playingSecond = 0))
        } else {
            stateChannel.send(oldState.copy(playingState = playingState))
        }
    }


}