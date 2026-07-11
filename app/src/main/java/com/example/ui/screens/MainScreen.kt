package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.PlaylistEntity
import com.example.data.model.ChannelEntity
import com.example.data.model.EpgEntity
import com.example.ui.components.VideoPlayer
import com.example.ui.theme.*
import com.example.ui.viewmodel.IptvViewModel
import com.example.ui.viewmodel.ScreenState
import kotlinx.coroutines.delay

@Composable
fun MainScreen(viewModel: IptvViewModel) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val recentNotif by viewModel.recentNotification.collectAsStateWithLifecycle()

    val context = LocalContext.current
    DisposableEffect(screenState) {
        val activity = context as? Activity
        if (activity != null) {
            if (screenState is ScreenState.Dashboard || screenState is ScreenState.Player) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        onDispose {}
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (screenState) {
            is ScreenState.Splash -> SplashScreen(viewModel)
            is ScreenState.Dashboard -> DashboardScreen(viewModel)
            is ScreenState.Player -> FullPlayerScreen(viewModel)
        }

        // Overlay Notification Toast
        recentNotif?.let { message ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = HighDensityAccent,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Notification",
                            tint = HighDensityActiveBg,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = message,
                            color = HighDensityActiveBg,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SplashScreen(viewModel: IptvViewModel) {
    val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HighDensityBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            // Live clock on Splash
            Text(
                text = currentTime,
                color = HighDensityAccent.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Image(
                painter = painterResource(id = com.example.R.drawable.app_logo),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .border(2.dp, HighDensityAccent, CircleShape)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "ERA KONTEN DIGITAL",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            
            Text(
                text = "ENTERTAINMENT PREMIUM",
                color = HighDensityAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
            )

            // Cinematic loading bar with glowing borders
            CircularProgressIndicator(
                color = HighDensityAccent,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Menghubungkan ke Playlist Server secara otomatis...",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Kompatibilitas Optimal Android 5+",
                color = HighDensityAccent.copy(alpha = 0.4f),
                fontSize = 8.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: IptvViewModel) {
    val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val displayedChannels by viewModel.displayedChannels.collectAsStateWithLifecycle()
    val multiScreenMode by viewModel.multiScreenMode.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortAscending by viewModel.sortAscending.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val focusedPlayerIndex by viewModel.focusedPlayerIndex.collectAsStateWithLifecycle()

    var showInputPlaylistDialog by remember { mutableStateOf(false) }
    var showEditPlaylistDialog by remember { mutableStateOf(false) }
    var showSlotSelectionDialogForChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var showPlaylistSelectorDialog by remember { mutableStateOf(false) }
    var playlistToEdit by remember { mutableStateOf<PlaylistEntity?>(null) }

    val activity = LocalContext.current as? Activity

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
            .background(HighDensityBg)
    ) {
        // HEADER SECTION
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(HighDensityHeader)
                .border(1.dp, HighDensityBorder)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Title & Live clock
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(id = com.example.R.drawable.app_logo),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, HighDensityAccent, CircleShape)
                )
                Column {
                    Text(
                        text = "ERA KONTEN DIGITAL",
                        color = HighDensityAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = currentTime,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Header Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Tambah PL Button
                Button(
                    onClick = { showInputPlaylistDialog = true },
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("input_playlist_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HighDensityBorder,
                        contentColor = HighDensityAccent
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Tambah PL", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                // Reload Button
                Button(
                    onClick = { viewModel.reloadPlaylist() },
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("reload_playlist_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HighDensityBorder,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Reload", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                // Multi screen settings (1, 2, 4 screen options)
                listOf(1, 2, 4).forEach { num ->
                    val isActive = multiScreenMode == num
                    Button(
                        onClick = { viewModel.setMultiScreenMode(num) },
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("multiscreen_${num}_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isActive) HighDensityAccent else HighDensityBorder,
                            contentColor = if (isActive) HighDensityActiveBg else Color.White
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("$num Layar", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Close Button
                Button(
                    onClick = { activity?.finish() },
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("close_app_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HighDensityRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Close", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // MAIN WORKSPACE (Sidebar Left & Channels List Right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // SIDEBAR LEFT (Width 150dp)
            Column(
                modifier = Modifier
                    .width(150.dp)
                    .fillMaxHeight()
                    .background(HighDensitySidebar)
                    .border(1.dp, HighDensityBorder)
            ) {
                // Active Playlist Info Block
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HighDensityInner)
                        .border(1.dp, HighDensityBorder)
                        .clickable { showPlaylistSelectorDialog = true }
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MASTER IPTV",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 7.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Pilih Playlist",
                            tint = HighDensityAccent,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    Text(
                        text = selectedPlaylist?.name ?: "BELUM ADA PLAYLIST",
                        color = HighDensityAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = if (selectedPlaylist?.expiryDate?.isNotBlank() == true) "Exp: ${selectedPlaylist?.expiryDate}" else "Exp: Selamanya",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Surface(
                        color = HighDensityBorder.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "KLIK UNTUK GANTI",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp)
                        )
                    }
                }

                // Genre List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp)
                ) {
                    // Favorites Group
                    item {
                        GenreItem(
                            title = "⭐ Favorites",
                            isSelected = selectedGenre == "⭐ Favorites",
                            onClick = { viewModel.selectGenre("⭐ Favorites") }
                        )
                    }

                    // Recents Group
                    item {
                        GenreItem(
                            title = "🕒 Recents",
                            isSelected = selectedGenre == "🕒 Recents",
                            onClick = { viewModel.selectGenre("🕒 Recents") }
                        )
                    }

                    // Dynamic Genres from playlist
                    items(genres) { genre ->
                        GenreItem(
                            title = genre,
                            isSelected = selectedGenre == genre,
                            onClick = { viewModel.selectGenre(genre) }
                        )
                    }
                }
            }

            // CHANNELS VIEW RIGHT
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(HighDensityBg)
            ) {
                // SEARCH & FILTER BAR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HighDensitySidebar)
                        .border(1.dp, HighDensityBorder)
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Search Input
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .background(HighDensityInner, RoundedCornerShape(6.dp))
                            .border(1.dp, HighDensityBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                textStyle = TextStyle(color = Color.White, fontSize = 9.sp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_channels_input"),
                                singleLine = true,
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Cari Channel...",
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontSize = 9.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }

                    // Sort Toggle Button
                    IconButton(
                        onClick = { viewModel.toggleSort() },
                        modifier = Modifier
                            .size(28.dp)
                            .background(HighDensityBorder, RoundedCornerShape(6.dp))
                    ) {
                        Icon(
                            imageVector = if (sortAscending) Icons.Default.SortByAlpha else Icons.Default.Sort,
                            contentDescription = "Urutkan Channel",
                            tint = HighDensityAccent,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // CHANNELS GRID
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = HighDensityAccent)
                    }
                } else if (displayedChannels.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tidak Ada Channel Ditemukan\nInput playlist atau rubah pencarian Anda.",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentPadding = PaddingValues(4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(displayedChannels) { channel ->
                            ChannelGridItem(
                                channel = channel,
                                onChannelClick = {
                                    if (multiScreenMode > 1) {
                                        showSlotSelectionDialogForChannel = channel
                                    } else {
                                        viewModel.selectChannelForPlayback(channel)
                                    }
                                },
                                onFavToggle = { viewModel.toggleFavoriteChannel(channel) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Input Playlist Dialog
    if (showInputPlaylistDialog) {
        PlaylistInputDialog(
            onDismiss = { showInputPlaylistDialog = false },
            onSave = { name, type, url, file, user, pass, mac ->
                viewModel.addPlaylist(name, type, url, file, user, pass, mac)
                showInputPlaylistDialog = false
            }
        )
    }

    // Edit Playlist Dialog
    if (showEditPlaylistDialog && playlistToEdit != null) {
        PlaylistInputDialog(
            playlist = playlistToEdit,
            onDismiss = { showEditPlaylistDialog = false },
            onSave = { name, type, url, file, user, pass, mac ->
                viewModel.updatePlaylist(
                    playlistToEdit!!.copy(
                        name = name,
                        type = type,
                        url = url,
                        filePath = file,
                        username = user,
                        password = pass,
                        macAddress = mac
                    )
                )
                showEditPlaylistDialog = false
            }
        )
    }

    // Playlist Selector Dialog
    if (showPlaylistSelectorDialog) {
        PlaylistSelectorDialog(
            playlists = playlists,
            selectedPlaylist = selectedPlaylist,
            onDismiss = { showPlaylistSelectorDialog = false },
            onSelect = { pl -> viewModel.selectPlaylist(pl) },
            onEdit = { pl ->
                playlistToEdit = pl
                showEditPlaylistDialog = true
            },
            onDelete = { pl -> viewModel.deletePlaylist(pl) },
            onAddClick = {
                showInputPlaylistDialog = true
            }
        )
    }

    // Slot Selection Dialog for Multi-screen Playback
    showSlotSelectionDialogForChannel?.let { channel ->
        AlertDialog(
            onDismissRequest = { showSlotSelectionDialogForChannel = null },
            title = {
                Text(
                    text = "Pilih Layar untuk Memutar",
                    color = HighDensityAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Saluran: ${channel.name}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Pilih posisi layar untuk memutar saluran ini dalam mode $multiScreenMode layar:",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 9.sp
                    )
                }
            },
            containerColor = HighDensitySidebar,
            shape = RoundedCornerShape(12.dp),
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (i in 0 until multiScreenMode) {
                        Button(
                            onClick = {
                                viewModel.selectChannelForPlayback(channel, i)
                                showSlotSelectionDialogForChannel = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (focusedPlayerIndex == i) HighDensityAccent else HighDensityBorder,
                                contentColor = if (focusedPlayerIndex == i) HighDensityActiveBg else Color.White
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Layar ${i + 1}", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            dismissButton = {
                Button(
                    onClick = { showSlotSelectionDialogForChannel = null },
                    colors = ButtonDefaults.buttonColors(containerColor = HighDensityRed),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Batal", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        )
    }
}

@Composable
fun GenreItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(if (isSelected) HighDensityActiveBg else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) HighDensityAccent.copy(alpha = 0.3f) else Color.Transparent
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                fontSize = 9.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(HighDensityAccent, CircleShape)
                )
            }
        }
    }
}

@Composable
fun ChannelGridItem(
    channel: ChannelEntity,
    onChannelClick: () -> Unit,
    onFavToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChannelClick() }
            .border(1.dp, HighDensityBorder, RoundedCornerShape(6.dp)),
        colors = CardDefaults.cardColors(containerColor = HighDensityHeader),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier.padding(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Channel Logo / Fallback Indicator
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(HighDensityActiveBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = channel.name.take(3).uppercase(),
                            color = HighDensityAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Channel Info & Epg
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = channel.name,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "EPG: Acara Hari Ini",
                        color = HighDensityAccent,
                        fontSize = 7.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Favorite Toggle Button
                IconButton(
                    onClick = { onFavToggle() },
                    modifier = Modifier.size(16.dp)
                ) {
                    Icon(
                        imageVector = if (channel.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorit",
                        tint = if (channel.isFavorite) HighDensityAccent else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Simulated Epg Program progress bar
            LinearProgressIndicator(
                progress = { 0.45f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp)),
                color = HighDensityAccent,
                trackColor = HighDensityBorder
            )
        }
    }
}

@Composable
fun FullPlayerScreen(viewModel: IptvViewModel) {
    val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()
    val activeChannels by viewModel.activeChannelsForPlayback.collectAsStateWithLifecycle()
    val focusedIndex by viewModel.focusedPlayerIndex.collectAsStateWithLifecycle()
    val multiScreenMode by viewModel.multiScreenMode.collectAsStateWithLifecycle()
    val resolution by viewModel.resolution.collectAsStateWithLifecycle()
    val audioQuality by viewModel.audioQuality.collectAsStateWithLifecycle()
    val isLandscape by viewModel.isLandscape.collectAsStateWithLifecycle()
    val epgPrograms by viewModel.focusedEpgProgram.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val allChannels by viewModel.normalChannelsList.collectAsStateWithLifecycle()

    var showControls by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Auto-hide controls after 2 seconds of inactivity
    LaunchedEffect(showControls, lastInteractionTime) {
        if (showControls) {
            delay(2000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
            .background(Color.Black)
            .clickable { 
                showControls = !showControls 
                lastInteractionTime = System.currentTimeMillis()
            }
    ) {
        // MULTI-SCREEN VIDEO TILES
        val columnsCount = when (multiScreenMode) {
            1 -> 1
            2 -> 2
            4 -> 2
            else -> 1
        }
        val rowsCount = when (multiScreenMode) {
            1 -> 1
            2 -> 1
            4 -> 2
            else -> 1
        }

        Column(modifier = Modifier.fillMaxSize()) {
            for (r in 0 until rowsCount) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    for (c in 0 until columnsCount) {
                        val playerIdx = r * columnsCount + c
                        val channel = activeChannels.getOrNull(playerIdx)
                        val isFocused = playerIdx == focusedIndex

                        VideoPlayer(
                            channel = channel,
                            isFocused = isFocused,
                            isMuted = isMuted || !isFocused,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(
                                    width = if (isFocused && multiScreenMode > 1) 2.dp else 0.5.dp,
                                    color = if (isFocused && multiScreenMode > 1) HighDensityAccent else HighDensityBorder
                                ),
                            onClick = {
                                viewModel.setFocusedPlayerIndex(playerIdx)
                                showControls = true
                            }
                        )
                    }
                }
            }
        }

        // PLAYER CONTROLS OVERLAYS
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // TOP CONTROLS BAR
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HighDensityHeader)
                        .border(1.dp, HighDensityBorder)
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Left side: App Logo and small label
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = com.example.R.drawable.app_logo),
                            contentDescription = "App Logo",
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, HighDensityAccent, CircleShape)
                        )
                        Text(
                            text = "ERA DIGITAL",
                            color = HighDensityAccent.copy(alpha = 0.8f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Center: Channel Name (Ukuran besar, tebal, jelas)
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.55f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = activeChannels.getOrNull(focusedIndex)?.name?.uppercase() ?: "MEMUTAR STREAM...",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Right side: Live clock
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .background(HighDensityBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = currentTime,
                            color = HighDensityAccent,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // BOTTOM CONTROLS BAR
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HighDensityHeader)
                        .border(1.dp, HighDensityBorder)
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                ) {
                    // Channel Selector Row using allChannels (displays all channels from active playlist)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(HighDensityAccent)
                                )
                                Text(
                                    text = "PILIH / GANTI CHANNEL (Layar Fokus: ${focusedIndex + 1})",
                                    color = HighDensityAccent,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                               )
                            }
                            Text(
                                text = "${allChannels.size} Total Saluran IPTV",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 7.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            items(allChannels) { channel ->
                                val isCurrent = activeChannels.getOrNull(focusedIndex)?.id == channel.id
                                Card(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .fillMaxHeight()
                                        .clickable {
                                            lastInteractionTime = System.currentTimeMillis()
                                            viewModel.selectChannelForPlayback(channel, focusedIndex)
                                        }
                                        .border(
                                            width = if (isCurrent) 1.2.dp else 0.8.dp,
                                            color = if (isCurrent) HighDensityAccent else HighDensityBorder,
                                            shape = RoundedCornerShape(6.dp)
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCurrent) HighDensityActiveBg else HighDensityHeader
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Channel Logo or Fallback
                                        if (!channel.logoUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = channel.logoUrl,
                                                contentDescription = channel.name,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(Color.White)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(HighDensityBorder),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Tv,
                                                    contentDescription = null,
                                                    tint = HighDensityAccent.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(10.dp)
                                                )
                                            }
                                        }
                                        
                                        Text(
                                            text = channel.name,
                                            color = if (isCurrent) HighDensityAccent else Color.White,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Main Buttons Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left bottom: PREV, NEXT, MUTE, HOME (all styled uniformly as Buttons like top)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // PREV Button
                            Button(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    viewModel.changeChannel(false)
                                },
                                modifier = Modifier.height(28.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HighDensityBorder,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Sebelumnya",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("PREV", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }

                            // NEXT Button
                            Button(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    viewModel.changeChannel(true)
                                },
                                modifier = Modifier.height(28.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HighDensityBorder,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Selanjutnya",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("NEXT", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }

                            // MUTE Button
                            Button(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    viewModel.toggleMute()
                                },
                                modifier = Modifier.height(28.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isMuted) HighDensityRed else HighDensityBorder,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = "Mute",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(if (isMuted) "UNMUTE" else "MUTE", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }

                            // HOME Button
                            Button(
                                onClick = { viewModel.goHome() },
                                modifier = Modifier.height(28.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HighDensityAccent,
                                    contentColor = HighDensityActiveBg
                                ),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Home",
                                    tint = HighDensityActiveBg,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("HOME", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Center bottom: Multi-screen selector (options 1, 2, 4)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Multi-screen Layer Selector (1, 2, 4 screen options)
                            Row(
                                modifier = Modifier
                                    .background(HighDensityBorder, RoundedCornerShape(6.dp))
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                             ) {
                                listOf(1, 2, 4).forEach { num ->
                                    val active = multiScreenMode == num
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (active) HighDensityAccent else Color.Transparent)
                                            .clickable { viewModel.setMultiScreenMode(num) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "$num LAYAR",
                                            color = if (active) HighDensityActiveBg else Color.White,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Right bottom: Stream configuration (resolution, audio quality next, screen orientation)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Resolution toggle
                            listOf("SD", "HD", "FHD", "UHD").forEach { res ->
                                val active = resolution == res
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (active) HighDensityAccent else HighDensityBorder)
                                        .clickable { viewModel.setResolution(res) }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = res,
                                        color = if (active) HighDensityActiveBg else Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Audio format toggle
                            val nextAudio = when (audioQuality) {
                                "Stereo" -> "Atmos"
                                "Atmos" -> "Surround 5.1"
                                else -> "Stereo"
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(HighDensityBorder)
                                    .clickable { viewModel.setAudioQuality(nextAudio) }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = audioQuality,
                                    color = HighDensityAccent,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Landscape / portrait switcher
                            IconButton(
                                onClick = { viewModel.toggleScreenOrientation() },
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(HighDensityBorder, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isLandscape) Icons.Default.ScreenRotation else Icons.Default.StayCurrentPortrait,
                                    contentDescription = "Arah Layar",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Capture hardware back button and go home safely
    BackHandler {
        viewModel.goHome()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSelectorDialog(
    playlists: List<PlaylistEntity>,
    selectedPlaylist: PlaylistEntity?,
    onDismiss: () -> Unit,
    onSelect: (PlaylistEntity) -> Unit,
    onEdit: (PlaylistEntity) -> Unit,
    onDelete: (PlaylistEntity) -> Unit,
    onAddClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    onAddClick()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = HighDensityAccent, contentColor = HighDensityActiveBg),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Tambah Playlist",
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("TAMBAH PLAYLIST", fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("TUTUP", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = HighDensityHeader,
        shape = RoundedCornerShape(12.dp),
        title = {
            Text(
                text = "PILIH MASTER IPTV PLAYLIST",
                color = HighDensityAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Belum ada playlist terdaftar.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(playlists) { pl ->
                        val isSelected = pl.id == selectedPlaylist?.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) HighDensityAccent else HighDensityBorder
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    onSelect(pl)
                                    onDismiss()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) HighDensityActiveBg else HighDensityInner
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = pl.name,
                                            color = if (isSelected) HighDensityAccent else Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isSelected) {
                                            Surface(
                                                color = HighDensityAccent.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(3.dp),
                                                border = BorderStroke(1.dp, HighDensityAccent)
                                            ) {
                                                Text(
                                                    text = "MASTER",
                                                    color = HighDensityAccent,
                                                    fontSize = 6.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = when (pl.type) {
                                            "ONLINE" -> "URL: ${pl.url}"
                                            "OFFLINE" -> "File Lokal M3U"
                                            "XTREAM" -> "Xtream: ${pl.url}"
                                            "MAC" -> "MAC: ${pl.url}"
                                            else -> pl.type
                                        },
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 8.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            onEdit(pl)
                                            onDismiss()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Playlist",
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            onDelete(pl)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Hapus Playlist",
                                            tint = HighDensityRed,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

// Complex Multi-Source Input Dialog (Online, Offline file, Xtream API, and MAC Portal)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistInputDialog(
    playlist: PlaylistEntity? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, type: String, url: String, file: String, user: String, pass: String, mac: String) -> Unit
) {
    var name by remember { mutableStateOf(playlist?.name ?: "") }
    var type by remember { mutableStateOf(playlist?.type ?: "ONLINE") } // ONLINE, OFFLINE, XTREAM, MAC
    var url by remember { mutableStateOf(playlist?.url ?: "") }
    var fileContent by remember { mutableStateOf(playlist?.filePath ?: "") }
    var username by remember { mutableStateOf(playlist?.username ?: "") }
    var password by remember { mutableStateOf(playlist?.password ?: "") }
    var macAddress by remember { mutableStateOf(playlist?.macAddress ?: "") }

    val context = LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            try {
                context.contentResolver.openInputStream(selectedUri)?.use { inputStream ->
                    val text = inputStream.bufferedReader().use { it.readText() }
                    fileContent = text
                    if (name.isBlank()) {
                        var displayName: String? = null
                        val cursor = context.contentResolver.query(selectedUri, null, null, null, null)
                        cursor?.use { c ->
                            if (c.moveToFirst()) {
                                val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (nameIndex != -1) {
                                    displayName = c.getString(nameIndex)
                                }
                            }
                        }
                        name = displayName?.removeSuffix(".m3u")?.removeSuffix(".m3u8")?.removeSuffix(".txt") ?: "Playlist Lokal"
                    }
                }
            } catch (e: Exception) {
                // Silent catch
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name, type, url, fileContent, username, password, macAddress)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = HighDensityAccent, contentColor = HighDensityActiveBg),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("SIMPAN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("BATAL", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            }
        },
        containerColor = HighDensityHeader,
        shape = RoundedCornerShape(12.dp),
        title = {
            Text(
                text = if (playlist == null) "INPUT PLAYLIST IPTV BARU" else "EDIT PLAYLIST IPTV",
                color = HighDensityAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Playlist Name Text Field
                item {
                    Text("Nama Playlist", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .testTag("playlist_name_input"),
                        textStyle = TextStyle(fontSize = 11.sp, color = Color.White),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = HighDensityInner,
                            unfocusedContainerColor = HighDensityInner,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                }

                // Playlist Source Type Tabs
                item {
                    Text("Sumber Playlist", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .background(HighDensityInner, RoundedCornerShape(6.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf(
                            Triple("ONLINE", "M3U Link", "type_online_tab"),
                            Triple("OFFLINE", "M3U File", "type_offline_tab"),
                            Triple("XTREAM", "Xtream", "type_xtream_tab"),
                            Triple("MAC", "MAC IPTV", "type_mac_tab")
                        ).forEach { (tKey, tLabel, tag) ->
                            val active = type == tKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (active) HighDensityAccent else Color.Transparent)
                                    .clickable { type = tKey }
                                    .padding(vertical = 4.dp)
                                    .testTag(tag),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tLabel,
                                    color = if (active) HighDensityActiveBg else Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Dynamic Input forms based on playlist types chosen
                item {
                    when (type) {
                        "ONLINE" -> {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Text("M3U URL Server Playlist", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                TextField(
                                    value = url,
                                    onValueChange = { url = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .testTag("playlist_url_input"),
                                    textStyle = TextStyle(fontSize = 10.sp, color = Color.White),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = HighDensityInner,
                                        unfocusedContainerColor = HighDensityInner
                                    ),
                                    singleLine = true
                                )
                            }
                        }
                        "OFFLINE" -> {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Isi File M3U Anda", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Button(
                                        onClick = { filePickerLauncher.launch("*/*") },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = HighDensityAccent,
                                            contentColor = HighDensityActiveBg
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(24.dp),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = "Pilih File",
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Text("PILIH FILE M3U", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                TextField(
                                    value = fileContent,
                                    onValueChange = { fileContent = it },
                                    placeholder = {
                                        Text(
                                            text = "Klik tombol PILIH FILE M3U di kanan atas untuk memuat file dari memori perangkat, atau tempel teks playlist M3U di sini...",
                                            fontSize = 9.sp,
                                            color = Color.White.copy(alpha = 0.4f)
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .padding(top = 4.dp)
                                        .testTag("playlist_file_input"),
                                    textStyle = TextStyle(fontSize = 10.sp, color = Color.White),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = HighDensityInner,
                                        unfocusedContainerColor = HighDensityInner,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                            }
                        }
                        "XTREAM" -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Column {
                                    Text("Host URL (e.g. http://xtream.xyz:8080)", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    TextField(
                                        value = url,
                                        onValueChange = { url = it },
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        textStyle = TextStyle(fontSize = 10.sp, color = Color.White),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = HighDensityInner,
                                            unfocusedContainerColor = HighDensityInner
                                        ),
                                        singleLine = true
                                    )
                                }
                                Column {
                                    Text("Username", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    TextField(
                                        value = username,
                                        onValueChange = { username = it },
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        textStyle = TextStyle(fontSize = 10.sp, color = Color.White),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = HighDensityInner,
                                            unfocusedContainerColor = HighDensityInner
                                        ),
                                        singleLine = true
                                    )
                                }
                                Column {
                                    Text("Password", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    TextField(
                                        value = password,
                                        onValueChange = { password = it },
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        textStyle = TextStyle(fontSize = 10.sp, color = Color.White),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = HighDensityInner,
                                            unfocusedContainerColor = HighDensityInner
                                        ),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                        "MAC" -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Column {
                                    Text("Portal Server URL (e.g. http://mag.server:80)", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    TextField(
                                        value = url,
                                        onValueChange = { url = it },
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        textStyle = TextStyle(fontSize = 10.sp, color = Color.White),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = HighDensityInner,
                                            unfocusedContainerColor = HighDensityInner
                                        ),
                                        singleLine = true
                                    )
                                }
                                Column {
                                    Text("MAC Address Device (e.g. 00:1A:79:XX:XX:XX)", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    TextField(
                                        value = macAddress,
                                        onValueChange = { macAddress = it },
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        textStyle = TextStyle(fontSize = 10.sp, color = Color.White),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = HighDensityInner,
                                            unfocusedContainerColor = HighDensityInner
                                        ),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

// Substring basic input text field to avoid material full field padding overheads
@Composable
fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    decorationBox: @Composable (@Composable () -> Unit) -> Unit
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        modifier = modifier,
        singleLine = singleLine,
        decorationBox = { innerTextField -> decorationBox(innerTextField) }
    )
}
