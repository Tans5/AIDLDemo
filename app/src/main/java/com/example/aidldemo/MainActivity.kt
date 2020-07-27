package com.example.aidldemo

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview

class MainActivity : AppCompatActivity() {

    var playingService: IPlayingMusicService? = null

    val musicPlayingCallback: MusicPlayingCallback = object : MusicPlayingCallback.Stub() {

        override fun currentPlayingState(playingState: Int) {
            println("PlayingState: $playingState")
        }

        override fun musicPlayingSeconds(seconds: Int) {
            println("PlayingSeconds: $seconds")
        }

        override fun musicPlaying(newMusic: PlayingMusicModel?) {
            println("MusicPlaying: ${newMusic?.musicName}")
        }

    }

    val playingServerConnection = object : ServiceConnection {

        override fun onServiceDisconnected(name: ComponentName?) {
            playingService = null
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val playingService = IPlayingMusicService.Stub.asInterface(service)
            playingService.newPlayingMusicModel(
                PlayingMusicModel(
                    musicName = "Hello, World!!",
                    length = 60 * 1,
                    author = "Tans"
                )
            )
            playingService.setProgressCallback(musicPlayingCallback)
            this@MainActivity.playingService = playingService
        }

    }

    @ExperimentalCoroutinesApi
    @FlowPreview
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindService(Intent(this, MusicPlayingService::class.java), playingServerConnection, BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(playingServerConnection)
    }
}