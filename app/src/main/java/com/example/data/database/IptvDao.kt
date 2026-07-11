package com.example.data.database

import androidx.room.*
import com.example.data.model.PlaylistEntity
import com.example.data.model.ChannelEntity
import com.example.data.model.EpgEntity
import com.example.data.model.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IptvDao {

    // Playlist Queries
    @Query("SELECT * FROM playlists ORDER BY id DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY id DESC")
    suspend fun getAllPlaylistsSync(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: Long)

    // Channel Queries
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId")
    fun getChannelsByPlaylist(playlistId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId")
    suspend fun getChannelsByPlaylistSync(playlistId: Long): List<ChannelEntity>

    @Query("SELECT DISTINCT genre FROM channels WHERE playlistId = :playlistId")
    fun getGenresForPlaylist(playlistId: Long): Flow<List<String>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND isFavorite = 1")
    fun getFavoriteChannels(playlistId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND lastWatched IS NOT NULL ORDER BY lastWatched DESC LIMIT 20")
    fun getRecentChannels(playlistId: Long): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteChannelsByPlaylist(playlistId: Long)

    @Query("UPDATE channels SET isFavorite = :isFav WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFav: Boolean)

    @Query("UPDATE channels SET lastWatched = :timestamp WHERE id = :id")
    suspend fun updateLastWatched(id: Long, timestamp: Long)

    // EPG Queries
    @Query("SELECT * FROM epg_programs WHERE channelName = :channelName AND endTime > :now ORDER BY startTime ASC")
    fun getEpgForChannel(channelName: String, now: Long): Flow<List<EpgEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpgPrograms(programs: List<EpgEntity>)

    @Query("DELETE FROM epg_programs WHERE endTime < :now")
    suspend fun purgeOldEpg(now: Long)

    // Reminder Queries
    @Query("SELECT * FROM reminders WHERE isNotified = 0")
    fun getPendingReminders(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Query("UPDATE reminders SET isNotified = 1 WHERE id = :id")
    suspend fun markReminderAsNotified(id: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: Long)
}
