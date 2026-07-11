package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // "ONLINE", "OFFLINE", "XTREAM", "MAC"
    val url: String = "",
    val filePath: String = "",
    val username: String = "",
    val password: String = "",
    val macAddress: String = "",
    val expiryDate: String = ""
)

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val name: String,
    val logoUrl: String? = null,
    val streamUrl: String,
    val genre: String,
    val isFavorite: Boolean = false,
    val lastWatched: Long? = null
)

@Entity(tableName = "epg_programs")
data class EpgEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelName: String,
    val title: String,
    val description: String? = null,
    val startTime: Long,
    val endTime: Long
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelName: String,
    val programTitle: String,
    val startTime: Long,
    val isNotified: Boolean = false
)
