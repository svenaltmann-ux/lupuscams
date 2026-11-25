package de.example.lupuscams

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Zeigt einen RTSP-Livestream. RTP läuft über TCP, das ist durch VPN-Tunnel am stabilsten.
 * muted = true schaltet die Tonspur ganz ab (Kacheln); false spielt den Ton ab (Vollbild).
 * reloadKey: wird er erhöht, verbindet sich der Player neu.
 * onError: wird mit einer kurzen Fehlerbeschreibung aufgerufen.
 */
@OptIn(UnstableApi::class)
@Composable
fun CameraPlayer(
    uri: Uri,
    modifier: Modifier = Modifier,
    muted: Boolean = true,
    reloadKey: Int = 0,
    onError: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    var buffering by remember(uri, reloadKey) { mutableStateOf(true) }
    var error by remember(uri, reloadKey) { mutableStateOf<String?>(null) }

    val player = remember(uri, reloadKey) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                RtspMediaSource.Factory()
                    .setForceUseRtpTcp(true)
                    .setTimeoutMs(10_000)
            )
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(uri))
                playWhenReady = true
                prepare()
            }
    }

    LaunchedEffect(player, muted) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, muted)
            .build()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
                if (state == Player.STATE_READY) error = null
            }

            override fun onPlayerError(e: PlaybackException) {
                val cause = e.cause?.message?.take(80)
                val text = if (cause.isNullOrBlank()) e.errorCodeName else "${e.errorCodeName}: $cause"
                error = "Keine Verbindung ($text)"
                buffering = false
                onError?.invoke(text)
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
            error != null -> Text(error!!, color = Color.White, modifier = Modifier.padding(8.dp))
            buffering -> CircularProgressIndicator(color = Color.White)
        }
    }
}
