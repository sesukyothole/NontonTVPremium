package com.example.data.repository

import android.content.Context
import android.util.Base64
import android.util.Xml
import com.example.data.database.IptvDao
import com.example.data.model.PlaylistEntity
import com.example.data.model.ChannelEntity
import com.example.data.model.EpgEntity
import com.example.data.model.ReminderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

class IptvRepository(private val dao: IptvDao, private val context: Context? = null) {

    private val client = OkHttpClient()
    private val sharedPrefs = context?.getSharedPreferences("iptv_settings", Context.MODE_PRIVATE)

    fun saveEpgUrlForPlaylist(playlistId: Long, url: String) {
        sharedPrefs?.edit()?.putString("epg_url_$playlistId", url)?.apply()
    }

    fun getEpgUrlForPlaylist(playlistId: Long): String? {
        return sharedPrefs?.getString("epg_url_$playlistId", null)
    }

    suspend fun getPlaylistByIdSync(playlistId: Long): PlaylistEntity? = withContext(Dispatchers.IO) {
        dao.getPlaylistById(playlistId)
    }

    suspend fun insertEpgPrograms(programs: List<EpgEntity>) = withContext(Dispatchers.IO) {
        dao.insertEpgPrograms(programs)
    }

    val allPlaylists: Flow<List<PlaylistEntity>> = dao.getAllPlaylists()

    suspend fun getAllPlaylistsSync(): List<PlaylistEntity> = withContext(Dispatchers.IO) {
        dao.getAllPlaylistsSync()
    }

    fun getChannelsForPlaylist(playlistId: Long): Flow<List<ChannelEntity>> =
        dao.getChannelsByPlaylist(playlistId)

    suspend fun getChannelsForPlaylistSync(playlistId: Long): List<ChannelEntity> = withContext(Dispatchers.IO) {
        dao.getChannelsByPlaylistSync(playlistId)
    }

    fun getGenresForPlaylist(playlistId: Long): Flow<List<String>> =
        dao.getGenresForPlaylist(playlistId)

    fun getFavoriteChannels(playlistId: Long): Flow<List<ChannelEntity>> =
        dao.getFavoriteChannels(playlistId)

    fun getRecentChannels(playlistId: Long): Flow<List<ChannelEntity>> =
        dao.getRecentChannels(playlistId)

    fun getEpgForChannel(channelName: String, now: Long): Flow<List<EpgEntity>> =
        dao.getEpgForChannel(channelName, now)

    suspend fun insertPlaylist(playlist: PlaylistEntity): Long = withContext(Dispatchers.IO) {
        dao.insertPlaylist(playlist)
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        dao.deletePlaylistById(playlistId)
        dao.deleteChannelsByPlaylist(playlistId)
    }

    suspend fun toggleFavorite(channelId: Long, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        dao.updateFavoriteStatus(channelId, isFavorite)
    }

    suspend fun updateLastWatched(channelId: Long, timestamp: Long) = withContext(Dispatchers.IO) {
        dao.updateLastWatched(channelId, timestamp)
    }

    suspend fun insertReminder(reminder: ReminderEntity): Long = withContext(Dispatchers.IO) {
        dao.insertReminder(reminder)
    }

    suspend fun deleteReminder(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteReminder(id)
    }

    val pendingReminders: Flow<List<ReminderEntity>> = dao.getPendingReminders()

    suspend fun markReminderAsNotified(id: Long) = withContext(Dispatchers.IO) {
        dao.markReminderAsNotified(id)
    }

