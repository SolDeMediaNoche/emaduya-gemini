
package com.wave.music

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val WaveBlack = Color(0xFF101010)
private val WaveSurface = Color(0xFF222222)
private val WaveGreen = Color(0xFFB8FF5C)
private val WaveWhite = Color(0xFFF5F5F5)
private val WaveMuted = Color(0xFFAAAAAA)

class MainActivity : ComponentActivity() {

    private var controller by mutableStateOf<MediaController?>(null)
    private var songs by mutableStateOf<List<Song>>(emptyList())
    private var permissionGranted by mutableStateOf(false)
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        permissionGranted =
            ContextCompat.checkSelfPermission(this, permission) ==
                    PackageManager.PERMISSION_GRANTED

        val token = SessionToken(
            this,
            ComponentName(this, MusicPlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener(
            {
                try {
                    controller = controllerFuture?.get()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            MoreExecutors.directExecutor()
        )

        setContent {
            val permissionLauncher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    permissionGranted = granted
                    if (granted) refreshSongs()
                }

            LaunchedEffect(permissionGranted) {
                if (permissionGranted) refreshSongs()
                else permissionLauncher.launch(permission)
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = WaveGreen,
                    background = WaveBlack,
                    surface = WaveSurface,
                    onBackground = WaveWhite,
                    onSurface = WaveWhite
                )
            ) {
                WaveApp(
                    songs = songs,
                    controller = controller,
                    permissionGranted = permissionGranted,
                    onRefresh = ::refreshSongs,
                    onRequestPermission = {
                        permissionLauncher.launch(permission)
                    }
                )
            }
        }
    }

    private fun refreshSongs() {
        if (!permissionGranted) return

        // Evita bloquear el hilo de la interfaz al escanear la biblioteca.
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val result = MusicRepository.getSongs(this@MainActivity)
            withContext(Dispatchers.Main) {
                songs = result
            }
        }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onDestroy()
    }
}

