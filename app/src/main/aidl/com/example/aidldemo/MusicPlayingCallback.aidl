
package com.example.aidldemo;
import com.example.aidldemo.PlayingMusicModel;

interface MusicPlayingCallback {

    oneway void musicPlayingSeconds(int seconds);

    oneway void musicPlaying(in PlayingMusicModel newMusic);

    oneway void currentPlayingState(int playingState);

    long callbackId();
}