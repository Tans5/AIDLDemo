package com.example.aidldemo

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlin.coroutines.resume


@ExperimentalCoroutinesApi
@FlowPreview
class BroadcastMusicPlayingCallback(coroutineScope: CoroutineScope) : MusicPlayingCallback.Stub(), CoroutineScope by coroutineScope {

    val playingStateChannel: BroadcastChannel<MusicPlayingTask.PlayingState> = BroadcastChannel(Channel.CONFLATED)

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
        launch { musicPlayingSecondsChannel.send(seconds) }
    }

    override fun musicPlaying(newMusic: PlayingMusicModel?) {
        launch { musicPlayingChannel.send(newMusic) }
    }

}

suspend fun Activity.bindServiceSuspend(intent: Intent, flags: Int): Triple<ComponentName?, IBinder?, ServiceConnection> = suspendCancellableCoroutine { cont ->
    this.bindService(intent, object : ServiceConnection {

        override fun onServiceDisconnected(name: ComponentName?) {
            cont.cancel(Throwable("Service disconnected: ${name ?: ""}"))
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            cont.resume(Triple(name, service, this))
        }

    }, flags)
}

@ExperimentalCoroutinesApi
@FlowPreview
class MainActivity : AppCompatActivity(), CoroutineScope by CoroutineScope(Dispatchers.Main) {

    val musicPlayingCallback: BroadcastMusicPlayingCallback = BroadcastMusicPlayingCallback(this)

    var playingServiceConnection: ServiceConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        launch {
            val (_, binder, serviceConnection) = bindServiceSuspend(Intent(this@MainActivity, MusicPlayingService::class.java), Context.BIND_AUTO_CREATE)
            this@MainActivity.playingServiceConnection = serviceConnection
            val playingService = IPlayingMusicService.Stub.asInterface(binder)
            playingService.setProgressCallback(musicPlayingCallback)
            withContext(Dispatchers.IO) {
                playingService.newPlayingMusicModel(PlayingMusicModel(musicName = "Hello, World!",
                    length = 60 * 5, author = "Tans"))
            }
            launch {
                musicPlayingCallback.musicPlayingSecondsChannel.asFlow()
                    .collect { seconds ->
                        val playingSong = musicPlayingCallback.musicPlayingChannel.asFlow().first()
                        if (playingSong != null) {
                            song_playing_pb.progress = ((seconds.toFloat() / playingSong.length.toFloat()) * 100f).toInt()
                            playing_time_tv.text = "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
                            song_duration_tv.text = "${(playingSong.length / 60).toString().padStart(2, '0')}:${(playingSong.length % 60).toString().padStart(2, '0')}"
                        } else {
                            song_playing_pb.progress = 0
                            playing_time_tv.text = ""
                            song_duration_tv.text = ""
                        }
                    }
            }

            launch {
                musicPlayingCallback.musicPlayingChannel.asFlow()
                    .collect { playingMusic ->
                        if (playingMusic != null) {
                            println("Music Name: ${playingMusic.musicName}")
                            song_name_tv.text = playingMusic.musicName
                            author_name_tv.text = playingMusic.author
                        } else {
                            song_name_tv.text = ""
                            author_name_tv.text = ""
                        }
                    }
            }

            launch {
                musicPlayingCallback.playingStateChannel.asFlow()
                    .collect { state ->
                        if (state != MusicPlayingTask.PlayingState.Running) {
                            control_bt.text = "Start"
                        } else {
                            control_bt.text = "Pause"
                        }
                    }
            }

            control_bt.setOnClickListener {
                launch(Dispatchers.IO) {
                    val currentState = musicPlayingCallback.playingStateChannel.asFlow().first()
                    if (currentState == MusicPlayingTask.PlayingState.Running) {
                        playingService.pause()
                    } else {
                        playingService.start()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val playingServiceConnection = this.playingServiceConnection
        if (playingServiceConnection != null) {
            unbindService(playingServiceConnection)
        }
        cancel()
    }
}