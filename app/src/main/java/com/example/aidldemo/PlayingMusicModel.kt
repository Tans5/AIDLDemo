package com.example.aidldemo

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable

class PlayingMusicModel(
    var id: Long,
    var musicName: String,
    // Seconds
    var length: Int,
    var artist: String,
    var albums: String,
    var uri: Uri,
    var track: Int,
    var mimeType: String
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readLong(),
        musicName = parcel.readString() ?: "",
        length = parcel.readInt(),
        artist = parcel.readString() ?: "",
        albums = parcel.readString() ?: "",
        uri = parcel.readParcelable<Uri>(Uri::class.java.classLoader) ?: Uri.EMPTY,
        track = parcel.readInt(),
        mimeType = parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(musicName)
        parcel.writeInt(length)
        parcel.writeString(artist)
        parcel.writeString(albums)
        parcel.writeParcelable(uri, flags)
        parcel.writeInt(track)
        parcel.writeString(mimeType)
    }

    fun readFromParcel(parcel: Parcel) {
        musicName = parcel.readString() ?: ""
        length = parcel.readInt()
        artist = parcel.readString() ?: ""
        albums = parcel.readString() ?: ""
        uri = parcel.readParcelable<Uri>(Uri::class.java.classLoader) ?: Uri.EMPTY
        track = parcel.readInt()
        mimeType = parcel.readString() ?: ""
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<PlayingMusicModel> {
        override fun createFromParcel(parcel: Parcel): PlayingMusicModel {
            return PlayingMusicModel(parcel)
        }

        override fun newArray(size: Int): Array<PlayingMusicModel?> {
            return arrayOfNulls(size)
        }
    }

}