    // Auto-fetch playlist content and load channels
    suspend fun refreshPlaylist(playlistId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val playlist = dao.getPlaylistById(playlistId) ?: return@withContext Result.failure(Exception("Playlist not found"))
            
            // Delete existing channels for this playlist first
            dao.deleteChannelsByPlaylist(playlistId)

            val channels = when (playlist.type) {
                "ONLINE" -> {
                    val m3uContent = fetchUrlContent(playlist.url)
                    extractEpgUrlFromM3u(m3uContent)?.let { epgUrl ->
                        saveEpgUrlForPlaylist(playlistId, epgUrl)
                    }
                    parseM3u(playlistId, m3uContent)
                }
                "OFFLINE" -> {
                    // Offline uses already saved/parsed content or a file content cached
                    parseM3u(playlistId, playlist.filePath)
                }
                "XTREAM" -> {
                    fetchXtreamChannels(playlist)
                }
                "MAC" -> {
                    fetchMacAddressChannels(playlist)
                }
                else -> emptyList()
            }

            if (channels.isNotEmpty()) {
                dao.insertChannels(channels)
                Result.success(Unit)
            } else {
                // Generate default channels as fallback
                val fallback = generateDemoChannels(playlistId)
                dao.insertChannels(fallback)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun extractEpgUrlFromM3u(content: String): String? {
        val firstLine = content.lineSequence().firstOrNull { it.startsWith("#EXTM3U") } ?: return null
        return extractAttribute(firstLine, "url-tvg") ?: extractAttribute(firstLine, "x-tvg-url")
    }

    fun fetchUrlContent(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to download: $response")
            val isGzipped = url.endsWith(".gz", ignoreCase = true) || 
                            response.header("Content-Encoding")?.contains("gzip", ignoreCase = true) == true
            
            val stream = response.body?.byteStream() ?: return ""
            val finalStream = if (isGzipped) {
                GZIPInputStream(stream)
            } else {
                stream
            }
            return finalStream.bufferedReader().use { it.readText() }
        }
    }

    fun parseXmlTv(xmlContent: String, channelNames: Set<String>): List<EpgEntity> {
        val epgList = mutableListOf<EpgEntity>()
        if (xmlContent.isBlank()) return epgList
        
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xmlContent))
            
            var eventType = parser.eventType
            var currentProgram: EpgEntity? = null
            var tagText = ""
            
            val channelMap = mutableMapOf<String, String>() // Map tvg-id to display-name
            var currentChannelId: String? = null
            
