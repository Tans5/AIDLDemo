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

        override fun start() { playingTask.updatePlayingState(MusicPlayingTask.PlayingState.Pause) }

        override fun stop() { playingTask.updatePlayingState(MusicPlayingTask.PlayingState.Stop) }

        override fun getPlayingMusicModel(): PlayingMusicModel? = playingTask.getCurrentPlayingMusic()

        override fun setProgressCallback(callback: MusicPlayingCallback?) {
            if (callback != null) { progressCallbacks.add(callback) }
        }

    }

    override fun onCreate() {
        super.onCreate()
        launch {
            playingTask.stateChannel.asFlow()
                .distinctUntilChanged()
                .collect { state ->
                    val callback: List<MusicPlayingCallback> = progressCallbacks
                    callback.forEach {
                        it.musicPlayingSeconds(state.playingSecond)
                        it.musicPlaying(state.playingMusic)
                    }
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

@ExperimentalCoroutinesApi
class MusicPlayingTask(val coroutineScope: CoroutineScope) {

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

    val stateChannel = BroadcastChannel<State>(100)

    init {
        coroutineScope.launch {
            stateChannel.send(
                State(
                    playingMusic = null,
                    playingState = PlayingState.Stop,
                    playingSecond = 0
                )
            )
        }
    }

    val jobs: MutableList<Job> = mutableListOf()

    @FlowPreview
    fun startTask(newMusic: PlayingMusicModel) {
        jobs.forEach { it.cancel() }
        jobs.clear()

        coroutineScope.launch {
            stateChannel.send(
                State(
                    playingMusic = newMusic,
                    playingState = PlayingState.Running,
                    playingSecond = 0
                )
            )
        }.let { jobs.add(it) }

        // Running
        coroutineScope.launch {
            stateChannel.asFlow()
                .distinctUntilChanged()
                .filter { it.playingMusic != null && it.playingState == PlayingState.Running }
                .collect { oldState ->
                    // pretend playing music.
                    delay(1000)
                    launch {
                        if (oldState.playingSecond + 1 == oldState.playingMusic?.length) {
                            stateChannel.send(oldState.copy(playingSecond = 0, playingState = PlayingState.Stop))
                        } else {
                            stateChannel.send(oldState.copy(playingSecond = oldState.playingSecond + 1))
                        }
                    }
                }
        }.let { jobs.add(it) }

        // Stop
        coroutineScope.launch {
            stateChannel.asFlow()
                .distinctUntilChangedBy { it.playingState }
                .filter { it.playingState == PlayingState.Stop }
                .collect { oldState ->
                    launch {
                        stateChannel.send(oldState.copy(playingSecond = 0))
                    }
                }

        }.let { jobs.add(it) }
    }

    @FlowPreview
    fun getCurrentPlayingState(): PlayingState = runBlocking(coroutineScope.coroutineContext) { stateChannel.asFlow().first().playingState }

    @FlowPreview
    fun getCurrentPlayingSeconds(): Int = runBlocking(coroutineScope.coroutineContext) { stateChannel.asFlow().first().playingSecond }

    @FlowPreview
    fun getCurrentPlayingMusic(): PlayingMusicModel? = runBlocking(coroutineScope.coroutineContext) { stateChannel.asFlow().first().playingMusic }

    @FlowPreview
    fun updatePlayingState(playingState: PlayingState) = runBlocking(coroutineScope.coroutineContext) {
        val oldState = stateChannel.asFlow().first()
        if (playingState == PlayingState.Stop) {
            stateChannel.send(oldState.copy(playingState = playingState, playingSecond = 0))
        } else {
            stateChannel.send(oldState.copy(playingState = playingState))
        }
    }


}