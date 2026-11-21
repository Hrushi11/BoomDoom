package app.boomdoom.core.player

import android.os.Looper
import androidx.annotation.OptIn as AndroidOptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.boomdoom.core.model.Interaction
import app.boomdoom.core.model.LessonManifest

@AndroidOptIn(UnstableApi::class)
@Composable
fun rememberLessonPlayer(
    source: String,
    manifest: LessonManifest,
    onInteraction: (Interaction) -> Unit,
    onEnded: () -> Unit,
    onError: () -> Unit = {},
): ExoPlayer {
    val context = LocalContext.current
    val interactionCallback by rememberUpdatedState(onInteraction)
    val endedCallback by rememberUpdatedState(onEnded)
    val errorCallback by rememberUpdatedState(onError)
    val player = remember(context, source, manifest) { ExoPlayer.Builder(context).build() }
    DisposableEffect(player) {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) endedCallback()
            }

            override fun onPlayerError(error: PlaybackException) = errorCallback()
        })
        player.setMediaItem(MediaItem.fromUri(source))
        manifest.interactions.forEach { interaction ->
            player.createMessage { _, _ ->
                player.pause()
                interactionCallback(interaction)
            }
                .setPosition((interaction.pauseAtS * 1000).toLong())
                .setLooper(Looper.getMainLooper())
                .setDeleteAfterDelivery(true)
                .send()
        }
        player.prepare()
        onDispose { player.release() }
    }
    return player
}

@AndroidOptIn(UnstableApi::class)
@Composable
fun LessonVideo(player: Player, shutterColor: Int, modifier: Modifier = Modifier) {
    AndroidView(
        factory = {
            PlayerView(it).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT // keep burned-in captions visible
                setShutterBackgroundColor(shutterColor)
            }
        },
        modifier = modifier,
        update = { it.player = player },
        onRelease = { it.player = null },
    )
}
