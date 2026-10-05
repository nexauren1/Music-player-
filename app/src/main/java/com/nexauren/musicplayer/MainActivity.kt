package com.musicplayer.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val playerViewModel by viewModels<PlayerViewModel>()
    private val premiumRepository by lazy { PremiumRepository(this) }
    private var premiumEvent by mutableIntStateOf(0)
    private var premiumMessage by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handlePayPalIntent(intent)
        setContent { MusicPlayerRoot(playerViewModel, premiumEvent, premiumMessage) }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePayPalIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        ApkInstaller.resumeIfPending(this)
        lifecycleScope.launch {
            val account = FirebaseAccountRepository(this@MainActivity).currentAccount()
            val local = premiumRepository.loadLocal()
            if (account != null && local.verified && local.plan != PremiumPlan.NONE) {
                val result = PayPalVerifier.verify(
                    uid = account.uid,
                    plan = local.plan,
                    orderId = local.orderId,
                    subscriptionId = local.subscriptionId
                )
                result.onSuccess {
                    if (it.verified) {
                        premiumRepository.setVerifiedFromWorker(
                            it.plan,
                            it.expiresAtMillis,
                            it.orderId,
                            it.subscriptionId
                        )
                    } else {
                        premiumRepository.clearVerifiedPremium()
                        premiumEvent++
                        premiumMessage = "Premium is no longer active."
                    }
                }
            }
        }
    }

    private fun handlePayPalIntent(intent: Intent?) {
        if (intent?.scheme != "musicplayer" || intent.host != "paypal") return

        if (intent.path == "/cancel") {
            premiumMessage = "Payment cancelled. Premium was not activated."
            premiumEvent++
            return
        }

        if (intent.path != "/success") return

        val account = FirebaseAccountRepository(this).currentAccount()
        if (account == null) {
            premiumMessage = "Sign in again so Music Player can verify this purchase."
            premiumEvent++
            return
        }

        val plan = when (intent.getStringExtra("plan") ?: intent.data?.getQueryParameter("plan")) {
            "quarterly" -> PremiumPlan.QUARTERLY
            "lifetime" -> PremiumPlan.LIFETIME
            else -> PremiumPlan.NONE
        }
        val orderId = intent.getStringExtra("orderId")
            ?: intent.data?.getQueryParameter("orderId")
        val subscriptionId = intent.getStringExtra("subscriptionId")
            ?: intent.data?.getQueryParameter("subscriptionId")

        lifecycleScope.launch {
            PayPalVerifier.verify(account.uid, plan, orderId, subscriptionId)
                .onSuccess {
                    if (it.verified) {
                        premiumRepository.setVerifiedFromWorker(
                            it.plan,
                            it.expiresAtMillis,
                            it.orderId,
                            it.subscriptionId
                        )
                        premiumMessage = "Premium activated. All premium features are now unlocked."
                    } else {
                        premiumMessage = "Payment returned, but PayPal has not confirmed Premium yet."
                    }
                    premiumEvent++
                }
                .onFailure {
                    premiumMessage = "Premium verification failed: " + (it.message ?: "Unknown error")
                    premiumEvent++
                }
        }
    }
}

