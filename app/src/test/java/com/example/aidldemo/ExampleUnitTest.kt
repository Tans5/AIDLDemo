package com.example.aidldemo

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Test

import org.junit.Assert.*

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
                author = "Tans"
            )
        )
    }
}