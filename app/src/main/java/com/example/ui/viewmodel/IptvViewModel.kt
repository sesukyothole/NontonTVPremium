package com.example.ui.viewmodel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.PlaylistEntity
import com.example.data.model.ChannelEntity
import com.example.data.model.EpgEntity
import com.example.data.model.ReminderEntity
import com.example.data.repository.IptvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface ScreenState {
    object Splash : ScreenState
    object Dashboard : ScreenState
    object Player : ScreenState
}

class IptvViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = IptvRepository(database.iptvDao(), application)

    private val sharedPrefs = application.getSharedPreferences("iptv_settings", Context.MODE_PRIVATE)

    private fun saveMasterPlaylistId(id: Long) {
        sharedPrefs.edit().putLong("master_playlist_id", id).apply()
    }

    private fun getMasterPlaylistId(): Long {
        return sharedPrefs.getLong("master_playlist_id", -1L)
    }

    // Clock state
    private val _currentTime = MutableStateFlow("")
    val currentTime: StateFlow<String> = _currentTime.asStateFlow()

    // Screen State
    private val _screenState = MutableStateFlow<ScreenState>(ScreenState.Splash)
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    // Playlist loading & list
    val playlists: StateFlow<List<PlaylistEntity>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPlaylist = MutableStateFlow<PlaylistEntity?>(null)
    val selectedPlaylist: StateFlow<PlaylistEntity?> = _selectedPlaylist.asStateFlow()

    private val _activePlaylistChannels = MutableStateFlow<List<ChannelEntity>>(emptyList())
    val activePlaylistChannels: StateFlow<List<ChannelEntity>> = _activePlaylistChannels.asStateFlow()

    // Selected category/genre
    private val _selectedGenre = MutableStateFlow("⭐ Favorites")
    val selectedGenre: StateFlow<String> = _selectedGenre.asStateFlow()

    // Search and Sort
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortAscending = MutableStateFlow(true) // true: A-Z, false: Z-A
    val sortAscending: StateFlow<Boolean> = _sortAscending.asStateFlow()

    // UI state for Loading or Errors
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Notification Trigger Callback for Compose Overlay UI
    private val _recentNotification = MutableStateFlow<String?>(null)
    val recentNotification: StateFlow<String?> = _recentNotification.asStateFlow()

    // Live active genre categories
    val genres: StateFlow<List<String>> = _selectedPlaylist.flatMapLatest { playlist ->
        if (playlist == null) flowOf(emptyList())
        else repository.getGenresForPlaylist(playlist.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live favorite channels for active playlist
    private val favoritesList = _selectedPlaylist.flatMapLatest { playlist ->
        if (playlist == null) flowOf(emptyList())
        else repository.getFavoriteChannels(playlist.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live recent channels for active playlist
    private val recentsList = _selectedPlaylist.flatMapLatest { playlist ->
        if (playlist == null) flowOf(emptyList())
        else repository.getRecentChannels(playlist.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live active playlist channels
    val normalChannelsList = _selectedPlaylist.flatMapLatest { playlist ->
        if (playlist == null) flowOf(emptyList())
        else repository.getChannelsForPlaylist(playlist.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered & sorted channel grid
    @OptIn(ExperimentalCoroutinesApi::class)
    val displayedChannels: StateFlow<List<ChannelEntity>> = combine(
        normalChannelsList,
        favoritesList,
        recentsList,
        _selectedGenre,
        combine(_searchQuery, _sortAscending) { q, s -> Pair(q, s) }
    ) { normal, favs, recents, genre, queryAndSort ->
        val query = queryAndSort.first
        val asc = queryAndSort.second
        var list = when (genre) {
            "⭐ Favorites" -> favs
            "🕒 Recents" -> recents
            else -> normal.filter { it.genre == genre }
        }

        // Apply search query
        if (query.isNotBlank()) {
            list = list.filter { it.name.contains(query, ignoreCase = true) }
        }

        // Apply sort
        list = if (asc) {
            list.sortedBy { it.name.lowercase() }
        } else {
            list.sortedByDescending { it.name.lowercase() }
        }

        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Full screen multi-screen state
    private val _multiScreenMode = MutableStateFlow(1) // 1, 2, or 4 screens
    val multiScreenMode: StateFlow<Int> = _multiScreenMode.asStateFlow()

    // Up to 4 active players. Index 0 is the primary video stream.
    private val _activeChannelsForPlayback = MutableStateFlow<List<ChannelEntity?>>(listOf(null, null, null, null))
    val activeChannelsForPlayback: StateFlow<List<ChannelEntity?>> = _activeChannelsForPlayback.asStateFlow()

    private val _focusedPlayerIndex = MutableStateFlow(0)
    val focusedPlayerIndex: StateFlow<Int> = _focusedPlayerIndex.asStateFlow()

    // Settings
    private val _resolution = MutableStateFlow("HD") // SD, HD, FHD, UHD
    val resolution: StateFlow<String> = _resolution.asStateFlow()

    private val _audioQuality = MutableStateFlow("Stereo") // Stereo, Dolby Atmos, 5.1 Surround
    val audioQuality: StateFlow<String> = _audioQuality.asStateFlow()

    private val _isLandscape = MutableStateFlow(true) // Landscape/Portrait mode
    val isLandscape: StateFlow<Boolean> = _isLandscape.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    // Active focused EPG program
    val focusedEpgProgram: StateFlow<List<EpgEntity>> = _activeChannelsForPlayback.flatMapLatest { playing ->
        val activeChan = playing.getOrNull(_focusedPlayerIndex.value)
        if (activeChan == null) flowOf(emptyList())
        else repository.getEpgForChannel(activeChan.name, System.currentTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Initialize real-time Clock
        viewModelScope.launch {
            val formatter = SimpleDateFormat("dd MMM yyyy • HH:mm:ss", Locale.getDefault())
            while (true) {
                _currentTime.value = formatter.format(Date())
                delay(1000)
            }
        }

        // Initialize active reminders checker loop (10-second poll for in-app or heads-up notifications)
        viewModelScope.launch {
            repository.pendingReminders.collect { reminders ->
                val now = System.currentTimeMillis()
                for (rem in reminders) {
                    if (now >= rem.startTime) {
                        triggerNotification(rem)
                        repository.markReminderAsNotified(rem.id)
                    }
                }
            }
        }

        // Auto-fetch master playlist on first boot
        viewModelScope.launch {
            _isLoading.value = true
            delay(2500) // Beautiful cinematic intro timing
            
            val list = repository.getAllPlaylistsSync()
            val masterId = getMasterPlaylistId()
            
            if (list.isEmpty()) {
                _selectedPlaylist.value = null
                _recentNotification.value = "Silakan masukkan Playlist IPTV pertama Anda"
            } else {
                val masterPlaylist = list.find { it.id == masterId } ?: list.first()
                _selectedPlaylist.value = masterPlaylist
                saveMasterPlaylistId(masterPlaylist.id)
                _recentNotification.value = "Mengecek update playlist '${masterPlaylist.name}'..."
                val res = repository.refreshPlaylist(masterPlaylist.id)
                if (res.isSuccess) {
                    _recentNotification.value = "Playlist '${masterPlaylist.name}' berhasil diperbarui!"
                } else {
                    _recentNotification.value = "Gagal memperbarui '${masterPlaylist.name}', memuat data lokal."
                }
            }
            _isLoading.value = false
            _screenState.value = ScreenState.Dashboard
            delay(2000)
            _recentNotification.value = null
        }
    }

    fun selectPlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            _selectedPlaylist.value = playlist
            _isLoading.value = true
            saveMasterPlaylistId(playlist.id)
            repository.refreshPlaylist(playlist.id)
            _selectedGenre.value = "⭐ Favorites"
            _isLoading.value = false
        }
    }

    fun addPlaylist(
        name: String,
        type: String,
        url: String = "",
        filePath: String = "",
        user: String = "",
        pass: String = "",
        mac: String = ""
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val playlist = PlaylistEntity(
                name = name,
                type = type,
                url = url,
                filePath = filePath,
                username = user,
                password = pass,
                macAddress = mac,
                expiryDate = if (type == "XTREAM" || type == "MAC") "31 Dec 2026" else ""
            )
            val newId = repository.insertPlaylist(playlist)
            val updated = playlist.copy(id = newId)
            _selectedPlaylist.value = updated
            saveMasterPlaylistId(newId)
            
            _recentNotification.value = "Memproses playlist baru..."
            val result = repository.refreshPlaylist(newId)
            
            if (result.isSuccess) {
                val channels = repository.getChannelsForPlaylistSync(newId)
                if (channels.isNotEmpty()) {
                    val firstChannel = channels.first()
                    _activeChannelsForPlayback.value = listOf(firstChannel, null, null, null)
                    _focusedPlayerIndex.value = 0
                    repository.updateLastWatched(firstChannel.id, System.currentTimeMillis())
                    _selectedGenre.value = firstChannel.genre
                    _screenState.value = ScreenState.Player
                    _recentNotification.value = "Memutar otomatis: ${firstChannel.name}"
                } else {
                    _selectedGenre.value = "⭐ Favorites"
                    _recentNotification.value = "Playlist berhasil ditambahkan!"
                }
            } else {
                _selectedGenre.value = "⭐ Favorites"
                _recentNotification.value = "Gagal memuat playlist!"
            }
            
            _isLoading.value = false
            delay(4000)
            if (_recentNotification.value == "Memutar otomatis: ${_activeChannelsForPlayback.value.firstOrNull()?.name}") {
                _recentNotification.value = null
            }
        }
    }

    fun updatePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.insertPlaylist(playlist) // Replace is safe
            _selectedPlaylist.value = playlist
            saveMasterPlaylistId(playlist.id)
            repository.refreshPlaylist(playlist.id)
            _isLoading.value = false
        }
    }

    fun deleteCurrentPlaylist() {
        viewModelScope.launch {
            val current = _selectedPlaylist.value
            if (current != null) {
                deletePlaylist(current)
            }
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.deletePlaylist(playlist.id)
            
            val current = _selectedPlaylist.value
            if (current?.id == playlist.id) {
                _selectedPlaylist.value = null
                _activeChannelsForPlayback.value = listOf(null, null, null, null)
                
                // Re-fetch remaining
                val remaining = repository.getAllPlaylistsSync()
                remaining.firstOrNull()?.let { next ->
                    _selectedPlaylist.value = next
                    saveMasterPlaylistId(next.id)
                    repository.refreshPlaylist(next.id)
                } ?: run {
                    _selectedPlaylist.value = null
                    // Reset master ID preference
                    sharedPrefs.edit().remove("master_playlist_id").apply()
                }
            }
            _isLoading.value = false
        }
    }

    fun reloadPlaylist() {
        viewModelScope.launch {
            val current = _selectedPlaylist.value
            if (current != null) {
                _isLoading.value = true
                repository.refreshPlaylist(current.id)
                _isLoading.value = false
            }
        }
    }

    fun selectGenre(genre: String) {
        _selectedGenre.value = genre
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSort() {
        _sortAscending.value = !_sortAscending.value
    }

    fun selectChannelForPlayback(channel: ChannelEntity, index: Int = -1) {
        viewModelScope.launch {
            val targetIndex = if (index in 0 until _multiScreenMode.value) index else _focusedPlayerIndex.value
            val currentList = _activeChannelsForPlayback.value.toMutableList()
            currentList[targetIndex] = channel
            _activeChannelsForPlayback.value = currentList
            
            // Save to recents
            repository.updateLastWatched(channel.id, System.currentTimeMillis())
            
            // Go to player screen
            _screenState.value = ScreenState.Player

            // Fetch actual EPG programs for this channel from IPTV source
            fetchEpgForChannel(channel)
        }
    }

    fun setFocusedPlayerIndex(index: Int) {
        if (index in 0 until _multiScreenMode.value) {
            _focusedPlayerIndex.value = index
        }
    }

    fun setMultiScreenMode(mode: Int) {
        if (mode in listOf(1, 2, 4)) {
            _multiScreenMode.value = mode
            if (_focusedPlayerIndex.value >= mode) {
                _focusedPlayerIndex.value = 0
            }
        }
    }

    fun changeChannel(next: Boolean) {
        val currentChannel = _activeChannelsForPlayback.value[_focusedPlayerIndex.value] ?: return
        val channelList = displayedChannels.value
        if (channelList.isEmpty()) return

        val currentIndex = channelList.indexOfFirst { it.id == currentChannel.id }
        if (currentIndex == -1) return

        val newIndex = if (next) {
            (currentIndex + 1) % channelList.size
        } else {
            (currentIndex - 1 + channelList.size) % channelList.size
        }

        selectChannelForPlayback(channelList[newIndex], _focusedPlayerIndex.value)
    }

    fun toggleFavoriteChannel(channel: ChannelEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(channel.id, !channel.isFavorite)
        }
    }

    fun setResolution(res: String) {
        _resolution.value = res
    }

    fun setAudioQuality(audio: String) {
        _audioQuality.value = audio
    }

    fun toggleScreenOrientation() {
        _isLandscape.value = !_isLandscape.value
    }

    fun scheduleProgramReminder(program: EpgEntity) {
        viewModelScope.launch {
            val reminder = ReminderEntity(
                channelName = program.channelName,
                programTitle = program.title,
                startTime = program.startTime
            )
            repository.insertReminder(reminder)
            _recentNotification.value = "Pengingat dijadwalkan: ${program.title}"
            delay(4000)
            _recentNotification.value = null
        }
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun goHome() {
        _screenState.value = ScreenState.Dashboard
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    private fun triggerNotification(reminder: ReminderEntity) {
        val context = getApplication<Application>().applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "iptv_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Siaran IPTV Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi pengingat acara IPTV"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Acara Favorit Dimulai!")
            .setContentText("Saksikan '${reminder.programTitle}' sekarang di ${reminder.channelName}!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(reminder.id.toInt(), builder.build())

        // Also trigger an overlay notification inside the Jetpack Compose app!
        viewModelScope.launch {
            _recentNotification.value = "Mulai Sekarang: ${reminder.programTitle} di ${reminder.channelName}!"
            delay(10000)
            _recentNotification.value = null
        }
    }

    fun fetchEpgForChannel(channel: ChannelEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val playlist = repository.getPlaylistByIdSync(channel.playlistId) ?: return@launch
                if (playlist.type == "XTREAM") {
                    val urlParts = channel.streamUrl.split("/")
                    val lastPart = urlParts.lastOrNull() ?: ""
                    val streamId = lastPart.split(".").firstOrNull() ?: ""
                    if (streamId.isNotBlank() && streamId.all { it.isDigit() }) {
                        val epgPrograms = repository.fetchXtreamEpg(playlist, streamId, channel.name)
                        if (epgPrograms.isNotEmpty()) {
                            repository.insertEpgPrograms(epgPrograms)
                        }
                    }
                } else if (playlist.type == "ONLINE" || playlist.type == "OFFLINE") {
                    val epgUrl = repository.getEpgUrlForPlaylist(playlist.id)
                    if (!epgUrl.isNullOrBlank()) {
                        val xmlContent = repository.fetchUrlContent(epgUrl)
                        val epgPrograms = repository.parseXmlTv(xmlContent, setOf(channel.name))
                        if (epgPrograms.isNotEmpty()) {
                            repository.insertEpgPrograms(epgPrograms)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