private enum class AppScreen(val label: String) {
    HOME("Home"),
    LIBRARY("Library"),
    FAVORITES("Favorites"),
    MOST_PLAYED("Most played"),
    EQUALIZER("Equalizer"),
    PREMIUM("Premium"),
    SETTINGS("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicPlayerRoot(
    vm: PlayerViewModel,
    premiumEvent: Int,
    premiumMessage: String
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val songs by vm.songs.collectAsState()
    val currentSong by vm.currentSong.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val position by vm.position.collectAsState()
    val duration by vm.duration.collectAsState()

    val premiumRepo = remember { PremiumRepository(context) }
    val accountRepo = remember { FirebaseAccountRepository(context) }

    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val storedAppearance = remember { AppearanceStore.load(context) }
    var darkMode by rememberSaveable { mutableStateOf(storedAppearance.darkMode) }
    var themeName by rememberSaveable { mutableStateOf(storedAppearance.theme.name) }
    var backgroundName by rememberSaveable { mutableStateOf(storedAppearance.background.name) }
    var showAppearanceSetup by rememberSaveable { mutableStateOf(!storedAppearance.configured) }
    var nowPlaying by rememberSaveable { mutableStateOf(false) }
    var accountDialog by remember { mutableStateOf(false) }
    var premiumRefresh by remember { mutableIntStateOf(0) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updating by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableIntStateOf(0) }
    var updateError by remember { mutableStateOf("") }

    val appTheme = runCatching { AppThemeStyle.valueOf(themeName) }.getOrDefault(AppThemeStyle.VIOLET)
    val appBackground = runCatching { AppBackgroundStyle.valueOf(backgroundName) }.getOrDefault(AppBackgroundStyle.GRADIENT)

    var notificationsAllowed by remember {
        mutableStateOf(NotificationHelper.areNotificationsEnabled(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationsAllowed = NotificationHelper.areNotificationsEnabled(context)
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        vm.scan()
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        val audioPermission =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                audioPermission
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            audioPermissionLauncher.launch(audioPermission)
        } else {
            vm.scan()
            if (Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        premiumRepo.syncFromFirebase()
        delay(1500)
        if (updateInfo == null) {
            updateInfo = UpdateManager.check(context)
        }
    }

    LaunchedEffect(premiumEvent) {
        if (premiumEvent > 0) {
            premiumRefresh++
            screen = AppScreen.PREMIUM
            Toast.makeText(context, premiumMessage, Toast.LENGTH_LONG).show()
        }
    }

    BackHandler(enabled = nowPlaying) {
        nowPlaying = false
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

    MaterialTheme(colorScheme = themeColors(appTheme, darkMode)) {
        AppBackdrop(style = appBackground, dark = darkMode, theme = appTheme) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(Modifier.height(28.dp))
                    Row(
                        Modifier.padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            Modifier.size(46.dp),
                            shape = RoundedCornerShape(15.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Music Player", fontWeight = FontWeight.ExtraBold)
                            Text(
                                "Local audio • offline-first",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    listOf(
                        AppScreen.HOME to Icons.Filled.Home,
                        AppScreen.LIBRARY to Icons.Filled.LibraryMusic,
                        AppScreen.FAVORITES to Icons.Filled.Favorite,
                        AppScreen.MOST_PLAYED to Icons.Filled.BarChart,
                        AppScreen.EQUALIZER to Icons.Filled.Tune,
                        AppScreen.PREMIUM to Icons.Filled.Star,
                        AppScreen.SETTINGS to Icons.Filled.Settings
                    ).forEach { (item, icon) ->
                        NavigationDrawerItem(
                            label = { Text(item.label) },
                            icon = { Icon(icon, null) },
                            selected = screen == item,
                            onClick = {
                                screen = item
                                drawerOpen = false
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    Text(
                        "v" + BuildConfig.VERSION_NAME,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.statusBars,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                screen.label,
                                fontWeight = FontWeight.ExtraBold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { drawerOpen = true }) {
                                Icon(Icons.Filled.Menu, "Menu")
                            }
                        },
                        actions = {
                            IconButton(onClick = { accountDialog = true }) {
                                Icon(
                                    if (accountRepo.currentAccount() != null)
                                        Icons.Filled.AccountCircle
                                    else
                                        Icons.Filled.Person,
                                    "Account"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                },
                bottomBar = {
                    Column {
                        currentSong?.let { song ->
                            MiniPlayer(
                                song = song,
                                playing = playing,
                                position = position,
                                duration = duration,
                                onOpen = { nowPlaying = true },
                                onPlayPause = vm::togglePlayPause,
                                onNext = vm::next
                            )
                        }

                        NavigationBar(windowInsets = WindowInsets.navigationBars) {
                            val bottom = listOf(
                                AppScreen.HOME to Icons.Filled.Home,
                                AppScreen.LIBRARY to Icons.Filled.LibraryMusic,
                                AppScreen.FAVORITES to Icons.Filled.Favorite,
                                AppScreen.MOST_PLAYED to Icons.Filled.BarChart
                            )
                            bottom.forEach { (item, icon) ->
                                NavigationBarItem(
                                    selected = screen == item,
                                    onClick = { screen = item },
                                    icon = { Icon(icon, null) },
                                    label = { Text(item.label.split(' ').first()) }
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
                        premium = premiumRepo.isPremium(),
                        onQueryChange = { query = it }
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
                        title = "Favorites",
                        songs = vm.favorites(),
                        showPlays = false
                    )
                    AppScreen.MOST_PLAYED -> SongListScreen(
                        modifier = Modifier.padding(padding),
                        vm = vm,
                        title = "Most played",
                        songs = vm.mostPlayed(),
                        showPlays = true
                    )
                    AppScreen.EQUALIZER -> EqualizerScreen(
                        modifier = Modifier.padding(padding),
                        audioSessionId = vm.audioSessionId(),
                        premiumRepo = premiumRepo,
                        onBack = { screen = AppScreen.HOME }
                    )
                    AppScreen.PREMIUM -> PremiumScreen(
                        modifier = Modifier.padding(padding),
                        account = accountRepo.currentAccount(),
                        premium = premiumRepo.loadLocal(),
                        previewUses = premiumRepo.remainingPreviewUses(),
                        onAccount = { accountDialog = true },
                        onRefresh = {
                            activity?.let { host ->
                                host.lifecycleScope.launch {
                                    premiumRepo.syncFromFirebase()
                                    premiumRefresh++
                                }
                            }
                        },
                        onPurchase = { plan ->
                            val account = accountRepo.currentAccount()
                            if (account == null) {
                                accountDialog = true
                            } else {
                                val workerBase = BuildConfig.PAYPAL_WORKER_URL.trim().trimEnd('/')
                                val fallbackUrl = when (plan) {
                                    PremiumPlan.QUARTERLY -> BuildConfig.PAYPAL_QUARTERLY_URL
                                    PremiumPlan.LIFETIME -> BuildConfig.PAYPAL_LIFETIME_URL
                                    PremiumPlan.NONE -> ""
                                }
                                val checkoutUrl = when (plan) {
                                    PremiumPlan.QUARTERLY ->
                                        if (workerBase.isNotBlank()) {
                                            workerBase + "/paypal/checkout/quarterly?uid=" +
                                                android.net.Uri.encode(account.uid)
                                        } else fallbackUrl
                                    PremiumPlan.LIFETIME ->
                                        if (workerBase.isNotBlank()) {
                                            workerBase + "/paypal/checkout/lifetime?uid=" +
                                                android.net.Uri.encode(account.uid)
                                        } else fallbackUrl
                                    PremiumPlan.NONE -> ""
                                }

                                if (checkoutUrl.isBlank() || checkoutUrl.startsWith("https://example.com")) {
                                    Toast.makeText(
                                        context,
                                        "PayPal checkout is not configured yet.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(checkoutUrl))
                                        )
                                    }.onFailure {
                                        Toast.makeText(
                                            context,
                                            "Unable to open PayPal checkout.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        },
                        quarterlyCheckoutReady = BuildConfig.PAYPAL_WORKER_URL.isNotBlank() ||
                            (BuildConfig.PAYPAL_QUARTERLY_URL.isNotBlank() &&
                                !BuildConfig.PAYPAL_QUARTERLY_URL.startsWith("https://example.com")),
                        lifetimeCheckoutReady = BuildConfig.PAYPAL_WORKER_URL.isNotBlank() ||
                            (BuildConfig.PAYPAL_LIFETIME_URL.isNotBlank() &&
                                !BuildConfig.PAYPAL_LIFETIME_URL.startsWith("https://example.com")),
                        refreshToken = premiumRefresh
                    )
                    AppScreen.SETTINGS -> SettingsScreen(
                        modifier = Modifier.padding(padding),
                        vm = vm,
                        darkMode = darkMode,
                        onDarkMode = {
                            darkMode = it
                            AppearanceStore.save(context, appTheme, appBackground, it)
                        },
                        themeName = themeName,
                        backgroundName = backgroundName,
                        onThemeChange = {
                            themeName = it.name
                            AppearanceStore.save(context, it, appBackground, darkMode)
                        },
                        onBackgroundChange = {
                            backgroundName = it.name
                            AppearanceStore.save(context, appTheme, it, darkMode)
                        },
                        onOpenPremium = { screen = AppScreen.PREMIUM },
                        onAccount = { accountDialog = true },
                        onCheckUpdates = {
                            activity?.let { host ->
                                host.lifecycleScope.launch {
                                    updateInfo = UpdateManager.check(context)
                                }
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

        if (accountDialog) {
            AccountDialog(
                activity = activity,
                repo = accountRepo,
                onDismiss = { accountDialog = false },
                onSigned = {
                    accountDialog = false
                    activity?.lifecycleScope?.launch {
                        premiumRepo.syncFromFirebase()
                        premiumRefresh++
                    }
                }
            )
        }

        updateInfo?.let { info ->
            UpdateDialog(
                info = info,
                progress = updateProgress,
                updating = updating,
                onDismiss = { if (!updating) updateInfo = null },
                onInstall = {
                    updating = true
                    activity?.let { host ->
                        host.lifecycleScope.launch {
                            runCatching {
                                UpdateManager.downloadAndInstall(context, info) {
                                    updateProgress = it
                                }
                            }
                            updating = false
                            updateInfo = null
                        }
                    }
                }
            )
        }

        if (showAppearanceSetup) {
            AppearanceSetupDialog(
                theme = appTheme,
                background = appBackground,
                darkMode = darkMode,
                onThemeChange = { themeName = it.name },
                onBackgroundChange = { backgroundName = it.name },
                onDarkModeChange = { darkMode = it },
                onSave = {
                    AppearanceStore.save(context, appTheme, appBackground, darkMode)
                    showAppearanceSetup = false
                }
            )
        }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    vm: PlayerViewModel,
    songs: List<Song>,
    query: String,
    premium: Boolean,
    onQueryChange: (String) -> Unit
) {
    val current by vm.currentSong.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val recent = remember(songs) {
        songs.sortedByDescending { it.dateAddedMillis }.take(6)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SearchBar(query, onQueryChange) }
        item { SectionTitle("Now playing", "Live status • view only") }
        item { NowPlayingInfoCard(current, playing) }
        item { SectionTitle("Recently added", "Latest files • view only") }

        item {
            if (recent.isEmpty()) {
                EmptyCard("No recently added music yet.")
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recent, key = { it.id }) { song ->
                        RecentAddedCard(song)
                    }
                }
            }
        }

        item {
            SectionTitle(
                "All songs",
                songs.size.toString() + " tracks • tap a track to play"
            )
        }

        if (songs.isEmpty()) {
            item { EmptyCard("No songs found. Scan your library from Settings.") }
        } else {
            itemsIndexed(songs, key = { _, song -> song.id }) { _, song ->
                SongRow(song, vm, showPlays = false)
            }
        }

        if (!premium) {
            item { BannerAd() }
        } else {
            item {
                PremiumHomeBadge()
            }
        }
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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SearchBar(query, onQueryChange) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Tracks", songs.size.toString(), Modifier.weight(1f))
                StatCard("Albums", songs.map { it.albumId }.distinct().size.toString(), Modifier.weight(1f))
                StatCard("Artists", songs.map { it.artist }.distinct().size.toString(), Modifier.weight(1f))
            }
        }
        item { SectionTitle("All music", "Your complete local library") }
        if (songs.isEmpty()) {
            item { EmptyCard("No songs found.") }
        } else {
            itemsIndexed(songs, key = { _, song -> song.id }) { _, song ->
                SongRow(song, vm, showPlays = false)
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search title, artist or album") },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        singleLine = true,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NowPlayingInfoCard(song: Song?, playing: Boolean) {
    val transition = rememberInfiniteTransition(label = "home_now_playing")
    val pulse by transition.animateFloat(0.88f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse")

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ),
                    RoundedCornerShape(30.dp)
                )
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song == null) {
                    Surface(
                        Modifier.size(72.dp).graphicsLayer {
                            alpha = pulse
                            scaleX = pulse
                            scaleY = pulse
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Nothing playing", fontWeight = FontWeight.ExtraBold)
                        Text("Start a track from All songs.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Artwork(
                        song,
                        Modifier.size(82.dp).graphicsLayer {
                            scaleX = if (playing) pulse else 1f
                            scaleY = if (playing) pulse else 1f
                        }
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.ExtraBold)
                        Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimatedBars(playing)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (playing) "Live playback" else "Paused",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(if (playing) "Playing now" else "Paused") },
                            leadingIcon = { Icon(Icons.Filled.CheckCircle, null) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentAddedCard(song: Song) {
    Card(
        Modifier.width(164.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(10.dp)) {
            Artwork(song, Modifier.fillMaxWidth().height(140.dp))
            Spacer(Modifier.height(9.dp))
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Recently added",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SongListScreen(
    modifier: Modifier,
    vm: PlayerViewModel,
    title: String,
    songs: List<Song>,
    showPlays: Boolean
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item { SectionTitle(title, songs.size.toString() + " tracks") }
        if (songs.isEmpty()) {
            item { EmptyCard("Nothing here yet.") }
        } else {
            itemsIndexed(songs, key = { _, song -> song.id }) { _, song ->
                SongRow(song, vm, showPlays)
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, vm: PlayerViewModel, showPlays: Boolean) {
    var menu by rememberSaveable(song.id) { mutableStateOf(false) }

    Card(
        Modifier.fillMaxWidth().clickable { vm.play(song) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(song, Modifier.size(58.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                Text(
                    if (showPlays) song.artist + " • " + vm.playCount(song) + " plays"
                    else song.artist + " • " + song.album,
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
                    TextButton(onClick = { vm.play(song); menu = false }) { Text("Play now") }
                    TextButton(onClick = { vm.startSleepTimer(15); menu = false }) { Text("Sleep 15 min") }
                    TextButton(onClick = { vm.favorite(song); menu = false }) {
                        Text(if (vm.isFavorite(song)) "Remove favorite" else "Add favorite")
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    song: Song,
    playing: Boolean,
    position: Long,
    duration: Long,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            )
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Artwork(song, Modifier.size(56.dp))
                    if (playing) {
                        Surface(
                            Modifier.align(Alignment.BottomEnd).size(18.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(3.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.ExtraBold)
                    Text(
                        song.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onPlayPause) {
                    Surface(Modifier.size(42.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(9.dp)
                        )
                    }
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, null)
                }
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
    val transition = rememberInfiniteTransition(label = "now_playing_effects")
    val pulse by transition.animateFloat(
        0.94f,
        1.02f,
        infiniteRepeatable(tween(950), RepeatMode.Reverse),
        label = "artPulse"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        Modifier.fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                ),
                                RoundedCornerShape(32.dp)
                            )
                            .padding(18.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "NOW PLAYING",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        "Your sound. Your space.",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                IconButton(onClick = onEqualizer) {
                                    Icon(Icons.Filled.Tune, "Equalizer")
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Box(
                                Modifier.fillMaxWidth().height(320.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    Modifier.size(300.dp).alpha(if (playing) 0.34f else 0.22f),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary
                                ) {}
                                Artwork(
                                    song,
                                    Modifier.fillMaxWidth().height(292.dp).graphicsLayer {
                                        scaleX = if (playing) pulse else 1f
                                        scaleY = if (playing) pulse else 1f
                                    }
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        song.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                AnimatedBars(playing)
                            }
                            Slider(
                                value = if (duration > 0) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
                                onValueChange = { vm.seekTo(it.toLong()) },
                                valueRange = 0f..duration.coerceAtLeast(1L).toFloat()
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(formatDuration(position), style = MaterialTheme.typography.labelSmall)
                                Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.setVolume(0f) }) { Icon(Icons.Filled.VolumeDown, "Mute") }
                            Slider(value = volume, onValueChange = vm::setVolume, modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.setVolume(1f) }) { Icon(Icons.Filled.VolumeUp, "Max volume") }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = vm::toggleShuffle) { Icon(Icons.Filled.Shuffle, "Shuffle") }
                            IconButton(onClick = vm::previous) { Icon(Icons.Filled.SkipPrevious, null, Modifier.size(32.dp)) }
                            Surface(Modifier.size(72.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                                IconButton(onClick = vm::togglePlayPause) {
                                    Icon(
                                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }
                            IconButton(onClick = vm::next) { Icon(Icons.Filled.SkipNext, null, Modifier.size(32.dp)) }
                            IconButton(onClick = vm::toggleRepeat) { Icon(Icons.Filled.Repeat, "Repeat") }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = { vm.startSleepTimer(15) }, label = { Text("15 min") })
                            AssistChip(onClick = { vm.startSleepTimer(30) }, label = { Text("30 min") })
                            AssistChip(onClick = { vm.startSleepTimer(60) }, label = { Text("60 min") })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EqualizerScreen(
    modifier: Modifier,
    audioSessionId: Int,
    premiumRepo: PremiumRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val eq = remember { EqualizerController(context) }
    var attached by remember { mutableStateOf(false) }
    var levels by remember { mutableStateOf(List(5) { .5f }) }
    var message by remember { mutableStateOf("Effects are saved when you leave the page.") }
    val premiumActive = premiumRepo.isPremium()

    DisposableEffect(audioSessionId) {
        eq.attach(audioSessionId)
        attached = audioSessionId > 0
        if (attached) levels = eq.normalizedLevels().ifEmpty { List(5) { .5f } }
        onDispose {
            eq.release()
            attached = false
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                Column {
                    Text("Equalizer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text(if (attached) "Connected to the active player" else "Play a song first", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Presets", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(9.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("Flat", "Bass", "Treble", "Vocal", "Rock")) { label ->
                            AssistChip(
                                onClick = {
                                    if (!attached) return@AssistChip
                                    val freePreset = label == "Flat"
                                    if (freePreset || premiumActive || premiumRepo.consumePreviewUse()) {
                                        val index = listOf("Flat", "Bass", "Treble", "Vocal", "Rock").indexOf(label)
                                        eq.setPreset(index.toShort())
                                        levels = eq.normalizedLevels().ifEmpty { List(5) { .5f } }
                                        message = if (premiumActive || freePreset) {
                                            "Preset applied."
                                        } else {
                                            "Premium preview used. " + premiumRepo.remainingPreviewUses() + " preview(s) left."
                                        }
                                    } else {
                                        message = "The 3 free premium previews are finished. Unlock Premium for unlimited effects."
                                    }
                                },
                                label = { Text(label) },
                                leadingIcon = if (label != "Flat") {
                                    { Icon(Icons.Filled.Star, null) }
                                } else null
                            )
                        }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Bands" + if (premiumActive) "" else " • Premium", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    levels.forEachIndexed { index, value ->
                        Text("Band ${index + 1}", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = value,
                            enabled = attached && premiumActive,
                            onValueChange = {
                                if (!attached || !premiumActive) {
                                    message = "Band controls are Premium. Use the free presets to preview effects."
                                    return@Slider
                                }
                                levels = levels.toMutableList().also { list -> list[index] = it }
                                val range = (eq.upperBound() - eq.lowerBound()).coerceAtLeast(1)
                                val level = eq.lowerBound() + (range * it).toInt()
                                eq.setBand(index, level)
                            }
                        )
                    }
                }
            }
        }
        item {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PremiumScreen(
    modifier: Modifier,
    account: AccountSnapshot?,
    premium: PremiumSnapshot,
    previewUses: Int,
    onAccount: () -> Unit,
    onRefresh: () -> Unit,
    onPurchase: (PremiumPlan) -> Unit,
    quarterlyCheckoutReady: Boolean,
    lifetimeCheckoutReady: Boolean,
    refreshToken: Int
) {
    val premiumActive = premium.verified && when (premium.plan) {
        PremiumPlan.LIFETIME -> true
        PremiumPlan.QUARTERLY ->
            premium.expiresAtMillis == null || premium.expiresAtMillis > System.currentTimeMillis()
        PremiumPlan.NONE -> false
    }

    LaunchedEffect(refreshToken) { }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (premiumActive) {
            item { PremiumUnlockedCard(premium) }
            item {
                SettingsSection("Everything Premium", "Your account is verified and protected by the PayPal verification service.") {
                    PremiumBenefit("No ads", "Enjoy Music Player without banner advertising.")
                    PremiumBenefit("Unlimited equalizer", "Use all presets and band controls without the free preview limit.")
                    PremiumBenefit("Advanced sleep timer", "Use 15, 30 and 60 minute sleep timers.")
                    PremiumBenefit("Premium effects", "Unlock the full visual and audio effects experience.")
                    PremiumBenefit("Account recovery", "Your verified purchase stays linked to your Music Player account.")
                }
            }
            item {
                Card(
                    Modifier.fillMaxWidth().clickable(onClick = onRefresh),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Refresh, null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Verify again", fontWeight = FontWeight.ExtraBold)
                            Text(
                                "Refresh PayPal status before using Premium on another device.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    shape = RoundedCornerShape(30.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        Modifier.fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                ),
                                RoundedCornerShape(30.dp)
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Text("Premium", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(6.dp))
                            Text("Unlock the complete Music Player experience with verified account access.")
                            Spacer(Modifier.height(10.dp))
                            if (account == null) {
                                Text("Sign in before purchasing so the verified purchase can return to this account.")
                                Spacer(Modifier.height(10.dp))
                                Button(onClick = onAccount) { Text("Sign in / create account") }
                            } else {
                                Text("Signed in as " + (account.email ?: account.displayName.orEmpty()))
                            }
                        }
                    }
                }
            }
            item { PlanCard("Quarterly", "$5", "Every 3 months", true, quarterlyCheckoutReady) { onPurchase(PremiumPlan.QUARTERLY) } }
            item { PlanCard("Lifetime", "$30", "One-time payment • permanent", false, lifetimeCheckoutReady) { onPurchase(PremiumPlan.LIFETIME) } }
            item {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Free limits", fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(6.dp))
                        Text("Free users get " + PremiumRepository.TEST_PREMIUM_PREVIEW_LIMIT + " premium effect previews. After the limit, premium equalizer controls stay locked until a verified plan is active.")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Previews used: " + previewUses + " / " + PremiumRepository.TEST_PREMIUM_PREVIEW_LIMIT,
                            color = if (previewUses >= PremiumRepository.TEST_PREMIUM_PREVIEW_LIMIT)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth().clickable(onClick = onRefresh), shape = RoundedCornerShape(22.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Refresh, null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Refresh entitlement", fontWeight = FontWeight.Bold)
                            Text("Check the latest verified account state.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumUnlockedCard(premium: PremiumSnapshot) {
    val transition = rememberInfiniteTransition(label = "premium_glow")
    val alpha by transition.animateFloat(
        0.55f,
        1f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "premiumAlpha"
    )

    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ),
                    RoundedCornerShape(32.dp)
                )
                .padding(22.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        Modifier.size(66.dp).alpha(alpha),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(15.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("PREMIUM ACTIVE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                        Text("Everything unlocked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    premium.plan.name.lowercase().replaceFirstChar { it.uppercase() } +
                        if (premium.plan == PremiumPlan.QUARTERLY && premium.expiresAtMillis != null)
                            " • renews automatically"
                        else " • permanent access"
                )
                premium.expiresAtMillis?.takeIf { it > 0L }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Next billing: " + java.text.DateFormat.getDateInstance().format(java.util.Date(it)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Your payment was verified by the Music Player payment service.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PremiumBenefit(title: String, detail: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(Modifier.size(32.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.padding(6.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlanCard(
    name: String,
    price: String,
    detail: String,
    highlight: Boolean,
    checkoutReady: Boolean,
    onClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable(enabled = checkoutReady, onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(56.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(14.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(5.dp))
                Text(
                    if (checkoutReady) "PayPal checkout ready" else "Payment link not configured",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (checkoutReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(price, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun AccountDialog(
    activity: Activity?,
    repo: FirebaseAccountRepository,
    onDismiss: () -> Unit,
    onSigned: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val current = repo.currentAccount()
    val configured = repo.isFirebaseConfigured()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (current == null) "Optional account" else "Your account", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (current != null) {
                    Text(current.email ?: current.displayName ?: current.uid)
                    Text("Account is used to sync premium entitlement.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Music Player works without an account. Sign in only when you need premium purchases and cloud entitlement.")
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = configured && !busy
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = configured && !busy
                    )
                    Button(
                        onClick = {
                            busy = true
                            error = ""
                            scope.launch {
                                val result = repo.signInWithEmail(email, password)
                                busy = false
                                result.onSuccess { onSigned() }.onFailure { error = it.message.orEmpty() }
                            }
                        },
                        enabled = configured && !busy && email.isNotBlank() && password.length >= 6,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Sign in") }

                    TextButton(
                        onClick = {
                            busy = true
                            error = ""
                            scope.launch {
                                val result = repo.createWithEmail(email, password)
                                busy = false
                                result.onSuccess { onSigned() }.onFailure { error = it.message.orEmpty() }
                            }
                        },
                        enabled = configured && !busy && email.isNotBlank() && password.length >= 6,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Create account") }

                    TextButton(
                        onClick = {
                            val host = activity
                            if (host == null) {
                                error = "Google Sign-In needs the app activity."
                            } else {
                                busy = true
                                error = ""
                                scope.launch {
                                    val result = repo.signInWithGoogle(host)
                                    busy = false
                                    result.onSuccess { onSigned() }.onFailure { error = it.message.orEmpty() }
                                }
                            }
                        },
                        enabled = configured && !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Continue with Google") }

                    if (!configured) {
                        Text(
                            "Firebase is not connected yet. These account controls will activate after the Firebase project is registered.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (current != null) {
                TextButton(onClick = { repo.signOut(); onDismiss() }) { Text("Sign out") }
            } else {
                TextButton(onClick = onDismiss) { Text("Continue without account") }
            }
        }
    )
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    vm: PlayerViewModel,
    darkMode: Boolean,
    onDarkMode: (Boolean) -> Unit,
    themeName: String,
    backgroundName: String,
    onThemeChange: (AppThemeStyle) -> Unit,
    onBackgroundChange: (AppBackgroundStyle) -> Unit,
    onOpenPremium: () -> Unit,
    onAccount: () -> Unit,
    onCheckUpdates: () -> Unit
) {
    var autoScan by rememberSaveable { mutableStateOf(true) }
    var notifications by rememberSaveable { mutableStateOf(true) }
    val selectedTheme = runCatching { AppThemeStyle.valueOf(themeName) }.getOrDefault(AppThemeStyle.VIOLET)
    val selectedBackground = runCatching { AppBackgroundStyle.valueOf(backgroundName) }.getOrDefault(AppBackgroundStyle.GRADIENT)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsSection("Account", "Optional. Used for premium purchases and cloud entitlement.") {
                Button(onClick = onAccount, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Person, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Manage account")
                }
            }
        }
        item {
            SettingsSection("Premium", "Quarterly $5 or Lifetime $30.") {
                Button(onClick = onOpenPremium, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Star, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Premium")
                }
            }
        }
        item {
            SettingsSection("Library", "Local songs stay on the device.") {
                SwitchRow("Auto-scan new music", "Refresh the library when the app opens.", autoScan) {
                    autoScan = it
                    if (it) vm.scan()
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = vm::scan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan library now")
                }
            }
        }
        item {
            SettingsSection("Appearance", "Choose the visual identity, background and light/dark mode.") {
                Text("Theme", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                ThemeChoices(selectedTheme, onThemeChange)
                Spacer(Modifier.height(13.dp))
                Text("Background", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                BackgroundChoices(selectedBackground, onBackgroundChange)
                Spacer(Modifier.height(5.dp))
                SwitchRow("Dark mode", "Use the darker color system.", darkMode, onDarkMode)
            }
        }
        item {
            SettingsSection("Notifications", "Playback and update notifications.") {
                SwitchRow("Update notifications", "Allow update checks to notify you.", notifications) {
                    notifications = it
                }
            }
        }
        item {
            SettingsSection("Updates", "Keep Music Player current.") {
                Button(onClick = onCheckUpdates, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Update, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Check for updates")
                }
            }
        }
        item {
            SettingsSection("About", "Music Player is a local-first Android audio player.") {
                Text("Music Player " + BuildConfig.VERSION_NAME, fontWeight = FontWeight.Bold)
                Text("Offline-first playback powered by AndroidX Media3.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(17.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ThemeChoices(selected: AppThemeStyle, onSelected: (AppThemeStyle) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(AppThemeStyle.values().toList()) { theme ->
            AssistChip(
                onClick = { onSelected(theme) },
                label = { Text(theme.label) },
                leadingIcon = {
                    Surface(
                        Modifier.size(16.dp),
                        shape = CircleShape,
                        color = when (theme) {
                            AppThemeStyle.VIOLET -> Color(0xFF6D42E8)
                            AppThemeStyle.OCEAN -> Color(0xFF006B94)
                            AppThemeStyle.SUNSET -> Color(0xFFC54836)
                            AppThemeStyle.MINT -> Color(0xFF087A58)
                        }
                    ) {}
                }
            )
        }
    }
}

@Composable
private fun BackgroundChoices(selected: AppBackgroundStyle, onSelected: (AppBackgroundStyle) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(AppBackgroundStyle.values().toList()) { background ->
            AssistChip(onClick = { onSelected(background) }, label = { Text(background.label) })
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun AppearanceSetupDialog(
    theme: AppThemeStyle,
    background: AppBackgroundStyle,
    darkMode: Boolean,
    onThemeChange: (AppThemeStyle) -> Unit,
    onBackgroundChange: (AppBackgroundStyle) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Make Music Player yours", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose your theme and background before you start.")
                Text("Theme", fontWeight = FontWeight.Bold)
                ThemeChoices(theme, onThemeChange)
                Text("Background", fontWeight = FontWeight.Bold)
                BackgroundChoices(background, onBackgroundChange)
                SwitchRow("Dark mode", "You can change this later in Settings.", darkMode, onDarkModeChange)
            }
        },
        confirmButton = { Button(onClick = onSave) { Text("Continue") } }
    )
}

@Composable
private fun AnimatedBars(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "bars")
    val a by transition.animateFloat(
        initialValue = 8f,
        targetValue = if (active) 26f else 10f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "barA"
    )
    val b by transition.animateFloat(
        initialValue = 14f,
        targetValue = if (active) 34f else 12f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "barB"
    )
    val c by transition.animateFloat(
        initialValue = 10f,
        targetValue = if (active) 22f else 9f,
        animationSpec = infiniteRepeatable(tween(460), RepeatMode.Reverse),
        label = "barC"
    )
    Row(
        Modifier.height(36.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(Modifier.width(4.dp).height(a.dp), shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary) {}
        Surface(Modifier.width(4.dp).height(b.dp), shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondary) {}
        Surface(Modifier.width(4.dp).height(c.dp), shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiary) {}
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.LibraryMusic, null, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(8.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(13.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BannerAd() {
    val context = LocalContext.current
    AndroidView(
        factory = {
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = "ca-app-pub-3940256099942544/6300978111"
                loadAd(AdRequest.Builder().build())
            }
        },
        modifier = Modifier.fillMaxWidth().height(60.dp)
    )
}

@Composable
private fun Artwork(song: Song, modifier: Modifier) {
    val context = LocalContext.current
    var artwork by remember(song.id) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(song.id) {
        artwork = MusicRepository(context).loadArtwork(song.uri)
    }

    Surface(
        modifier.clip(RoundedCornerShape(18.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        if (artwork != null) {
            androidx.compose.foundation.Image(
                bitmap = artwork!!.asImageBitmap(),
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
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
                Text("Music Player " + info.versionName)
                Spacer(Modifier.height(7.dp))
                Text(info.changelog, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (updating) {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("$progress%", modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onInstall, enabled = !updating) { Text("Download & install") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !updating) { Text("Later") } }
    )
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