@Composable
private fun WaveApp(
    songs: List<Song>,
    controller: MediaController?,
    permissionGranted: Boolean,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit
) {
    var tab by remember { mutableStateOf("Inicio") }
    var search by remember { mutableStateOf("") }
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var showPlayer by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var shuffle by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var favorites by remember { mutableStateOf(setOf<Long>()) }
    var accent by remember { mutableStateOf(WaveGreen) }
    var timerMinutes by remember { mutableIntStateOf(30) }
    var timerActive by remember { mutableStateOf(false) }
    var crossfadeEnabled by remember { mutableStateOf(false) }
    var crossfadeSeconds by remember { mutableFloatStateOf(5f) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(controller) {
        while (true) {
            controller?.let { player ->
                playing = player.isPlaying
                position = player.currentPosition.coerceAtLeast(0L)
                duration = player.duration.takeIf { it > 0L } ?: 0L
                shuffle = player.shuffleModeEnabled
                repeatMode = player.repeatMode

                val id = player.currentMediaItem?.mediaId?.toLongOrNull()
                if (id != null) {
                    currentSong = songs.firstOrNull { it.id == id } ?: currentSong
                }
            }
            delay(350)
        }
    }

    val visibleSongs = remember(songs, search, tab, favorites) {
        when (tab) {
            "Buscar" -> songs.filter {
                search.isBlank() ||
                        it.title.contains(search, true) ||
                        it.artist.contains(search, true) ||
                        it.album.contains(search, true)
            }
            "Favoritos" -> songs.filter { it.id in favorites }
            else -> songs
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(WaveBlack)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = WaveBlack,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    "WAVE",
                    color = accent,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    "TU MÚSICA, TU MUNDO",
                    color = WaveMuted,
                    fontSize = 9.sp,
                    letterSpacing = 1.5.sp
                )
            }

            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Actualizar biblioteca",
                    tint = WaveWhite
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Inicio", "Buscar", "Biblioteca", "Favoritos", "Recap")
                .forEach { destination ->
                    val selected = tab == destination
                    Surface(
                        color = if (selected) accent else WaveSurface,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.clickable { tab = destination }
                    ) {
                        Text(
                            destination,
                            color = if (selected) WaveBlack else WaveWhite,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 10.dp
                            )
                        )
                    }
                }
        }

        Spacer(Modifier.height(16.dp))

        if (tab == "Buscar") {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                placeholder = { Text("Canción, artista o álbum") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            )
        }

        if (tab == "Inicio") {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    "TU BIBLIOTECA MUSICAL",
                    color = accent,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp
                )
                Text(
                    "Que suene tu mundo.",
                    color = WaveWhite,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${songs.size} canciones en tu dispositivo",
                    color = WaveMuted,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        when {
            !permissionGranted -> EmptyState(
                "Permiso necesario",
                "Permite el acceso a tus archivos de música.",
                "Dar permiso",
                onRequestPermission
            )

            tab == "Ajustes" -> {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(20.dp)
                ) {
                    item {
                        Text(
                            "Ajustes",
                            color = WaveWhite,
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(20.dp))
                        Text("Color de acento", color = WaveMuted)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            listOf(
                                WaveGreen,
                                Color(0xFF55C8FF),
                                Color(0xFFCE93FF),
                                Color(0xFFFF7997),
                                Color(0xFFFFC857)
                            ).forEach { color ->
                                Box(
                                    Modifier
                                        .size(34.dp)
                                        .background(color, CircleShape)
                                        .clickable { accent = color }
                                )
                            }
                        }

                        HorizontalDivider(
                            Modifier.padding(vertical = 22.dp),
                            color = WaveSurface
                        )

                        Text(
                            "Temporizador de apagado",
                            color = WaveWhite,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "La música se pausará cuando termine el tiempo.",
                            color = WaveMuted,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("$timerMinutes minutos", color = accent)
                        Slider(
                            value = timerMinutes.toFloat(),
                            onValueChange = {
                                timerMinutes = it.toInt().coerceIn(1, 180)
                            },
                            valueRange = 1f..180f,
                            steps = 178
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(15, 30, 45, 60).forEach { minutes ->
                                OutlinedButton(
                                    onClick = { timerMinutes = minutes },
                                    contentPadding = PaddingValues(
                                        horizontal = 10.dp
                                    )
                                ) {
                                    Text("$minutes min")
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val args = Bundle().apply {
                                    putInt(
                                        MusicPlaybackService.KEY_TIMER_MINUTES,
                                        timerMinutes
                                    )
                                }
                                controller?.sendCustomCommand(
                                    SessionCommand(
                                        MusicPlaybackService.ACTION_SET_TIMER,
                                        Bundle.EMPTY
                                    ),
                                    args
                                )?.addListener(
                                    { timerActive = true },
                                    MoreExecutors.directExecutor()
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = controller != null
                        ) {
                            Text("Activar temporizador")
                        }
                        OutlinedButton(
                            onClick = {
                                controller?.sendCustomCommand(
                                    SessionCommand(
                                        MusicPlaybackService.ACTION_CANCEL_TIMER,
                                        Bundle.EMPTY
                                    ),
                                    Bundle.EMPTY
                                )?.addListener(
                                    { timerActive = false },
                                    MoreExecutors.directExecutor()
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = controller != null && timerActive
                        ) {
                            Text("Cancelar temporizador")
                        }
                        if (timerActive) {
                            Text(
                                "Temporizador activado. El contador exacto se mantiene en el servicio.",
                                color = accent,
                                fontSize = 12.sp
                            )
                        }

                        HorizontalDivider(
                            Modifier.padding(vertical = 22.dp),
                            color = WaveSurface
                        )

                        Text(
                            "Crossfade",
                            color = WaveWhite,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Mezclar el final de una canción con la siguiente",
                                color = WaveMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = crossfadeEnabled,
                                onCheckedChange = { crossfadeEnabled = it }
                            )
                        }
                        Text(
                            "${crossfadeSeconds.toInt()} segundos",
                            color = accent
                        )
                        Slider(
                            value = crossfadeSeconds,
                            onValueChange = { crossfadeSeconds = it },
                            valueRange = 1f..50f,
                            enabled = crossfadeEnabled
                        )
                        Text(
                            "La selección visual está preparada; la mezcla de audio requiere integrar un segundo reproductor y su transición de volumen.",
                            color = WaveMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            tab == "Recap" -> {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    Text(
                        "Tu recap",
                        color = WaveWhite,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(14.dp))
                    RecapCard("Canciones en biblioteca", "${songs.size}", accent)
                    Spacer(Modifier.height(10.dp))
                    RecapCard(
                        "Canción actual",
                        currentSong?.title ?: "Todavía no reproduces música",
                        accent
                    )
                    Spacer(Modifier.height(10.dp))
                    RecapCard(
                        "Favoritos de esta sesión",
                        "${favorites.size}",
                        accent
                    )
                    Text(
                        "El historial semanal, minutos acumulados y top de artistas requieren guardar cada reproducción en una base de datos local.",
                        color = WaveMuted,
                        fontSize = 12.sp
                    )
                }
            }

            else -> {
                if (visibleSongs.isEmpty()) {
                    EmptyState(
                        "No hay canciones para mostrar",
                        "Actualiza la biblioteca o cambia la búsqueda.",
                        "Actualizar",
                        onRefresh
                    )
                } else {
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            horizontal = 14.dp,
                            vertical = 8.dp
                        )
                    ) {
                        items(visibleSongs, key = { it.id }) { song ->
                            SongRow(
                                song = song,
                                accent = accent,
                                favorite = song.id in favorites,
                                onFavorite = {
                                    favorites = if (song.id in favorites) {
                                        favorites - song.id
                                    } else {
                                        favorites + song.id
                                    }
                                },
                                onClick = {
                                    val player = controller ?: return@SongRow
                                    val index = songs.indexOfFirst {
                                        it.id == song.id
                                    }
                                    if (index >= 0) {
                                        val playlist = songs.map { item ->
                                            MediaItem.Builder()
                                                .setMediaId(item.id.toString())
                                                .setUri(item.uri)
                                                .setMediaMetadata(
                                                    MediaMetadata.Builder()
                                                        .setTitle(item.title)
                                                        .setArtist(item.artist)
                                                        .setAlbumTitle(item.album)
                                                        .build()
                                                )
                                                .build()
                                        }

                                        // Si se toca la canción que ya suena,
                                        // se abre el reproductor sin reiniciarla.
                                        if (
                                            player.currentMediaItem?.mediaId ==
                                            song.id.toString()
                                        ) {
                                            showPlayer = true
                                        } else {
                                            player.setMediaItems(
                                                playlist,
                                                index,
                                                0L
                                            )
                                            player.prepare()
                                            player.play()
                                            currentSong = song
                                            showPlayer = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (currentSong != null) {
            MiniPlayer(
                song = currentSong!!,
                playing = playing,
                accent = accent,
                onOpen = { showPlayer = true },
                onToggle = {
                    controller?.let {
                        if (it.isPlaying) it.pause() else it.play()
                    }
                },
                onNext = { controller?.seekToNextMediaItem() }
            )
        }

        NavigationBar(
            containerColor = Color(0xFF080808),
            contentColor = WaveWhite
        ) {
            listOf(
                "Inicio" to Icons.Default.Home,
                "Buscar" to Icons.Default.Search,
                "Biblioteca" to Icons.Default.LibraryMusic,
                "Ajustes" to Icons.Default.Settings
            ).forEach { (label, icon) ->
                NavigationBarItem(
                    selected = tab == label,
                    onClick = { tab = label },
                    icon = { Icon(icon, contentDescription = label) },
                    label = { Text(label, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = accent,
                        selectedTextColor = accent,
                        indicatorColor = WaveSurface,
                        unselectedIconColor = WaveMuted,
                        unselectedTextColor = WaveMuted
                    )
                )
            }
        }
    }

    if (showPlayer && currentSong != null) {
        PlayerDialog(
            song = currentSong!!,
            controller = controller,
            accent = accent,
            playing = playing,
            position = position,
            duration = duration,
            favorite = currentSong!!.id in favorites,
            shuffle = shuffle,
            repeatMode = repeatMode,
            onFavorite = {
                val id = currentSong!!.id
                favorites = if (id in favorites) favorites - id else favorites + id
            },
            onShuffle = {
                controller?.let {
                    it.shuffleModeEnabled = !it.shuffleModeEnabled
                }
            },
            onRepeat = {
                controller?.let {
                    it.repeatMode = when (it.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }
            },
            onLyrics = { showLyrics = true },
            onDismiss = { showPlayer = false }
        )
    }

    if (showLyrics && currentSong != null) {
        LyricsDialog(
            song = currentSong!!,
            position = position,
            onSeek = { controller?.seekTo(it) },
            onDismiss = { showLyrics = false }
        )
    }
}

@Composable
private fun SongRow(
    song: Song,
    accent: Color,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(54.dp)
                .background(WaveSurface, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = WaveWhite,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${song.artist} • ${song.album}",
                color = WaveMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onFavorite) {
            Icon(
                if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorito",
                tint = if (favorite) accent else WaveMuted
            )
        }
        Text(
            formatTime(song.duration),
            color = WaveMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun MiniPlayer(
    song: Song,
    playing: Boolean,
    accent: Color,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .background(WaveSurface, RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Album,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(38.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = WaveWhite,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(song.artist, color = WaveMuted, fontSize = 12.sp)
        }
        IconButton(onClick = onToggle) {
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Reproducir o pausar",
                tint = accent
            )
        }
        IconButton(onClick = onNext) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = "Siguiente",
                tint = WaveWhite
            )
        }
    }
}

@Composable
private fun PlayerDialog(
    song: Song,
    controller: MediaController?,
    accent: Color,
    playing: Boolean,
    position: Long,
    duration: Long,
    favorite: Boolean,
    shuffle: Boolean,
    repeatMode: Int,
    onFavorite: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onLyrics: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(WaveBlack, RoundedCornerShape(26.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "REPRODUCIENDO",
                    color = accent,
                    letterSpacing = 2.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Cerrar",
                        tint = WaveWhite
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(WaveSurface, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Album,
                    contentDescription = "Portada",
                    tint = accent,
                    modifier = Modifier.size(100.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                song.title,
                color = WaveWhite,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(song.artist, color = WaveMuted)
            Text(song.album, color = WaveMuted, fontSize = 12.sp)

            Spacer(Modifier.height(16.dp))
            Slider(
                value = position.toFloat().coerceIn(
                    0f,
                    duration.coerceAtLeast(1L).toFloat()
                ),
                onValueChange = { controller?.seekTo(it.toLong()) },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent
                )
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime(position), color = WaveMuted, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(formatTime(duration), color = WaveMuted, fontSize = 11.sp)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onShuffle) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Aleatorio",
                        tint = if (shuffle) accent else WaveMuted
                    )
                }
                IconButton(onClick = {
                    controller?.seekToPreviousMediaItem()
                }) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = WaveWhite
                    )
                }
                FilledIconButton(
                    onClick = {
                        controller?.let {
                            if (it.isPlaying) it.pause() else it.play()
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = accent,
                        contentColor = WaveBlack
                    ),
                    modifier = Modifier.size(60.dp)
                ) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(34.dp)
                    )
                }
                IconButton(onClick = {
                    controller?.seekToNextMediaItem()
                }) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Siguiente",
                        tint = WaveWhite
                    )
                }
                IconButton(onClick = onRepeat) {
                    Icon(
                        if (repeatMode == Player.REPEAT_MODE_ONE)
                            Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repetición",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF)
                            accent else WaveMuted
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = onFavorite) {
                    Icon(
                        if (favorite) Icons.Default.Favorite
                        else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (favorite) accent else WaveWhite
                    )
                    Text(" Favorito", color = WaveWhite)
                }
                TextButton(onClick = onLyrics) {
                    Icon(
                        Icons.Default.Lyrics,
                        contentDescription = null,
                        tint = WaveWhite
                    )
                    Text(" Letras", color = WaveWhite)
                }
            }
        }
    }
}

@Composable
private fun LyricsDialog(
    song: Song,
    position: Long,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var loading by remember(song.id) { mutableStateOf(true) }
    var error by remember(song.id) { mutableStateOf<String?>(null) }
    var syncedLyrics by remember(song.id) { mutableStateOf<String?>(null) }
    var plainLyrics by remember(song.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(song.id) {
        loading = true
        error = null
        syncedLyrics = null
        plainLyrics = null

        try {
            val result = LyricsRepository.find(
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.duration
            )
            syncedLyrics = result?.syncedLyrics
            plainLyrics = result?.plainLyrics

            if (
                result == null ||
                (result.syncedLyrics == null && result.plainLyrics == null)
            ) {
                error = "No se encontraron letras para esta canción."
            }
        } catch (e: Exception) {
            error = "No se pudieron consultar las letras. Comprueba Internet."
        } finally {
            loading = false
        }
    }

    val lines = remember(syncedLyrics, plainLyrics) {
        val source = syncedLyrics ?: plainLyrics.orEmpty()
        source.lines().mapNotNull { line ->
            val match = Regex(
                """\[(\d{2}):(\d{2})(?:\.(\d{1,3}))?](.*)"""
            ).matchEntire(line.trim())

            if (match != null) {
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val fraction = match.groupValues[3]
                    .padEnd(3, '0')
                    .take(3)
                    .toLongOrNull() ?: 0L
                val text = match.groupValues[4].trim()
                if (text.isBlank()) null
                else (minutes * 60_000L + seconds * 1000L + fraction) to text
            } else if (syncedLyrics == null && line.isNotBlank()) {
                null
            } else null
        }
    }

    val activeLine = lines.indexOfLast { it.first <= position }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 650.dp)
                .background(WaveBlack, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("LETRAS", color = WaveGreen, letterSpacing = 2.sp)
                    Text(
                        song.title,
                        color = WaveWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(song.artist, color = WaveMuted, fontSize = 12.sp)
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cerrar letras",
                        tint = WaveWhite
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                loading -> Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = WaveGreen)
                }

                error != null -> Text(
                    error!!,
                    color = WaveMuted,
                    modifier = Modifier.padding(16.dp)
                )

                syncedLyrics != null -> LazyColumn {
                    items(lines.size) { index ->
                        val (time, text) = lines[index]
                        Text(
                            text,
                            color = if (index == activeLine) WaveGreen else WaveMuted,
                            fontSize = if (index == activeLine) 20.sp else 16.sp,
                            fontWeight = if (index == activeLine)
                                FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSeek(time) }
                                .padding(vertical = 10.dp)
                        )
                    }
                }

                plainLyrics != null -> Column(
                    Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        plainLyrics!!,
                        color = WaveWhite,
                        fontSize = 16.sp,
                        lineHeight = 25.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Estas letras no tienen marcas de tiempo; no se pueden sincronizar línea por línea.",
                        color = WaveMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RecapCard(
    label: String,
    value: String,
    accent: Color
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(WaveSurface, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Text(label, color = WaveMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyState(
    title: String,
    message: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.LibraryMusic,
            contentDescription = null,
            tint = WaveGreen,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(title, color = WaveWhite, fontSize = 19.sp)
        Text(message, color = WaveMuted, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        Button(onClick = onClick) { Text(buttonText) }
    }
}

private fun formatTime(milliseconds: Long): String {
    val total = milliseconds.coerceAtLeast(0L) / 1000L
    return String.format(
        Locale.getDefault(),
        "%d:%02d",
        total / 60L,
        total % 60L
    )
}
