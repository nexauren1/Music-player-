package com.nexauren.musicplayer

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.viewModels
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val playerViewModel by viewModels<PlayerViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            NexaMusicRoot(playerViewModel)
        }
    }
}

private enum class AppScreen(val label: String) {
    HOME("Home"),
    LIBRARY("Library"),
    FAVORITES("Favorites"),
    MOST_PLAYED("Most played"),
    EQUALIZER("Equalizer"),
    SETTINGS("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NexaMusicRoot(vm: PlayerViewModel) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val songs by vm.songs.collectAsState()
    val currentSong by vm.currentSong.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val position by vm.position.collectAsState()
    val duration by vm.duration.collectAsState()
    vm.libraryVersion.collectAsState()

    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var topMenu by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var nowPlaying by rememberSaveable { mutableStateOf(false) }
    var darkMode by rememberSaveable { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updating by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }

    val audioPermission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.scan() }

    LaunchedEffect(Unit) {
        val requested = mutableListOf(audioPermission)
        if (Build.VERSION.SDK_INT >= 33) {
            requested += Manifest.permission.POST_NOTIFICATIONS
        }

        val missing = requested.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                it
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            vm.scan()
        }
    }

    val filteredSongs = remember(songs, query) {
        if (query.isBlank()) songs else songs.filter {
            it.title.contains(query, true) ||
                it.artist.contains(query, true) ||
                it.album.contains(query, true)
        }
    }

    val drawerState = androidx.compose.material3.rememberDrawerState(
        androidx.compose.material3.DrawerValue.Closed
    )

    LaunchedEffect(drawerOpen) {
        if (drawerOpen) drawerState.open() else drawerState.close()
    }

    BackHandler(enabled = nowPlaying) {
        nowPlaying = false
    }

    MaterialTheme(
        colorScheme = if (darkMode) darkColors() else lightColors()
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(Modifier.height(28.dp))
                    Text(
                        "NEXA MUSIC",
                        modifier = Modifier.padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))

                    listOf(
                        AppScreen.HOME to Icons.Filled.Home,
                        AppScreen.LIBRARY to Icons.Filled.LibraryMusic,
                        AppScreen.FAVORITES to Icons.Filled.Favorite,
                        AppScreen.MOST_PLAYED to Icons.Filled.BarChart,
                        AppScreen.EQUALIZER to Icons.Filled.Tune,
                        AppScreen.SETTINGS to Icons.Filled.Settings
                    ).forEach { item ->
                        NavigationDrawerItem(
                            label = { Text(item.first.label) },
                            icon = { Icon(item.second, null) },
                            selected = screen == item.first,
                            onClick = {
                                screen = item.first
                                drawerOpen = false
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Version " + BuildConfig.VERSION_NAME,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets.statusBars,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                if (screen == AppScreen.HOME) "Nexa Music" else screen.label,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { drawerOpen = true }) {
                                Icon(Icons.Filled.Menu, "Menu")
                            }
                        },
                        actions = {
                            Box {
                                IconButton(onClick = { topMenu = true }) {
                                    Icon(Icons.Filled.MoreVert, "More")
                                }
                                DropdownMenu(
                                    expanded = topMenu,
                                    onDismissRequest = { topMenu = false }
                                ) {
                                    TextButton(onClick = {
                                        topMenu = false
                                        screen = AppScreen.EQUALIZER
                                    }) { Text("Equalizer") }
                                    TextButton(onClick = {
                                        topMenu = false
                                        screen = AppScreen.SETTINGS
                                    }) { Text("Settings") }
                                    TextButton(onClick = {
                                        topMenu = false
                                        activity?.lifecycleScope?.launch {
                                            updateInfo = UpdateManager.check(context)
                                        }
                                    }) { Text("Check for updates") }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                },
                bottomBar = {
                    Column {
                        currentSong?.let {
                            MiniPlayer(
                                song = it,
                                playing = playing,
                                onOpen = { nowPlaying = true },
                                onPlayPause = vm::togglePlayPause,
                                onNext = vm::next
                            )
                        }

                        NavigationBar(windowInsets = WindowInsets.navigationBars) {
                            val items = listOf(
                                AppScreen.HOME to Icons.Filled.Home,
                                AppScreen.LIBRARY to Icons.Filled.LibraryMusic,
                                AppScreen.FAVORITES to Icons.Filled.Favorite,
                                AppScreen.MOST_PLAYED to Icons.Filled.BarChart
                            )
                            items.forEach { item ->
                                NavigationBarItem(
                                    selected = screen == item.first,
                                    onClick = { screen = item.first },
                                    icon = { Icon(item.second, null) },
                                    label = {
                                        Text(item.first.label.split(" ").first())
                                    }
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                when (screen) {
                    AppScreen.HOME -> HomeScreen(
                        modifier = Modifier.padding(padding),
                        vm = vm,
                        songs = filteredSongs,
                        query = query,
                        onQueryChange = { query = it },
                        onOpenNowPlaying = { nowPlaying = true }
                    )
                    AppScreen.LIBRARY -> LibraryScreen(
                        modifier = Modifier.padding(padding),
                        vm = vm,
                        songs = filteredSongs,
                        query = query,
                        onQueryChange = { query = it }
                    )
                    AppScreen.FAVORITES -> SongListScreen(
                        modifier = Modifier.padding(padding),
                        vm = vm,
                        title = "Your favorites",
                        songs = vm.favorites()
                    )
                    AppScreen.MOST_PLAYED -> SongListScreen(
                        modifier = Modifier.padding(padding),
                        vm = vm,
                        title = "Most played",
                        songs = vm.mostPlayed()
                    )
                    AppScreen.EQUALIZER -> EqualizerScreen(
                        modifier = Modifier.padding(padding),
                        audioSessionId = vm.audioSessionId(),
                        onBack = { screen = AppScreen.HOME }
                    )
                    AppScreen.SETTINGS -> SettingsScreen(
                        modifier = Modifier.padding(padding),
                        darkMode = darkMode,
                        onDarkMode = { darkMode = it },
                        onScan = { vm.scan() },
                        onCheckUpdates = {
                            activity?.lifecycleScope?.launch {
                                updateInfo = UpdateManager.check(context)
                            }
                        }
                    )
                }
            }
        }

        if (nowPlaying && currentSong != null) {
            NowPlayingSheet(
                song = currentSong!!,
                vm = vm,
                playing = playing,
                position = position,
                duration = duration,
                onDismiss = { nowPlaying = false },
                onEqualizer = {
                    nowPlaying = false
                    screen = AppScreen.EQUALIZER
                }
            )
        }

        updateInfo?.let { info ->
            UpdateDialog(
                info = info,
                progress = progress,
                updating = updating,
                onDismiss = { if (!updating) updateInfo = null },
                onInstall = {
                    updating = true
                    activity?.lifecycleScope?.launch {
                        runCatching {
                            UpdateManager.downloadAndInstall(context, info) {
                                progress = it
                            }
                        }
                        updating = false
                        updateInfo = null
                    }
                }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    vm: PlayerViewModel,
    songs: List<Song>,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    val currentSong by vm.currentSong.collectAsState()
    val playing by vm.isPlaying.collectAsState()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search songs, artists, albums") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )

        Spacer(Modifier.height(18.dp))

        if (currentSong != null) {
            HeroNowPlaying(currentSong!!, playing, onOpenNowPlaying)
        } else {
            WelcomeCard(songs.size) { vm.scan() }
        }

        Spacer(Modifier.height(22.dp))
        SectionHeader("Recently added")
        SongCarousel(songs.take(12), vm)

        Spacer(Modifier.height(22.dp))
        SectionHeader("Favorites")
        val favorites = vm.favorites()
        if (favorites.isEmpty()) {
            EmptyHint("Heart songs to build your favorites.")
        } else {
            SongCarousel(favorites.take(10), vm)
        }

        Spacer(Modifier.height(22.dp))
        SectionHeader("Most played")
        val mostPlayed = vm.mostPlayed()
        if (mostPlayed.isEmpty()) {
            EmptyHint("Your listening history will appear here.")
        } else {
            SongCarousel(mostPlayed.take(10), vm)
        }

        Spacer(Modifier.height(22.dp))
        AndroidAdView()
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun LibraryScreen(
    modifier: Modifier,
    vm: PlayerViewModel,
    songs: List<Song>,
    query: String,
    onQueryChange: (String) -> Unit
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(onClick = {}, label = { Text(songs.size.toString() + " songs") })
            AssistChip(
                onClick = {},
                label = { Text(songs.map { it.albumId }.distinct().size.toString() + " albums") }
            )
            AssistChip(
                onClick = {},
                label = { Text(songs.map { it.artist }.distinct().size.toString() + " artists") }
            )
        }

        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Search your library") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp)
        )

        SongListScreen(
            modifier = Modifier.weight(1f),
            vm = vm,
            title = "All songs",
            songs = songs
        )
    }
}

@Composable
private fun SongListScreen(
    modifier: Modifier,
    vm: PlayerViewModel,
    title: String,
    songs: List<Song>
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(songs.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (songs.isEmpty()) {
            EmptyHint("Nothing here yet.")
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    bottom = 120.dp
                )
            ) {
                itemsIndexed(songs, key = { _, song -> song.id }) { _, song ->
                    SongRow(song, vm, title == "Most played")
                }
            }
        }
    }
}

@Composable
private fun SongCarousel(songs: List<Song>, vm: PlayerViewModel) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(songs, key = { it.id }) { song ->
            Card(
                Modifier
                    .width(170.dp)
                    .clickable { vm.play(song) },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(Modifier.padding(10.dp)) {
                    Artwork(song, Modifier
                        .fillMaxWidth()
                        .height(150.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        song.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        song.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, vm: PlayerViewModel, showPlays: Boolean) {
    var menu by rememberSaveable(song.id) { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { vm.play(song) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(song, Modifier.size(58.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
            val secondary = if (showPlays) {
                song.artist + " • " + vm.playCount(song) + " plays"
            } else {
                song.artist + " • " + song.album
            }
            Text(
                secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = { vm.favorite(song) }) {
            Icon(
                if (vm.isFavorite(song)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                "Favorite"
            )
        }

        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, "More")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                TextButton(onClick = {
                    vm.play(song)
                    menu = false
                }) { Text("Play now") }
                TextButton(onClick = {
                    vm.startSleepTimer(15)
                    menu = false
                }) { Text("Sleep timer 15 min") }
                TextButton(onClick = {
                    vm.favorite(song)
                    menu = false
                }) {
                    Text(if (vm.isFavorite(song)) "Remove favorite" else "Add favorite")
                }
            }
        }
    }
}

@Composable
private fun HeroNowPlaying(song: Song, playing: Boolean, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.99f,
        targetValue = 1.01f,
        animationSpec = infiniteRepeatable(
            tween(1500),
            RepeatMode.Reverse
        ),
        label = "scale"
    )

    Card(
        Modifier
            .fillMaxWidth()
            .scale(if (playing) pulse.value else 1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF6C4CF1),
                            Color(0xFFE94B8F),
                            Color(0xFF2B8FFF)
                        )
                    ),
                    RoundedCornerShape(30.dp)
                )
                .padding(22.dp)
        ) {
            Column {
                Text(
                    "NOW PLAYING",
                    color = Color.White.copy(alpha = .78f),
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(song, Modifier.size(112.dp))
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            song.title,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2
                        )
                        Text(song.artist, color = Color.White.copy(alpha = .85f))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (playing) "Playing" else "Paused",
                            color = Color.White.copy(alpha = .8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeCard(count: Int, onRefresh: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "Your music. Your way.",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                count.toString() + " tracks found on this device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("Scan library")
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    song: Song,
    playing: Boolean,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        tonalElevation = 5.dp
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Artwork(song, Modifier.size(48.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    song.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    null
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.SkipNext, null)
            }
            IconButton(onClick = onOpen) {
                Icon(Icons.Filled.ExpandLess, null)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingSheet(
    song: Song,
    vm: PlayerViewModel,
    playing: Boolean,
    position: Long,
    duration: Long,
    onDismiss: () -> Unit,
    onEqualizer: () -> Unit
) {
    val volume by vm.volume.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        var moreMenu by remember { mutableStateOf(false) }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Now Playing",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEqualizer) {
                    Icon(Icons.Filled.Tune, "Equalizer")
                }
                Box {
                    IconButton(onClick = { moreMenu = true }) {
                        Icon(Icons.Filled.MoreVert, "More")
                    }
                    DropdownMenu(
                        expanded = moreMenu,
                        onDismissRequest = { moreMenu = false }
                    ) {
                        TextButton(onClick = {
                            vm.startSleepTimer(15)
                            moreMenu = false
                        }) { Text("Sleep 15 min") }
                        TextButton(onClick = {
                            vm.startSleepTimer(30)
                            moreMenu = false
                        }) { Text("Sleep 30 min") }
                        TextButton(onClick = {
                            vm.favorite(song)
                            moreMenu = false
                        }) {
                            Text(
                                if (vm.isFavorite(song))
                                    "Remove favorite"
                                else
                                    "Add favorite"
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Artwork(song, Modifier
                .fillMaxWidth()
                .height(320.dp))
            Spacer(Modifier.height(18.dp))

            Text(
                song.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(10.dp))
            Slider(
                value = if (duration > 0) {
                    position.toFloat().coerceIn(0f, duration.toFloat())
                } else {
                    0f
                },
                onValueChange = { vm.seekTo(it.toLong()) },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat()
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(position))
                Text(formatDuration(duration))
            }

            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.setVolume(0f) }) {
                    Icon(Icons.Filled.VolumeDown, "Mute")
                }
                Slider(
                    value = volume,
                    onValueChange = vm::setVolume,
                    modifier = Modifier.weight(1f),
                    valueRange = 0f..1f
                )
                IconButton(onClick = { vm.setVolume(1f) }) {
                    Icon(Icons.Filled.VolumeUp, "Max volume")
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = vm::toggleShuffle) {
                    Icon(Icons.Filled.Shuffle, "Shuffle")
                }
                IconButton(onClick = vm::previous) {
                    Icon(Icons.Filled.SkipPrevious, null, Modifier.size(36.dp))
                }
                Surface(
                    Modifier.size(68.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    IconButton(onClick = vm::togglePlayPause) {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
                IconButton(onClick = vm::next) {
                    Icon(Icons.Filled.SkipNext, null, Modifier.size(36.dp))
                }
                IconButton(onClick = vm::toggleRepeat) {
                    Icon(Icons.Filled.Repeat, "Repeat")
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { vm.startSleepTimer(15) },
                    label = { Text("15 min") }
                )
                AssistChip(
                    onClick = { vm.startSleepTimer(30) },
                    label = { Text("30 min") }
                )
                AssistChip(
                    onClick = { vm.startSleepTimer(60) },
                    label = { Text("60 min") }
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun EqualizerScreen(
    modifier: Modifier,
    audioSessionId: Int,
    onBack: () -> Unit
) {
    val eq = remember { EqualizerController() }
    var attached by remember { mutableStateOf(false) }
    var levels by remember { mutableStateOf(List(5) { 0f }) }

    DisposableEffect(audioSessionId) {
        eq.attach(audioSessionId)
        attached = audioSessionId > 0
        onDispose {
            eq.release()
            attached = false
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text(
                "Equalizer",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            if (attached)
                "Live audio effect connected."
            else
                "Play a song first to connect the equalizer.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Flat", "Bass", "Treble", "Vocal").forEachIndexed { index, label ->
                AssistChip(
                    onClick = { if (attached) eq.setPreset(index.toShort()) },
                    label = { Text(label) }
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        levels.forEachIndexed { index, value ->
            Column {
                Text("Band " + (index + 1), fontWeight = FontWeight.SemiBold)
                Slider(
                    value = value,
                    onValueChange = {
                        levels = levels.toMutableList().also { list ->
                            list[index] = it
                        }
                        val range = (eq.upperBound() - eq.lowerBound()).coerceAtLeast(1)
                        val level = eq.lowerBound() + (range * it).toInt()
                        eq.setBand(index, level)
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Card {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "Audio effects",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Equalizer presets, background playback and animated playback controls.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    darkMode: Boolean,
    onDarkMode: (Boolean) -> Unit,
    onScan: () -> Unit,
    onCheckUpdates: () -> Unit
) {
    var autoScan by rememberSaveable { mutableStateOf(true) }
    var notifications by rememberSaveable { mutableStateOf(true) }
    var premiumDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp,
            vertical = 10.dp
        )
    ) {
        item {
            SettingsSection("Playback") {
                SettingSwitch(
                    "Auto-scan new music",
                    "Refresh the library when the app opens.",
                    autoScan
                ) {
                    autoScan = it
                    if (it) onScan()
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                    Text("Scan library now")
                }
            }
        }

        item {
            SettingsSection("Appearance") {
                SettingSwitch(
                    "Dark mode",
                    "Use the darker Nexa Music theme.",
                    darkMode,
                    onDarkMode
                )
            }
        }

        item {
            SettingsSection("Notifications") {
                SettingSwitch(
                    "Update notifications",
                    "Notify when a newer app build is available.",
                    notifications
                ) {
                    notifications = it
                }
            }
        }

        item {
            SettingsSection("Updates") {
                Button(onClick = onCheckUpdates, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Update, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Check for updates")
                }
            }
        }

        item {
            SettingsSection("Premium") {
                Text(
                    "Remove ads and unlock future premium features.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { premiumDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Premium")
                }
            }
        }

        item {
            SettingsSection("About") {
                Text(
                    "Nexa Music " + BuildConfig.VERSION_NAME,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Offline-first music player powered by AndroidX Media3.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (premiumDialog) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { premiumDialog = false },
            title = { Text("Nexa Music Premium") },
            text = {
                Text(
                    "PayPal is prepared through a hosted checkout URL. " +
                        "The PayPal client secret is not embedded in the APK."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(BuildConfig.PAYPAL_CHECKOUT_URL)
                            )
                        )
                    }
                    premiumDialog = false
                }) {
                    Text("Open checkout")
                }
            },
            dismissButton = {
                TextButton(onClick = { premiumDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(bottom = 18.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun UpdateDialog(
    info: UpdateInfo,
    progress: Int,
    updating: Boolean,
    onDismiss: () -> Unit,
    onInstall: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update available") },
        text = {
            Column {
                Text("Nexa Music " + info.versionName)
                Spacer(Modifier.height(8.dp))
                Text(
                    info.changelog,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (updating) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        progress.toString() + "%",
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onInstall,
                enabled = !updating
            ) {
                Text("Download & install")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !updating
            ) {
                Text("Later")
            }
        }
    )
}

@Composable
private fun Artwork(song: Song, modifier: Modifier) {
    val context = LocalContext.current
    val repository = remember(context) { MusicRepository(context) }
    var bitmap by remember(song.uri) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }

    LaunchedEffect(song.uri) {
        bitmap = repository.loadArtwork(song.uri)
    }

    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF6C4CF1), Color(0xFFE94B8F))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Album art",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: Text(
            song.title.take(1).uppercase(),
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        Modifier.padding(bottom = 8.dp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun EmptyHint(text: String) {
    Card(Modifier.fillMaxWidth()) {
        Text(
            text,
            Modifier.padding(20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AndroidAdView() {
    AndroidView(
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = "ca-app-pub-3940256099942544/6300978111"
                loadAd(AdRequest.Builder().build())
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
    )
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
