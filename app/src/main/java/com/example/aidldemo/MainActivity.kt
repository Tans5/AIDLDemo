package com.example.aidldemo

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.view.View
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

    val callbackId = System.currentTimeMillis()

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
        launch {
            musicPlayingSecondsChannel.send(seconds)
            println("Has send seconds: $seconds!!!")
        }
    }

    override fun musicPlaying(newMusic: PlayingMusicModel?) {
        launch { musicPlayingChannel.send(newMusic) }
    }

    override fun callbackId(): Long = callbackId

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

    var playingService: IPlayingMusicService? = null

    @ObsoleteCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this@MainActivity, MusicPlayingService::class.java))
        } else {
            startService(Intent(this@MainActivity, MusicPlayingService::class.java))
        }

        launch {
            val (_, binder, serviceConnection) = bindServiceSuspend(Intent(this@MainActivity, MusicPlayingService::class.java), Context.BIND_AUTO_CREATE)
            this@MainActivity.playingServiceConnection = serviceConnection
            val playingService = IPlayingMusicService.Stub.asInterface(binder)
            this@MainActivity.playingService = playingService
            playingService.addProgressCallback(musicPlayingCallback)
            launch {
                musicPlayingCallback.musicPlayingSecondsChannel.asFlow()
                    .collect { seconds ->
                        val playingSong = musicPlayingCallback.musicPlayingChannel.asFlow().first()
                        if (playingSong != null) {
                            song_playing_pb.progress =
                                ((seconds.toFloat() / playingSong.length.toFloat()) * 100f).toInt()
                            playing_time_tv.text = "${(seconds / 60).toString()
                                .padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
                            song_duration_tv.text = "${(playingSong.length / 60).toString()
                                .padStart(2, '0')}:${(playingSong.length % 60).toString()
                                .padStart(2, '0')}"
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
                            control_iv.visibility = View.VISIBLE
                        } else {
                            song_name_tv.text = ""
                            author_name_tv.text = ""
                            control_iv.visibility = View.INVISIBLE
                        }
                    }
            }

            launch {
                musicPlayingCallback.playingStateChannel.asFlow()
                    .collect { state ->
                        if (state != MusicPlayingTask.PlayingState.Running) {
                            control_iv.setImageResource(R.drawable.play)
                        } else {
                            control_iv.setImageResource(R.drawable.pause)
                        }
                    }
            }

            control_iv.setOnClickListener {
                launch(Dispatchers.IO) {
                    val currentState = musicPlayingCallback.playingStateChannel.asFlow().first()
                    if (currentState == MusicPlayingTask.PlayingState.Running) {
                        playingService.pause()
                    } else {
                        playingService.start()
                    }
                }
            }

            play_hello_world_bt.setOnClickListener {
                launch {
                    withContext(Dispatchers.IO) {
                        playingService.newPlayingMusicModel(PlayingMusicModel(musicName = "Hello, World!",
                            length = 60 * 5, author = "Tans"))
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val playingServiceConnection = this.playingServiceConnection
        val playingService = this.playingService
        playingService?.removeProgressCallback(musicPlayingCallback.callbackId)
        if (playingServiceConnection != null) {
            unbindService(playingServiceConnection)
        }
        cancel()
    }
}