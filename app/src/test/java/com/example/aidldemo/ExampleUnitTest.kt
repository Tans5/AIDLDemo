package com.example.aidldemo

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() = runBlocking {
        val task = MusicPlayingTask(this)
        task.startTask(
            PlayingMusicModel(
                musicName = "Hello, World",
                length = 300,
                artist = "Tans"
            )
        )
        launch {
            task.stateChannel.asFlow()
                .collect {
                    println(it)
                }
        }

        launch {
            delay(1000)
            task.updatePlayingState(MusicPlayingTask.PlayingState.Stop)
            delay(1000)
            task.updatePlayingState(MusicPlayingTask.PlayingState.Running)
            delay(2000)
            task.updatePlayingState(MusicPlayingTask.PlayingState.Pause)
            delay(1000)
            task.updatePlayingState(MusicPlayingTask.PlayingState.Running)
        }
        Unit
    }
}