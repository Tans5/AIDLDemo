// IPlayingMusicService.aidl
package com.example.aidldemo;

import com.example.aidldemo.PlayingMusicModel;
import com.example.aidldemo.MusicPlayingCallback;

// Declare any non-default types here with import statements

interface IPlayingMusicService {

    PlayingMusicModel getPlayingMusicModel();

    void pause();

    void stop();

    void start();

    void addProgressCallback(MusicPlayingCallback callback);

    oneway void removeProgressCallback(long callbackId);

    oneway void newPlayingMusicModel(in PlayingMusicModel newMusic);

}