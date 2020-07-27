package com.example.aidldemo

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

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

        override fun setProgressCallback(callback: MusicPlayingCallback?) {
            if (callback != null) { progressCallbacks.add(callback) }
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onDestroy() {
        super.onDestroy()
        cancel()
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
                    if (latestState.playingState == PlayingState.Running) {
                        if (latestState.playingSecond + 1 == latestState.playingMusic?.length) {
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
    fun getCurrentPlayingState(): PlayingState = runBlocking(coroutineContext) { stateChannel.asFlow().first().playingState }

    @FlowPreview
    fun getCurrentPlayingSeconds(): Int = runBlocking(coroutineContext) { stateChannel.asFlow().first().playingSecond }

    @FlowPreview
    fun getCurrentPlayingMusic(): PlayingMusicModel? = runBlocking(coroutineContext) { stateChannel.asFlow().first().playingMusic }

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