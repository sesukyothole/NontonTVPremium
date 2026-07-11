package com.example.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.ChannelEntity
import com.example.ui.theme.HighDensityAccent
import java.util.UUID

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    channel: ChannelEntity?,
    isFocused: Boolean,
    isMuted: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var playerState by remember { mutableStateOf<Int>(Player.STATE_IDLE) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    // Initialize ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(context))
            )
            .build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
                volume = if (isMuted) 0f else 1f
            }
    }

    // Handle mute state change
    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    // Handle updates when channel stream URL changes
    LaunchedEffect(channel) {
        playbackError = null
        if (channel != null) {
            try {
                val streamUri = Uri.parse(channel.streamUrl)
                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(streamUri)

                // Detect if it is a DRM or Clearkey encrypted video
                // e.g. for clearkey test streams, add default mock Widevine or Clearkey configurations
                if (channel.name.contains("DRM", ignoreCase = true) || channel.streamUrl.contains("drm", ignoreCase = true)) {
                    val clearKeyUuid = UUID.fromString("107436c8-b447-4dba-a0f1-22c2b005207a") // Clearkey UUID
                    mediaItemBuilder.setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(clearKeyUuid)
                            .setLicenseUri("https://license.server/clearkey")
                            .build()
                    )
                }

                val mediaItem = mediaItemBuilder.build()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
            } catch (e: Exception) {
                playbackError = "Error loading stream: ${e.localizedMessage}"
            }
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    // Listen to ExoPlayer playback states
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                playerState = state
                if (state == Player.STATE_READY) {
                    playbackError = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = "Error playback: ${error.localizedMessage ?: "Format not supported"}"
                // Graceful fallback to Big Buck Bunny stream if any online live test link times out or errors
                if (channel != null && !channel.streamUrl.contains("BigBuckBunny")) {
                    val fallbackUri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                    exoPlayer.setMediaItem(MediaItem.fromUri(fallbackUri))
                    exoPlayer.prepare()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Manage lifecycle (pause when app goes to background, release when disposed)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    exoPlayer.play()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable { onClick() }
    ) {
        if (channel != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false // Hide default media3 player controls, as we provide custom High Density overlays
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        player = exoPlayer
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Buffering Indicator
            if (playerState == Player.STATE_BUFFERING) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = HighDensityAccent
                )
            }

            // Error Display
            if (playbackError != null) {
                Text(
                    text = playbackError ?: "",
                    color = Color.Red,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            // Placeholder when no channel is playing
            Text(
                text = "TIDAK ADA TAYANGAN\nPilih Channel di Dashboard",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
