
package com.wave.music

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class AnimatedLyric(
    val timeMs: Long,
    val text: String
)

@Composable
fun FullScreenPlayer(
    song: Song,
    controller: MediaController?,
    positionMs: Long,
    durationMs: Long,
    favorite: Boolean,
    accent: Color,
    onFavorite: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var showLyrics by remember { mutableStateOf(false) }
    var syncedLyrics by remember(song.id) { mutableStateOf<List<AnimatedLyric>>(emptyList()) }
    var plainLyrics by remember(song.id) { mutableStateOf<String?>(null) }
    var lyricsLoading by remember(song.id) { mutableStateOf(false) }
    var lyricsError by remember(song.id) { mutableStateOf<String?>(null) }

    val customArtwork = remember(song.id) {
        ArtworkStore.get(context, "track", song.id)
    }
    var artwork by remember(song.id) { mutableStateOf<Uri?>(customArtwork) }

    val artworkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            ArtworkStore.set(context, "track", song.id, uri)
            artwork = uri
        }
    }

    val infinite = rememberInfiniteTransition(label = "wave-cover")
    val coverScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cover-pulse"
    )

    val isPlaying = controller?.isPlaying == true
    val activeIndex = syncedLyrics.indexOfLast { it.timeMs <= positionMs }

    LaunchedEffect(showLyrics, song.id) {
        if (!showLyrics) return@LaunchedEffect

        lyricsLoading = true
        lyricsError = null
        syncedLyrics = emptyList()
        plainLyrics = null

        try {
            val result = LyricsRepository.find(
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.duration
            )

            plainLyrics = result?.plainLyrics
            syncedLyrics = parseLrc(result?.syncedLyrics.orEmpty())

            if (
                result == null ||
                (result.syncedLyrics.isNullOrBlank() &&
                        result.plainLyrics.isNullOrBlank())
            ) {
                lyricsError = "No se encontraron letras para esta canción."
            }
        } catch (e: Exception) {
            lyricsError = "No se pudieron cargar las letras. Comprueba tu conexión."
        } finally {
            lyricsLoading = false
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF101010))
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Minimizar reproductor",
                            tint = Color.White
                        )
                    }
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (showLyrics) "LETRAS" else "REPRODUCIENDO",
                            color = accent,
                            fontSize = 11.sp,
                            letterSpacing = 2.sp
                        )
                        Text(
                            song.album,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = {
                        artworkPicker.launch(arrayOf("image/*"))
                    }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar portada",
                            tint = Color.White
                        )
                    }
                }

                if (showLyrics) {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            song.title,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(song.artist, color = Color.LightGray)
                        Spacer(Modifier.height(14.dp))

                        when {
                            lyricsLoading -> CircularProgressIndicator(
                                color = accent
                            )

                            lyricsError != null -> Text(
                                lyricsError!!,
                                color = Color.LightGray,
                                modifier = Modifier.padding(16.dp)
                            )

                            syncedLyrics.isNotEmpty() -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    contentPadding = PaddingValues(vertical = 24.dp)
                                ) {
                                    itemsIndexed(syncedLyrics) { index, line ->
                                        val selected = index == activeIndex
                                        val lineColor by animateColorAsState(
                                            targetValue = if (selected) accent
                                            else Color.White.copy(alpha = 0.48f),
                                            animationSpec = tween(350),
                                            label = "lyric-color"
                                        )

                                        AnimatedContent(
                                            targetState = selected,
                                            label = "lyric-highlight"
                                        ) { isActive ->
                                            Text(
                                                text = line.text,
                                                color = lineColor,
                                                fontSize = if (isActive) 22.sp else 17.sp,
                                                fontWeight = if (isActive)
                                                    FontWeight.Bold
                                                else FontWeight.Normal,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        controller?.seekTo(line.timeMs)
                                                    }
                                                    .padding(
                                                        horizontal = 12.dp,
                                                        vertical = 10.dp
                                                    )
                                            )
                                        }
                                    }
                                }
                            }

                            plainLyrics != null -> {
                                Text(
                                    plainLyrics!!,
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    lineHeight = 28.sp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(12.dp)
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.weight(0.25f))

                    Crossfade(
                        targetState = artwork,
                        animationSpec = tween(450),
                        label = "cover-change"
                    ) { imageUri ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .scale(if (isPlaying) coverScale else 1f)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFF252525)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageUri != null) {
                                AsyncImage(
                                    model = imageUri,
                                    contentDescription = "Portada de ${song.title}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.Album,
                                    contentDescription = "Sin portada personalizada",
                                    tint = accent,
                                    modifier = Modifier.size(100.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                song.artist,
                                color = Color.LightGray,
                                fontSize = 15.sp
                            )
                        }

                        IconButton(onClick = onFavorite) {
                            Icon(
                                if (favorite) Icons.Default.Favorite
                                else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorito",
                                tint = if (favorite) accent else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Slider(
                        value = positionMs.toFloat().coerceIn(
                            0f,
                            durationMs.coerceAtLeast(1L).toFloat()
                        ),
                        onValueChange = { controller?.seekTo(it.toLong()) },
                        valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent
                        )
                    )

                    Row(Modifier.fillMaxWidth()) {
                        Text(formatPlayerTime(positionMs), color = Color.LightGray)
                        Spacer(Modifier.weight(1f))
                        Text(formatPlayerTime(durationMs), color = Color.LightGray)
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            controller?.shuffleModeEnabled =
                                !(controller?.shuffleModeEnabled ?: false)
                        }) {
                            Icon(
                                Icons.Default.Shuffle,
                                contentDescription = "Aleatorio",
                                tint = if (controller?.shuffleModeEnabled == true)
                                    accent else Color.White
                            )
                        }

                        IconButton(onClick = {
                            controller?.seekToPreviousMediaItem()
                        }) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Anterior",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        FilledIconButton(
                            onClick = {
                                controller?.let {
                                    if (it.isPlaying) it.pause() else it.play()
                                }
                            },
                            modifier = Modifier.size(68.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = accent,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause
                                else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        IconButton(onClick = {
                            controller?.seekToNextMediaItem()
                        }) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Siguiente",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        IconButton(onClick = {
                            controller?.let {
                                it.repeatMode = when (it.repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                            }
                        }) {
                            Icon(
                                if (controller?.repeatMode == Player.REPEAT_MODE_ONE)
                                    Icons.Default.RepeatOne
                                else Icons.Default.Repeat,
                                contentDescription = "Repetir",
                                tint = if (
                                    controller?.repeatMode != Player.REPEAT_MODE_OFF
                                ) accent else Color.White
                            )
                        }
                    }

                    TextButton(onClick = { showLyrics = true }) {
                        Icon(
                            Icons.Default.Lyrics,
                            contentDescription = null,
                            tint = accent
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Ver letras animadas", color = accent)
                    }

                    Spacer(Modifier.weight(0.2f))
                }
            }
        }
    }
}

private fun parseLrc(raw: String): List<AnimatedLyric> {
    val regex = Regex(
        """\[(\d{2}):(\d{2})(?:[.:](\d{1,3}))?](.*)"""
    )

    return raw.lines().mapNotNull { line ->
        val match = regex.matchEntire(line.trim()) ?: return@mapNotNull null
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val fraction = match.groupValues[3]
            .padEnd(3, '0')
            .take(3)
            .toLongOrNull() ?: 0L
        val text = match.groupValues[4].trim()

        if (text.isBlank()) null
        else AnimatedLyric(
            timeMs = minutes * 60_000L + seconds * 1_000L + fraction,
            text = text
        )
    }.sortedBy { it.timeMs }
}

private fun formatPlayerTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000L
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}