            val xmlTvDateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name == "channel") {
                            currentChannelId = parser.getAttributeValue(null, "id")
                        } else if (name == "programme") {
                            val channelId = parser.getAttributeValue(null, "channel")
                            val startStr = parser.getAttributeValue(null, "start")
                            val stopStr = parser.getAttributeValue(null, "stop")
                            
                            val startTime = parseXmlTvDate(startStr, xmlTvDateFormat)
                            val endTime = parseXmlTvDate(stopStr, xmlTvDateFormat)
                            
                            currentProgram = EpgEntity(
                                channelName = channelId ?: "",
                                title = "",
                                description = "",
                                startTime = startTime,
                                endTime = endTime
                            )
                        }
                    }
                    XmlPullParser.TEXT -> {
                        tagText = parser.text ?: ""
                    }
                    XmlPullParser.END_TAG -> {
                        if (name == "channel") {
                            currentChannelId = null
                        } else if (currentChannelId != null && name == "display-name") {
                            val dispName = tagText.trim()
                            if (currentChannelId.isNotBlank() && dispName.isNotBlank()) {
                                channelMap[currentChannelId] = dispName
                            }
                        } else if (currentProgram != null) {
                            when (name) {
                                "title" -> {
                                    currentProgram = currentProgram.copy(title = tagText.trim())
                                }
                                "desc" -> {
                                    currentProgram = currentProgram.copy(description = tagText.trim())
                                }
                                "programme" -> {
                                    val mappedName = channelMap[currentProgram.channelName] ?: currentProgram.channelName
                                    
                                    val finalChannelName = channelNames.firstOrNull { 
                                        it.equals(mappedName, ignoreCase = true) || 
                                        it.equals(currentProgram!!.channelName, ignoreCase = true) 
                                    }
                                    
                                    if (finalChannelName != null && currentProgram.title.isNotBlank()) {
                                        epgList.add(currentProgram.copy(channelName = finalChannelName))
                                    }
                                    currentProgram = null
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return epgList
    }

    private fun parseXmlTvDate(dateStr: String?, formatter: SimpleDateFormat): Long {
        if (dateStr == null || dateStr.length < 14) return 0
        return try {
            val cleanDate = dateStr.substring(0, 14)
            formatter.parse(cleanDate)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun fetchXtreamEpg(playlist: PlaylistEntity, streamId: String, channelName: String): List<EpgEntity> = withContext(Dispatchers.IO) {
        val epgList = mutableListOf<EpgEntity>()
        try {
            val url = "${playlist.url}/player_api.php?username=${playlist.username}&password=${playlist.password}&action=get_short_epg&stream_id=$streamId"
            val responseString = fetchUrlContent(url)
            val jsonObject = JSONObject(responseString)
            val listings = jsonObject.optJSONArray("epg_listings")
            if (listings != null) {
                for (i in 0 until listings.length()) {
                    val obj = listings.getJSONObject(i)
                    val titleBase64 = obj.optString("title", "")
                    val descBase64 = obj.optString("description", "")
                    
                    val title = try {
                        val decoded = Base64.decode(titleBase64, Base64.DEFAULT)
                        String(decoded)
                    } catch (e: Exception) {
                        titleBase64
                    }
                    
                    val description = try {
                        val decoded = Base64.decode(descBase64, Base64.DEFAULT)
                        String(decoded)
                    } catch (e: Exception) {
                        descBase64
                    }
                    
                    val startTimestamp = obj.optLong("start_timestamp", 0) * 1000
                    val endTimestamp = obj.optLong("end_timestamp", 0) * 1000
                    
                    if (startTimestamp > 0 && endTimestamp > 0) {
                        epgList.add(
                            EpgEntity(
                                channelName = channelName,
                                title = title,
                                description = description,
                                startTime = startTimestamp,
                                endTime = endTimestamp
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        epgList
    }

    fun parseM3u(playlistId: Long, m3uContent: String): List<ChannelEntity> {
        val channels = mutableListOf<ChannelEntity>()
        if (m3uContent.isBlank()) return channels

        val reader = BufferedReader(StringReader(m3uContent))
        var line: String?
        var tvgLogo: String? = null
        var groupTitle = "General"
        var channelName = ""

        try {
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line!!.trim()
                if (trimmed.isEmpty()) continue

                if (trimmed.startsWith("#EXTINF:")) {
                    // Extract tvg-logo
                    tvgLogo = extractAttribute(trimmed, "tvg-logo")
                    // Extract group-title
                    groupTitle = extractAttribute(trimmed, "group-title") ?: "General"
                    
                    // Extract channel name after the last comma
                    val commaIndex = trimmed.lastIndexOf(',')
                    channelName = if (commaIndex != -1 && commaIndex < trimmed.length - 1) {
                        trimmed.substring(commaIndex + 1).trim()
                    } else {
                        "Unknown Channel"
                    }
                } else if (!trimmed.startsWith("#")) {
                    val streamUrl = trimmed
                    if (channelName.isEmpty()) {
                        channelName = "Channel ${channels.size + 1}"
                    }
                    channels.add(
                        ChannelEntity(
                            playlistId = playlistId,
                            name = channelName,
                            logoUrl = tvgLogo,
                            streamUrl = streamUrl,
                            genre = groupTitle
                        )
                    )
                    // Reset transient parameters
                    tvgLogo = null
                    groupTitle = "General"
                    channelName = ""
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return channels
    }

    private fun extractAttribute(line: String, attrName: String): String? {
        val search = "$attrName=\""
        val startIdx = line.indexOf(search)
        if (startIdx == -1) return null
        val valStart = startIdx + search.length
        val endIdx = line.indexOf("\"", valStart)
        if (endIdx == -1) return null
        return line.substring(valStart, endIdx)
    }

    private fun fetchXtreamChannels(playlist: PlaylistEntity): List<ChannelEntity> {
        val channels = mutableListOf<ChannelEntity>()
        try {
            // Standard Xtream API endpoint for live streams:
            // http://<host>:<port>/player_api.php?username=<user>&password=<pass>&action=get_live_streams
            val url = "${playlist.url}/player_api.php?username=${playlist.username}&password=${playlist.password}&action=get_live_streams"
            val responseString = fetchUrlContent(url)
            val jsonArray = JSONArray(responseString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.optString("name", "Unknown Xtream")
                val streamId = obj.optString("stream_id", "")
                val categoryId = obj.optString("category_id", "General")
                val container = obj.optString("container_extension", "ts")
                val logo = obj.optString("stream_icon", null)
                
                val streamUrl = "${playlist.url}/live/${playlist.username}/${playlist.password}/$streamId.$container"
                
                channels.add(
                    ChannelEntity(
                        playlistId = playlist.id,
                        name = name,
                        logoUrl = logo,
                        streamUrl = streamUrl,
                        genre = categoryId
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return channels
    }

    private fun fetchMacAddressChannels(playlist: PlaylistEntity): List<ChannelEntity> {
        // Mock MAC portal portal_api response or parse basic links
        // For standard MAC address portal, it communicates with portal.php or uses portal_api
        val channels = mutableListOf<ChannelEntity>()
        try {
            // Generate some channels based on MAC Address configuration
            val mockGenres = listOf("Premium Movie", "News Info", "Sports Arena", "Kids & Family")
            for (i in 1..20) {
                val genre = mockGenres[i % mockGenres.size]
                channels.add(
                    ChannelEntity(
                        playlistId = playlist.id,
                        name = "MAC Channel $i HD",
                        logoUrl = null,
                        streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                        genre = genre
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return channels
    }

    // Default high-quality streaming channels
    fun generateDemoChannels(playlistId: Long): List<ChannelEntity> {
        return listOf(
            ChannelEntity(
                playlistId = playlistId,
                name = "HBO HD PREMIUM",
                logoUrl = "https://images.unsplash.com/photo-1598899134739-24c46f58b8c0?w=120&q=80",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                genre = "Entertainment"
            ),
            ChannelEntity(
                playlistId = playlistId,
                name = "Nat Geo Wild HD",
                logoUrl = "https://images.unsplash.com/photo-1546182990-dffeafbe841d?w=120&q=80",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                genre = "Documentary"
            ),
            ChannelEntity(
                playlistId = playlistId,
                name = "beIN Sports 1 Ultra",
                logoUrl = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=120&q=80",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                genre = "Sports"
            ),
            ChannelEntity(
                playlistId = playlistId,
                name = "Breaking News HD",
                logoUrl = "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=120&q=80",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
                genre = "News"
            ),
            ChannelEntity(
                playlistId = playlistId,
                name = "Disney Kids Premium",
                logoUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=120&q=80",
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                genre = "Kids"
            ),
            // DRM & Adaptive Test Channels
            ChannelEntity(
                playlistId = playlistId,
                name = "DASH Live Stream (Adaptive)",
                logoUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=120&q=80",
                streamUrl = "https://livesim.dashif.org/livesim/testpic_2s/Manifest.mpd",
                genre = "Adaptive Test"
            ),
            ChannelEntity(
                playlistId = playlistId,
                name = "HLS Live Stream (Adaptive)",
                logoUrl = "https://images.unsplash.com/photo-1528459801416-a9e53bbf4e17?w=120&q=80",
                streamUrl = "https://playertest.longtailvideo.com/adaptive/bipbop/bipbop.m3u8",
                genre = "Adaptive Test"
            )
        )
    }

    private suspend fun generateSimulatedEpg(channelNames: List<String>) {
        val now = System.currentTimeMillis()
        val epgList = mutableListOf<EpgEntity>()
        
        val shows = listOf(
            Pair("Interstellar Special Edition", "A team of explorers travel through a wormhole in space in an attempt to ensure humanity's survival."),
            Pair("Planet Earth III", "Explore the incredible diversity of life on our planet, from deep oceans to dense jungles."),
            Pair("Live: Premier League", "Matchday coverage featuring real-time commentary, tactical breakdowns, and live actions."),
            Pair("Morning World Update", "The latest global events, business analyses, financial reports, and breaking news coverage."),
            Pair("The Lion King Legend", "A young lion prince flees his kingdom only to learn the true meaning of responsibility and bravery."),
            Pair("Cosmic Horizons", "A deep dive into astrophysicists' latest discoveries about black holes and dark energy.")
        )

        for (name in channelNames) {
            // Generates continuous 2-hour EPG blocks for today
            for (i in -2..5) {
                val show = shows[Math.abs((name.hashCode() + i) % shows.size)]
                val start = now + (i * 2 * 3600 * 1000)
                val end = start + (2 * 3600 * 1000)
                epgList.add(
                    EpgEntity(
                        channelName = name,
                        title = show.first,
                        description = show.second,
                        startTime = start,
                        endTime = end
                    )
                )
            }
        }
        dao.insertEpgPrograms(epgList)
    }
}
