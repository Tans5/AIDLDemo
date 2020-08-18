package com.example.aidldemo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

@ExperimentalCoroutinesApi
@FlowPreview
class ChannelMusicPlayingCallback(coroutineScope: CoroutineScope) : MusicPlayingCallback.Stub(), CoroutineScope by coroutineScope {

    val callbackId = System.currentTimeMillis()

    val playingStateChannel: BroadcastChannel<MusicPlayingTask.PlayingState> = BroadcastChannel(
        Channel.CONFLATED)

    val musicPlayingSecondsChannel: BroadcastChannel<Int> = BroadcastChannel(Channel.CONFLATED)

    val musicPlayingChannel: BroadcastChannel<PlayingMusicModel?> = BroadcastChannel(Channel.CONFLATED)

    override fun currentPlayingState(playingState: Int) {
        launch {
            val state = when (playingState) {
                MusicPlayingTask.PlayingState.Running.ordinal -> MusicPlayingTask.PlayingState.Running
                MusicPlayingTask.PlayingState.Stop.ordinal -> MusicPlayingTask.PlayingState.Stop
                MusicPlayingTask.PlayingState.Pause.ordinal -> MusicPlayingTask.PlayingState.Pause
                else -> null
            }
            if (state != null) {
                playingStateChannel.send(state)
            }
        }
    }

    override fun musicPlayingSeconds(seconds: Int) {
        launch {
            musicPlayingSecondsChannel.send(seconds)
        }
    }

    override fun musicPlaying(newMusic: PlayingMusicModel?) {
        launch { musicPlayingChannel.send(newMusic) }
    }

    override fun callbackId(): Long = callbackId

}