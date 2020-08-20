package com.example.aidldemo

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.aidldemo.databinding.MusicItemLayoutBinding
import com.tans.rxutils.QueryMediaItem
import com.tans.rxutils.QueryMediaType
import com.tans.rxutils.getMedia
import com.tans.tadapter.adapter.DifferHandler
import com.tans.tadapter.recyclerviewutils.MarginDividerItemDecoration
import com.tans.tadapter.recyclerviewutils.ignoreLastDividerController
import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.toAdapter
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Observable
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle
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
                            music_playing_layout.visibility = View.VISIBLE

                            val bitmap = withContext(Dispatchers.IO) {
                                val picResult = runCatching {
                                    val mmr = MediaMetadataRetriever()
                                    mmr.setDataSource(this@MainActivity, playingMusic.uri)
                                    mmr.embeddedPicture
                                }
                                val picByteArray = picResult.getOrNull()
                                if (picByteArray == null) {
                                    BitmapFactory.decodeResource(resources, R.drawable.play)
                                } else {
                                    BitmapFactory.decodeByteArray(
                                        picByteArray,
                                        0,
                                        picByteArray.size
                                    )
                                }
                            }
                            album_iv.setImageBitmap(bitmap)
                        } else {
                            song_name_tv.text = ""
                            author_name_tv.text = ""
                            music_playing_layout.visibility = View.INVISIBLE
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

            launch {
                val isGrant = RxPermissions(this@MainActivity)
                    .request(Manifest.permission.READ_EXTERNAL_STORAGE)
                    .firstOrError()
                    .await()
                if (isGrant) {
                    val musicModels = withContext(Dispatchers.IO) {
                        getMedia(context = this@MainActivity, queryMediaType = QueryMediaType.Audio)
                            .map {
                                it.mapNotNull { e ->
                                    if (e is QueryMediaItem.Audio) {
                                        PlayingMusicModel(
                                            id = e.id,
                                            musicName = e.displayName,
                                            length =  0,
                                            artist = e.artist,
                                            albums = e.album,
                                            uri = e.uri,
                                            track = e.track,
                                            mimeType = e.mimeType
                                        )
                                    } else {
                                        null
                                    }
                                }
                            }
                            .await()
                    }
                    audio_rv.adapter = SimpleAdapterSpec<PlayingMusicModel, MusicItemLayoutBinding>(
                        layoutId = R.layout.music_item_layout,
                        bindData = { _, model, bind -> bind.data = model },
                        dataUpdater = Observable.just(musicModels),
                        differHandler = DifferHandler(itemsTheSame = { d1, d2 -> d1.id == d2.id }),
                        itemClicks = listOf { binding, _ ->
                            binding.musicItemRootLayout to { _, data ->
                                rxSingle(Dispatchers.IO) {
                                    data.length = 60
                                    playingService.newPlayingMusicModel(data)
                                }
                            }
                        }
                    ).toAdapter()
                    audio_rv.addItemDecoration(
                        MarginDividerItemDecoration.Companion.Builder()
                            .divider(
                                MarginDividerItemDecoration.Companion.ColorDivider(
                                    ContextCompat.getColor(this@MainActivity, R.color.text_grey),
                                    1
                                )
                            )
                            .marginStart(30)
                            .dividerDirection(MarginDividerItemDecoration.Companion.DividerDirection.Horizontal)
                            .dividerController(ignoreLastDividerController)
                            .build()
                    )
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