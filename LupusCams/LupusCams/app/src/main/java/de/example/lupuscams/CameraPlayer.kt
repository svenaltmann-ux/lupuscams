package de.example.lupuscams

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** Zeigt einen RTSP-Livestream. RTP läuft über TCP, das ist durch VPN-Tunnel am stabilsten. */
@OptIn(UnstableApi::class)
@Composable
fun CameraPlayer(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var buffering by remember(uri) { mutableStateOf(true) }
    var error by remember(uri) { mutableStateOf<String?>(null) }

    val player = remember(uri) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                RtspMediaSource.Factory()
                    .setForceUseRtpTcp(true)
                    .setTimeoutMs(10_000)
            )
            .build()
            .apply {
                volume = 0f
                setMediaItem(MediaItem.fromUri(uri))
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
                if (state == Player.STATE_READY) error = null
            }

            override fun onPlayerError(e: PlaybackException) {
                error = "Keine Verbindung (${e.errorCodeName})"
                buffering = false
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    this.player = player
                }
            },
            update = { it.player = player },
        )
        when {
            error != null -> Text(error!!, color = Color.White)
            buffering -> CircularProgressIndicator(color = Color.White)
        }
    }
}
