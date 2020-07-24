package com.example.aidldemo

import android.os.Parcel
import android.os.Parcelable

class PlayingMusicModel(
    var musicName: String,
    // Seconds
    var length: Int,
    var author: String
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(musicName)
        parcel.writeInt(length)
        parcel.writeString(author)
    }

    fun readFromParcel(parcel: Parcel) {
        musicName = parcel.readString() ?: ""
        length = parcel.readInt()
        author = parcel.readString() ?: ""
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