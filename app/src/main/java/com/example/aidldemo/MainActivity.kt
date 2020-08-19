package com.example.aidldemo

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.view.View
import com.tans.rxutils.QueryMediaItem
import com.tans.rxutils.QueryMediaType
import com.tans.rxutils.getMedia
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.rx2.await
import kotlin.coroutines.resume


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

    val musicPlayingCallback: ChannelMusicPlayingCallback = ChannelMusicPlayingCallback(this)

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
                            author_name_tv.text = playingMusic.artist
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
                            length = 60 * 5, artist = "Tans", id = 999, albums = "", uri = Uri.Builder().authority("file").path("/123/22").build(), track = 2, mimeType = ""))
                    }
                }
            }

//            launch {
//                val isGrant = RxPermissions(this@MainActivity)
//                    .request(Manifest.permission.READ_EXTERNAL_STORAGE)
//                    .firstOrError()
//                    .await()
//                if (isGrant) {
//                    val musicModels = withContext(Dispatchers.IO) {
//                        getMedia(context = this@MainActivity, queryMediaType = QueryMediaType.Audio)
//                            .map {
//                                it.mapNotNull { e ->
//                                    if (e is QueryMediaItem.Audio) {
//                                        val mmr = MediaMetadataRetriever()
//                                        mmr.setDataSource(this@MainActivity, e.uri)
//                                        val length = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: ""
//                                        PlayingMusicModel(
//                                            id = e.id,
//                                            musicName = e.displayName,
//                                            length =  length.toIntOrNull() ?: 0,
//                                            artist = e.artist,
//                                            albums = e.album,
//                                            uri = e.uri,
//                                            track = e.track,
//                                            mimeType = e.mimeType
//                                        )
//                                    } else {
//                                        null
//                                    }
//                                }
//                            }
//                            .await()
//                    }
//
//                    println("len: ${musicModels.size}")
//                }
//            }
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