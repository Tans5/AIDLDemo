package com.example.aidldemo

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

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
                .filter { it.playingState == PlayingState.Running && it.playingMusic != null }
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