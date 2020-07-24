
package com.example.aidldemo;
import com.example.aidldemo.PlayingMusicModel;

interface MusicPlayingCallback {

    void musicPlayingSeconds(int seconds);

    void musicPlaying(inout PlayingMusicModel newMusic);

    void currentPlayingState(int playingState);
}