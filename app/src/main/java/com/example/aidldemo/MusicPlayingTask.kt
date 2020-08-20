package com.example.aidldemo

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

@FlowPreview
@ExperimentalCoroutinesApi
class MusicPlayingTask(val coroutineScope: CoroutineScope): CoroutineScope by coroutineScope {

    private val player = MediaPlayer()

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
        val runningJob = launch(Dispatchers.IO) {

            while (true) {
                delay(300)
                val oldState = stateChannel.asFlow().filter { it.playingState == PlayingState.Running && it.playingMusic != null }.first()
                val playingSeconds = player.currentPosition / 1000
                val lengthSeconds = player.duration / 1000
                if (playingSeconds >= lengthSeconds) {
                    stateChannel.send(oldState.copy(playingSecond = 0, playingState = PlayingState.Stop))
                } else {
                    stateChannel.send(oldState.copy(playingSecond = playingSeconds))
                }
            }
        }

        player.setOnErrorListener { _, _, _ ->
            launch {
                stateChannel.send(
                    State(
                        playingMusic = null,
                        playingState = PlayingState.Stop,
                        playingSecond = 0
                    )
                )
            }
            false
        }

        CompletableDeferred<Unit>(runningJob).apply {
            invokeOnCompletion {
                player.release()
            }
        }

    }

    @FlowPreview
    fun startTask(newMusic: PlayingMusicModel) {
        launch(Dispatchers.IO) {
            val result = runCatching {
                if (coroutineScope is Context) {
                    if (player.isPlaying) {
                        player.stop()
                        player.seekTo(0)
                    }
                    player.reset()
                    player.setAudioAttributes(AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_MUSIC).build())
                    player.setDataSource(coroutineScope, newMusic.uri)
                    player.prepare()
                    player.start()
                }
            }
            if (result.isSuccess) {
                stateChannel.send(
                    State(
                        playingMusic = newMusic.apply { length = player.duration / 1000 },
                        playingState = PlayingState.Running,
                        playingSecond = 0
                    )
                )
            }
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
        if (oldState.playingState != playingState) {
            when (playingState) {
                PlayingState.Running -> {
                    if (!player.isPlaying) {
                        val result = runCatching {
                            if (oldState.playingState == PlayingState.Stop) { player.seekTo(0) }
                            player.start()
                        }
                        if (result.isSuccess) {
                            stateChannel.send(oldState.copy(playingState = PlayingState.Running))
                        }
                    }
                }
                PlayingState.Stop -> {
                    val result = runCatching { player.stop() }
                    if (result.isSuccess) {
                        stateChannel.send(oldState.copy(playingState = PlayingState.Stop, playingSecond = 0))
                    }
                }
                PlayingState.Pause -> {
                    if (player.isPlaying) {
                        val result = runCatching { player.pause() }
                        if (result.isSuccess) {
                            stateChannel.send(oldState.copy(playingState = PlayingState.Pause))
                        }
                    }
                }
            }
        }
    }


}