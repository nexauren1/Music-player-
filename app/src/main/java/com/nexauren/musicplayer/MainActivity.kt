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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val playerViewModel by viewModels<PlayerViewModel>()
    private val premiumRepository by lazy { PremiumRepository(this) }
    private var premiumEvent by mutableIntStateOf(0)
    private var premiumMessage by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppAnalytics.initialize(this)
        setContent {
            MusicPlayerRoot(
                playerViewModel,
                premiumEvent,
                premiumMessage
            ) { message ->
                premiumMessage = message
            }
        }

        // Process deep links after Compose is attached so launch-from-browser is safe.
        runCatching { handlePayPalIntent(intent) }
            .onFailure { premiumMessage = "Music Player could not process the return link." }
    }

    override fun onStart() {
        super.onStart()
        AppAnalytics.onActivityStart()
    }

    override fun onStop() {
        AppAnalytics.onActivityStop()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        runCatching { handlePayPalIntent(intent) }
            .onFailure { premiumMessage = "Music Player could not process the return link." }
    }

    override fun onResume() {
        super.onResume()
        runCatching {
            ApkInstaller.resumeIfPending(this)
        }.onFailure {
            // Never let a broken/stale pending APK prevent the app from launching.
            getSharedPreferences("update_install", MODE_PRIVATE)
                .edit()
                .remove("pending_apk")
                .apply()
        }

        lifecycleScope.launch {
            runCatching {
                val account = FirebaseAccountRepository(this@MainActivity).currentAccount()
                    ?: return@runCatching

                PayPalCheckout.verifyPending(this@MainActivity, account.uid, premiumRepository)
                    ?.onSuccess { verified ->
                        if (verified) {
                            premiumEvent++
                            premiumMessage = "Payment verified. Premium is now active."
                        }
                    }

                val local = premiumRepository.loadLocal()
                if (local.verified && local.plan != PremiumPlan.NONE) {
                    PayPalVerifier.verify(
                        uid = account.uid,
                        plan = local.plan,
                        orderId = local.orderId,
                        subscriptionId = local.subscriptionId
                    ).onSuccess {
                        if (it.verified) {
                            premiumRepository.setVerifiedFromWorker(
                                it.plan,
                                it.expiresAtMillis,
                                it.orderId,
                                it.subscriptionId
                            )
                            val cloudSync = premiumRepository.syncVerifiedToFirebase()
                            premiumEvent++
                            premiumMessage = if (cloudSync.isSuccess) {
                                "Premium status verified."
                            } else {
                                "Premium verified. Account sync is still processing."
                            }
                        } else {
                            premiumRepository.clearVerifiedPremium()
                            premiumEvent++
                            premiumMessage = "Premium is no longer active."
                        }
                    }
                }
            }.onFailure {
                // Premium verification must never make the main activity crash.
                premiumMessage = "Premium verification will retry automatically."
            }
        }
    }

    private fun handlePayPalIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "musicplayer" || data.host != "paypal") return

        if (data.path == "/cancel") {
            premiumMessage = "Payment cancelled. Premium was not activated."
            premiumEvent++
            return
        }

        if (data.path != "/success") return

        val account = FirebaseAccountRepository(this).currentAccount()
        if (account == null) {
            premiumMessage = "Sign in again so Music Player can verify this purchase."
            premiumEvent++
            return
        }

        val pending = PayPalCheckout.loadPending(this)
        val planFromIntent = when (data.getQueryParameter("plan")) {
            "quarterly" -> PremiumPlan.QUARTERLY
            "lifetime" -> PremiumPlan.LIFETIME
            else -> PremiumPlan.NONE
        }
        val plan = if (planFromIntent != PremiumPlan.NONE) planFromIntent
        else pending?.plan ?: PremiumPlan.NONE
        val orderId = data.getQueryParameter("orderId")
            ?.takeIf { it.isNotBlank() }
            ?: pending?.orderId
        val subscriptionId = data.getQueryParameter("subscriptionId")
            ?.takeIf { it.isNotBlank() }
            ?: pending?.subscriptionId

        lifecycleScope.launch {
            premiumMessage = "Verifying your PayPal payment…"

            val pendingResult = PayPalCheckout.verifyPending(
                this@MainActivity,
                account.uid,
                premiumRepository
            )

            if (pendingResult?.getOrNull() == true) {
                premiumMessage = "Premium activated. All Premium features are now unlocked."
                premiumEvent++
                return@launch
            }

            if (plan == PremiumPlan.NONE) {
                premiumMessage = "PayPal returned, but the purchase details could not be recovered."
                premiumEvent++
                return@launch
            }

            PayPalVerifier.verifyWithRetry(
                account.uid,
                plan,
                orderId,
                subscriptionId
            ).onSuccess {
                if (it.verified) {
                    premiumRepository.setVerifiedFromWorker(
                        it.plan,
                        it.expiresAtMillis,
                        it.orderId,
                        it.subscriptionId
                    )
                    val cloudSync = premiumRepository.syncVerifiedToFirebase()
                    PayPalCheckout.clearPending(this@MainActivity)
                    premiumMessage = if (cloudSync.isSuccess) {
                        "Premium activated. Your Firebase account is now synced."
                    } else {
                        "Premium activated. Firebase account sync is still processing."
                    }
                } else {
                    premiumMessage = "PayPal returned successfully, but Premium is still synchronizing."
                }
                premiumEvent++
            }.onFailure {
                premiumMessage = "Premium verification failed: " + (it.message ?: "Unknown error")
                premiumEvent++
            }
        }
    }
}

private enum class AppScreen(private val key: String) {
    HOME("Home"),
    LIBRARY("Library"),
    FAVORITES("Favorites"),
    MOST_PLAYED("Most played"),
    ACCOUNT("Account"),
    EQUALIZER("Equalizer"),
    PREMIUM("Premium"),
    SETTINGS("Settings");

    val label: String
        get() = I18n.t(key)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicPlayerRoot(
    vm: PlayerViewModel,
    premiumEvent: Int,
    premiumMessage: String,
    onPremiumMessage: (String) -> Unit
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
    var languageName by rememberSaveable { mutableStateOf(storedAppearance.language.name) }
    var showAppearanceSetup by rememberSaveable { mutableStateOf(!storedAppearance.configured) }
    var nowPlaying by rememberSaveable { mutableStateOf(false) }
    var accountDialog by remember { mutableStateOf(false) }
    var accountRefresh by remember { mutableIntStateOf(0) }
    var premiumRefresh by remember { mutableIntStateOf(0) }
    var premiumSnapshot by remember { mutableStateOf(premiumRepo.loadLocal()) }
    val accountSnapshot = remember(accountRefresh) { accountRepo.currentAccount() }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updating by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableIntStateOf(0) }
    var updateError by remember { mutableStateOf("") }
    var premiumProcessing by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var effectsOpen by rememberSaveable { mutableStateOf(false) }
    var drivingModeOpen by rememberSaveable { mutableStateOf(false) }
    var drivingMode by rememberSaveable { mutableStateOf(context.getSharedPreferences("player_modes", android.content.Context.MODE_PRIVATE).getBoolean("driving_mode", false)) }
    var showTutorial by rememberSaveable { mutableStateOf(!context.getSharedPreferences("first_run", android.content.Context.MODE_PRIVATE).getBoolean("tutorial_done", false)) }

    val appTheme = runCatching { AppThemeStyle.valueOf(themeName) }.getOrDefault(AppThemeStyle.VIOLET)
    val appBackground = runCatching { AppBackgroundStyle.valueOf(backgroundName) }.getOrDefault(AppBackgroundStyle.GRADIENT)
    val appLanguage = runCatching { AppLanguage.valueOf(languageName) }.getOrDefault(AppLanguage.ENGLISH)
    LaunchedEffect(appLanguage) { I18n.language = appLanguage }

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
        premiumSnapshot = premiumRepo.loadLocal()
        delay(1500)
        if (updateInfo == null) {
            updateInfo = UpdateManager.check(context)
        }
    }

    LaunchedEffect(premiumEvent) {
        premiumSnapshot = premiumRepo.loadLocal()
        if (premiumEvent > 0) {
            premiumRefresh++
            screen = AppScreen.PREMIUM
            Toast.makeText(context, premiumMessage, Toast.LENGTH_LONG).show()        }
    }

    LaunchedEffect(screen, nowPlaying, effectsOpen, drivingModeOpen) {
        val page = when {
            drivingModeOpen -> "driving_mode"
            effectsOpen -> "effects"
            nowPlaying -> "now_playing"
            else -> screen.name.lowercase()
        }
        AppAnalytics.screenView(page)
        if (nowPlaying && !effectsOpen) AppAnalytics.log("now_playing_open")
        if (effectsOpen) AppAnalytics.log("effects_open")
    }

    LaunchedEffect(screen, accountRefresh) {
        if (screen == AppScreen.PREMIUM || screen == AppScreen.EQUALIZER) {
            premiumRepo.syncFromFirebase()
            premiumSnapshot = premiumRepo.loadLocal()
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
                            Text(I18n.t("Music Player"), fontWeight = FontWeight.ExtraBold)
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
                        AppScreen.ACCOUNT to Icons.Filled.Person,
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

                    Spacer(Modifier.height(12.dp))
                    Text(
                        I18n.t("Language"),
                        modifier = Modifier.padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    LanguageChoices(
                        selected = appLanguage,
                        onSelected = {
                            languageName = it.name
                            AppearanceStore.save(context, appTheme, appBackground, darkMode, it)
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text(I18n.t("Driving mode")) },
                        icon = { Icon(Icons.Filled.DirectionsCar, null) },
                        selected = drivingMode,
                        onClick = {
                            drivingMode = !drivingMode
                            context.getSharedPreferences("player_modes", android.content.Context.MODE_PRIVATE)
                                .edit().putBoolean("driving_mode", drivingMode).apply()
                            drawerOpen = false
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )

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
                            IconButton(onClick = { screen = AppScreen.ACCOUNT }) {
                                Icon(
                                    if (accountSnapshot != null)
                                        Icons.Filled.AccountCircle
                                    else
                                        Icons.Filled.Person,
                                    "Account"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
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
                                onNext = vm::next,
                                onFavorite = { vm.favorite(song) },
                                onOpenEffects = { effectsOpen = true },
                                onOpenEqualizer = { screen = AppScreen.EQUALIZER },
                                onDrivingMode = {
                                    drivingMode = true
                                    drivingModeOpen = true
                                    context.getSharedPreferences("player_modes", android.content.Context.MODE_PRIVATE)
                                        .edit().putBoolean("driving_mode", true).apply()
                                },
                                onResetEffects = vm::resetEffects
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
                        title = I18n.t("Favorites"),
                        songs = vm.favorites(),
                        showPlays = false
                    )
                    AppScreen.MOST_PLAYED -> SongListScreen(
                        modifier = Modifier.padding(padding),
                        vm = vm,
                        title = I18n.t("Most played"),
                        songs = vm.mostPlayed(),
                        showPlays = true
                    )
                    AppScreen.ACCOUNT -> AccountScreen(
                        modifier = Modifier.padding(padding),
                        account = accountSnapshot,
                        premium = premiumSnapshot,
                        onSignIn = { accountDialog = true },
                        onSignOut = {
                            accountRepo.signOut()
                            accountRefresh++
                        },
                        onPremium = { screen = AppScreen.PREMIUM },
                        refreshToken = accountRefresh
                    )
                    AppScreen.EQUALIZER -> EqualizerScreen(
                        modifier = Modifier.padding(padding),
                        eq = vm.equalizerController(),
                        premium = premiumSnapshot,
                        onBack = { screen = AppScreen.HOME },
                        onOpenPremium = { screen = AppScreen.PREMIUM }
                    )
                    AppScreen.PREMIUM -> PremiumScreen(
                        modifier = Modifier.padding(padding),
                        account = accountSnapshot,
                        premium = premiumSnapshot,
                        onAccount = { screen = AppScreen.ACCOUNT },
                        processing = premiumProcessing,
                        onRefresh = {
                            activity?.let { host ->
                                host.lifecycleScope.launch {
                                    premiumProcessing = true
                                    val account = accountRepo.currentAccount()

                                    if (account != null) {
                                        val pending = PayPalCheckout.loadPending(context)

                                        if (pending != null) {
                                            val verification = PayPalVerifier.verifyWithRetry(
                                                uid = account.uid,
                                                plan = pending.plan,
                                                orderId = pending.orderId,
                                                subscriptionId = pending.subscriptionId
                                            )
                                            val result = verification.getOrNull()
                                            if (result?.verified == true) {
                                                premiumRepo.setVerifiedFromWorker(
                                                    result.plan,
                                                    result.expiresAtMillis,
                                                    result.orderId,
                                                    result.subscriptionId
                                                )
                                                val cloudSync = premiumRepo.syncVerifiedToFirebase()
                                                PayPalCheckout.clearPending(context)
                                                onPremiumMessage(
                                                    if (cloudSync.isSuccess)
                                                        "Payment verified. Premium is now active and synced to your account."
                                                    else
                                                        "Payment verified. Account sync is still processing."
                                                )
                                            } else if (verification.isFailure) {
                                                onPremiumMessage("Premium verification is temporarily unavailable. Your payment remains pending.")
                                            } else {
                                                onPremiumMessage("Payment is still processing. We will keep the purchase pending until PayPal confirms it.")
                                            }
                                        } else {
                                            val local = premiumRepo.loadLocal()
                                            if (local.plan != PremiumPlan.NONE && local.verified) {
                                                PayPalVerifier.verifyWithRetry(
                                                    uid = account.uid,
                                                    plan = local.plan,
                                                    orderId = local.orderId,
                                                    subscriptionId = local.subscriptionId
                                                ).onSuccess { result ->
                                                    if (result.verified) {
                                                        premiumRepo.setVerifiedFromWorker(
                                                            result.plan,
                                                            result.expiresAtMillis,
                                                            result.orderId,
                                                            result.subscriptionId
                                                        )
                                                        val cloudSync = premiumRepo.syncVerifiedToFirebase()
                                                        onPremiumMessage(
                                                            if (cloudSync.isSuccess)
                                                                "Premium status verified and synced to your account."
                                                            else
                                                                "Premium verified. Account sync is still processing."
                                                        )
                                                    } else {
                                                        premiumRepo.clearVerifiedPremium()
                                                        onPremiumMessage("Premium is no longer active.")
                                                    }
                                                }.onFailure {
                                                    onPremiumMessage("Premium verification is temporarily unavailable.")
                                                }
                                            } else {
                                                onPremiumMessage("No pending Premium purchase was found.")
                                            }
                                        }
                                    } else {
                                        onPremiumMessage("Sign in to verify Premium.")
                                    }

                                    premiumRepo.syncFromFirebase()
                                    premiumSnapshot = premiumRepo.loadLocal()
                                    premiumRefresh++
                                    premiumProcessing = false
                                }
                            }
                        },
                        onPurchase = { plan ->
                            val account = accountRepo.currentAccount()
                            if (account == null) {
                                accountDialog = true
                            } else if (plan == PremiumPlan.NONE) {
                                Toast.makeText(context, I18n.t("Choose a Premium plan."), Toast.LENGTH_SHORT).show()
                            } else {
                                activity?.lifecycleScope?.launch {
                                    premiumProcessing = true
                                    PayPalCheckout.createCheckout(context, account.uid, plan)
                                        .onSuccess { approvalUrl ->
                                            premiumProcessing = false
                                            onPremiumMessage("PayPal checkout opened. Waiting for payment confirmation.")
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(approvalUrl))
                                                )
                                            }.onFailure {
                                                Toast.makeText(context, I18n.t("Unable to open PayPal checkout."),
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                        .onFailure {
                                            premiumProcessing = false
                                            Toast.makeText(context, I18n.t("PayPal checkout failed: ") + (it.message ?: "Unknown error"),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                }
                            }
                        },
                        quarterlyCheckoutReady = BuildConfig.PAYPAL_WORKER_URL.isNotBlank(),
                        lifetimeCheckoutReady = BuildConfig.PAYPAL_WORKER_URL.isNotBlank(),
                        refreshToken = premiumRefresh
                    )
                    AppScreen.SETTINGS -> SettingsScreen(
                        modifier = Modifier.padding(padding),
                        vm = vm,
                        darkMode = darkMode,
                        onDarkMode = {
                            darkMode = it
                            AppearanceStore.save(context, appTheme, appBackground, it, appLanguage)
                        },
                        themeName = themeName,
                        backgroundName = backgroundName,
                        languageName = languageName,
                        onThemeChange = {
                            themeName = it.name
                            AppearanceStore.save(context, it, appBackground, darkMode, appLanguage)
                        },
                        onBackgroundChange = {
                            backgroundName = it.name
                            AppearanceStore.save(context, appTheme, it, darkMode, appLanguage)
                        },
                        onLanguageChange = {
                            languageName = it.name
                            AppearanceStore.save(context, appTheme, appBackground, darkMode, it)
                        },
                        onOpenPremium = { screen = AppScreen.PREMIUM },
                        onAccount = { accountDialog = true },
                        notificationsAllowed = notificationsAllowed,
                        onRequestNotifications = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                context.startActivity(
                                    Intent(
                                        android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                    ).apply {
                                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                )
                            }
                        },
                        checkingUpdate = checkingUpdate,
                        onCheckUpdates = {
                            activity?.let { host ->
                                host.lifecycleScope.launch {
                                    checkingUpdate = true
                                    val found = UpdateManager.check(context)
                                    updateInfo = found
                                    checkingUpdate = false
                                    if (found == null) {
                                        Toast.makeText(context, I18n.t("Music Player is up to date."),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
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
                premium = premiumSnapshot,
                playing = playing,
                position = position,
                duration = duration,
                onDismiss = { nowPlaying = false },
                onOpenEffects = { effectsOpen = true },
                onDrivingMode = {
                    drivingMode = true
                    drivingModeOpen = true
                    context.getSharedPreferences("player_modes", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("driving_mode", true).apply()
                },
                onEqualizer = {
                    nowPlaying = false
                    screen = AppScreen.EQUALIZER
                }
            )
        }

        if (drivingModeOpen && currentSong != null) {
            DrivingModeDialog(
                song = currentSong!!,
                playing = playing,
                onPlayPause = vm::togglePlayPause,
                onPrevious = vm::previous,
                onNext = vm::next,
                onDismiss = { drivingModeOpen = false }
            )
        }

        if (effectsOpen) {
            EffectsScreen(
                vm = vm,
                premium = premiumSnapshot,
                onDismiss = { effectsOpen = false }
            )
        }

        if (accountDialog) {
            AccountDialog(
                activity = activity,
                repo = accountRepo,
                onDismiss = { accountDialog = false },
                onSigned = {
                    accountDialog = false
                    accountRefresh++
                    activity?.lifecycleScope?.launch {
                        premiumRepo.syncFromFirebase()
                        premiumSnapshot = premiumRepo.loadLocal()
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
                error = updateError,
                onDismiss = { if (!updating) updateInfo = null },
                onInstall = {
                    updating = true
                    updateError = ""
                    activity?.let { host ->
                        host.lifecycleScope.launch {
                            runCatching {
                                UpdateManager.downloadAndInstall(context, info) {
                                    updateProgress = it
                                }
                            }.onFailure {
                                updateError = it.message ?: "The update could not be installed."
                            }
                            updating = false
                            if (updateError.isBlank()) {
                                updateInfo = null
                            }
                        }
                    }
                }
            )
        }

        if (showTutorial) {
            FirstRunTutorial(
                onFinish = {
                    context.getSharedPreferences("first_run", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("tutorial_done", true).apply()
                    showTutorial = false
                }
            )
        } else if (showAppearanceSetup) {
            AppearanceSetupDialog(
                theme = appTheme,
                background = appBackground,
                darkMode = darkMode,
                language = appLanguage,
                onThemeChange = { themeName = it.name },
                onBackgroundChange = { backgroundName = it.name },
                onDarkModeChange = { darkMode = it },
                onLanguageChange = { languageName = it.name },
                onSave = {
                    AppearanceStore.save(context, appTheme, appBackground, darkMode, appLanguage)
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
    onQueryChange: (String) -> Unit
) {
    val current by vm.currentSong.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val recent = remember(songs) { songs.sortedByDescending { it.dateAddedMillis }.take(8) }
    val mostPlayed = remember(songs) { vm.mostPlayed().take(8) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 154.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(
                    "YOUR MUSIC",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Music Player",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    if (songs.isEmpty()) "Add music to your library and start listening."
                    else "${songs.size} tracks ready to play",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { SearchBar(query, onQueryChange) }
        item { SectionTitle(I18n.t("Now playing"), I18n.t("Live status • view only")) }
        item { NowPlayingInfoCard(current, playing) }
        item { SectionTitle(I18n.t("Recently added"), I18n.t("Newest music on your device • view only")) }

        item {
            if (recent.isEmpty()) {
                EmptyCard(I18n.t("No recently added music yet."))
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(end = 8.dp)
                ) {
                    items(recent, key = { it.id }) { song ->
                        RecentAddedCard(song)
                    }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionTitle(I18n.t("Most played"), I18n.t("Your listening history"))
                Text(I18n.t("${mostPlayed.size}"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
            }
        }

        item {
            if (mostPlayed.isEmpty()) {
                EmptyCard(I18n.t("Your most played songs will appear here."))
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(end = 8.dp)
                ) {
                    items(mostPlayed, key = { it.id }) { song ->
                        MostPlayedCard(song, vm.playCount(song))
                    }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionTitle(I18n.t("All songs"), I18n.t("Your complete playable library"))
                Text(I18n.t("${songs.size}"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
            }
        }

        if (songs.isEmpty()) {
            item { EmptyCard(I18n.t("No songs found. Scan your library from Settings.")) }
        } else {
            itemsIndexed(songs, key = { _, song -> song.id }) { _, song ->
                SongRow(song, vm, showPlays = false)
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
                StatCard(I18n.t("Tracks"), songs.size.toString(), Modifier.weight(1f))
                StatCard(I18n.t("Albums"), songs.map { it.albumId }.distinct().size.toString(), Modifier.weight(1f))
                StatCard(I18n.t("Artists"), songs.map { it.artist }.distinct().size.toString(), Modifier.weight(1f))
            }
        }
        item { SectionTitle(I18n.t("All music"), I18n.t("Your complete local library")) }
        if (songs.isEmpty()) {
            item { EmptyCard(I18n.t("No songs found.")) }
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
        placeholder = { Text(I18n.t("Search title, artist or album")) },
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
    val pulse by transition.animateFloat(
        0.97f,
        1.015f,
        infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "heroPulse"
    )

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(34.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    ),
                    RoundedCornerShape(34.dp)
                )
                .padding(18.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "NOW PLAYING",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.82f),
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Your sound. Your space.",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.16f)
                    ) {
                        Text(
                            if (playing) I18n.t("PLAYING") else I18n.t("PAUSED"),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                if (song != null) {
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.size(286.dp)
                                .graphicsLayer {
                                    scaleX = if (playing) pulse else 1f
                                    scaleY = if (playing) pulse else 1f
                                }
                                .clip(RoundedCornerShape(30.dp))
                        ) {
                            Artwork(song, Modifier.fillMaxSize())
                            Box(
                                Modifier.fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                                        )
                                    )
                            )
                            Column(
                                Modifier.align(Alignment.BottomStart).padding(16.dp)
                            ) {
                                Text(
                                    song.title,
                                    color = Color.White,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    song.artist,
                                    color = Color.White.copy(alpha = 0.86f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (playing) I18n.t("Listening now") else I18n.t("Playback paused"),
                            color = Color.White.copy(alpha = 0.86f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        AnimatedBars(playing)
                    }
                } else {
                    Box(
                        Modifier.fillMaxWidth().height(220.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .background(Color.White.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.LibraryMusic,
                                null,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(I18n.t("Nothing playing yet"), color = Color.White, fontWeight = FontWeight.ExtraBold)
                            Text(
                                "Choose a track from All songs",
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun RecentAddedCard(song: Song) {
    Card(
        Modifier.width(172.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(10.dp)) {
            Box {
                Artwork(song, Modifier.fillMaxWidth().height(152.dp))
                Surface(
                    Modifier.align(Alignment.TopEnd).padding(8.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ) {
                    Icon(
                        Icons.Filled.LibraryMusic,
                        null,
                        tint = MaterialTheme.colorScheme.primary,                        modifier = Modifier.padding(8.dp).size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.ExtraBold)
            Text(
                song.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Recently added",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp)
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
            item { EmptyCard(I18n.t("Nothing here yet.")) }
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
                    TextButton(onClick = { vm.play(song); menu = false }) { Text(I18n.t("Play now")) }
                    TextButton(onClick = { vm.startSleepTimer(15); menu = false }) { Text(I18n.t("Sleep 15 min")) }
                    TextButton(onClick = { vm.favorite(song); menu = false }) {
                        Text(if (vm.isFavorite(song)) I18n.t("Remove favorite") else I18n.t("Add favorite"))
                    }
                }
            }
        }
    }
}

@Composable
private fun MostPlayedCard(song: Song, plays: Int) {
    Card(
        Modifier.width(186.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(Modifier.padding(10.dp)) {
            Box {
                Artwork(song, Modifier.fillMaxWidth().height(158.dp))
                Surface(
                    Modifier.align(Alignment.BottomStart).padding(8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        "${plays} plays",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.ExtraBold)
            Text(
                song.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    onNext: () -> Unit,
    onFavorite: () -> Unit,
    onOpenEffects: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onDrivingMode: () -> Unit,
    onResetEffects: () -> Unit
) {
    val progress = if (duration > 0) {
        (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 2.dp
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .padding(horizontal = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(
                    song,
                    Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(13.dp))
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        song.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) I18n.t("Pause") else I18n.t("Play"),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, I18n.t("Next track"))
                }
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, I18n.t("More options"))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(I18n.t("Effects")) },
                            onClick = { menuOpen = false; onOpenEffects() }
                        )
                        DropdownMenuItem(
                            text = { Text(I18n.t("Equalizer")) },
                            onClick = { menuOpen = false; onOpenEqualizer() }
                        )
                        DropdownMenuItem(
                            text = { Text(I18n.t("Driving mode")) },
                            onClick = { menuOpen = false; onDrivingMode() },
                            leadingIcon = { Icon(Icons.Filled.DirectionsCar, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(I18n.t("Reset effects")) },
                            onClick = { menuOpen = false; onResetEffects() }
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun NowPlayingSheet(
    song: Song,
    vm: PlayerViewModel,
    premium: PremiumSnapshot,
    playing: Boolean,
    position: Long,
    duration: Long,
    onDismiss: () -> Unit,
    onOpenEffects: () -> Unit,
    onEqualizer: () -> Unit,
    onDrivingMode: () -> Unit
) {
    val volume by vm.volume.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().fillMaxHeight(),
            contentPadding = PaddingValues(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.ArrowBack, I18n.t("Close"))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(I18n.t("Now Playing"), fontWeight = FontWeight.Black)
                        Text(
                            if (playing) I18n.t("Playing") else I18n.t("Paused"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, I18n.t("More options"))
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(I18n.t("Effects")) },
                                onClick = {
                                    menuOpen = false
                                    onOpenEffects()
                                },
                                leadingIcon = { Icon(Icons.Filled.Tune, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(I18n.t("Equalizer")) },
                                onClick = {
                                    menuOpen = false
                                    onEqualizer()
                                },
                                leadingIcon = { Icon(Icons.Filled.Tune, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(I18n.t("Driving mode")) },
                                onClick = { menuOpen = false; onDrivingMode() },
                                leadingIcon = { Icon(Icons.Filled.DirectionsCar, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(I18n.t("Reset effects")) },
                                onClick = {
                                    menuOpen = false
                                    vm.resetEffects()
                                },
                                leadingIcon = { Icon(Icons.Filled.Refresh, null) }
                            )
                        }
                    }
                }
            }

            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(310.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Artwork(song, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        song.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            item {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    Slider(
                        value = if (duration > 0) {
                            position.toFloat().coerceIn(0f, duration.toFloat())
                        } else 0f,
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

            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.setVolume(0f) }) {
                                Icon(Icons.Filled.VolumeDown, I18n.t("Mute"))
                            }
                            Slider(
                                value = volume,
                                onValueChange = vm::setVolume,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { vm.setVolume(1f) }) {
                                Icon(Icons.Filled.VolumeUp, I18n.t("Max volume"))
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = vm::toggleShuffle) { Icon(Icons.Filled.Shuffle, I18n.t("Shuffle")) }
                            IconButton(onClick = vm::previous) { Icon(Icons.Filled.SkipPrevious, I18n.t("Previous track"), Modifier.size(30.dp)) }
                            Surface(
                                Modifier.size(70.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                IconButton(onClick = vm::togglePlayPause) {
                                    Icon(
                                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        if (playing) I18n.t("Pause") else I18n.t("Play"),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                            IconButton(onClick = vm::next) { Icon(Icons.Filled.SkipNext, I18n.t("Next track"), Modifier.size(30.dp)) }
                            IconButton(onClick = vm::toggleRepeat) { Icon(Icons.Filled.Repeat, I18n.t("Repeat")) }
                        }
                    }
                }
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp)
                ) {
                    listOf(15, 30, 60).forEach { minutes ->
                        item {
                            AssistChip(
                                onClick = { vm.startSleepTimer(minutes) },
                                label = { Text(I18n.t("$minutes min")) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EffectsScreen(
    vm: PlayerViewModel,
    premium: PremiumSnapshot,
    onDismiss: () -> Unit
) {
    val speed by vm.speed.collectAsState()
    val pitch by vm.pitch.collectAsState()
    var effects by remember { mutableStateOf(vm.effectState()) }

    val premiumActive = premium.cloudSynced && premium.verified && when (premium.plan) {
        PremiumPlan.LIFETIME -> true
        PremiumPlan.QUARTERLY ->
            premium.expiresAtMillis == null || premium.expiresAtMillis > System.currentTimeMillis()
        PremiumPlan.NONE -> false
    }

    fun reset() {
        vm.resetEffects()
        effects = vm.effectState()
        AppAnalytics.log("effects_reset")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(I18n.t("Effects"), fontWeight = FontWeight.Black)
                                Text(
                                    if (premiumActive) I18n.t("Premium effects unlocked")
                                    else I18n.t("Playback controls are free"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.ArrowBack, I18n.t("Close"))
                            }
                        },
                        actions = {
                            IconButton(onClick = ::reset) {
                                Icon(Icons.Filled.Refresh, I18n.t("Reset effects"))
                            }
                        }
                    )
                }
            ) { padding ->
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(26.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(I18n.t("Playback"), fontWeight = FontWeight.Black)
                                Text(
                                    I18n.t("These settings are saved automatically and restored on the next launch."),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    item {
                        EffectSliderCard(
                            title = I18n.t("Speed"),
                            value = speed,
                            range = .5f..2f,
                            valueLabel = "%.2fx".format(speed),
                            onChange = vm::setSpeed
                        )
                    }

                    item {
                        EffectSliderCard(
                            title = I18n.t("Pitch"),
                            value = pitch,
                            range = .5f..1.5f,
                            valueLabel = "%.2fx".format(pitch),
                            onChange = vm::setPitch
                        )
                    }

                    item {
                        EffectSliderCard(
                            title = I18n.t("Bass Boost"),
                            value = effects.bassBoost,
                            range = 0f..1f,
                            enabled = premiumActive,
                            valueLabel = "${(effects.bassBoost * 100).toInt()}%",
                            onChange = {
                                effects = effects.copy(bassBoost = it)
                                vm.equalizerController().setBassBoost(it)
                            }
                        )
                    }

                    item {
                        EffectSliderCard(
                            title = I18n.t("3D Space"),
                            value = effects.surround,
                            range = 0f..1f,
                            enabled = premiumActive,
                            valueLabel = "${(effects.surround * 100).toInt()}%",
                            onChange = {
                                effects = effects.copy(surround = it)
                                vm.equalizerController().setSurround(it)
                            }
                        )
                    }

                    item {
                        EffectSliderCard(
                            title = I18n.t("Loudness"),
                            value = effects.loudness,
                            range = 0f..1f,
                            enabled = premiumActive,
                            valueLabel = "${(effects.loudness * 100).toInt()}%",
                            onChange = {
                                effects = effects.copy(loudness = it)
                                vm.equalizerController().setLoudness(it)
                            }
                        )
                    }

                    item {
                        EffectSliderCard(
                            title = I18n.t("Reverb"),
                            value = effects.reverb,
                            range = 0f..1f,
                            enabled = premiumActive,
                            valueLabel = "${(effects.reverb * 100).toInt()}%",
                            onChange = {
                                effects = effects.copy(reverb = it)
                                vm.equalizerController().setReverb(it)
                            }
                        )
                    }

                    item {
                        EffectSliderCard(
                            title = I18n.t("Delay / Echo"),
                            value = effects.delay,
                            range = 0f..1f,
                            enabled = premiumActive,
                            valueLabel = "${(effects.delay * 500).toInt()} ms",
                            onChange = {
                                effects = effects.copy(delay = it)
                                vm.equalizerController().setDelay(it)
                            }
                        )
                    }

                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            I18n.t("Auto saved"),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            I18n.t("Speed, pitch and audio effects are stored on this device."),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = ::reset,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Refresh, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(I18n.t("Reset all effects"))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EffectSliderCard(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    enabled: Boolean = true,
    onChange: (Float) -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text(valueLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                enabled = enabled
            )
            if (!enabled) {
                Text(
                    I18n.t("Premium effect"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EqualizerScreen(
    modifier: Modifier,
    eq: EqualizerController,
    premium: PremiumSnapshot,
    onBack: () -> Unit,
    onOpenPremium: () -> Unit
) {
    val premiumActive = premium.cloudSynced && premium.verified && when (premium.plan) {
        PremiumPlan.LIFETIME -> true
        PremiumPlan.QUARTERLY ->
            premium.expiresAtMillis == null || premium.expiresAtMillis > System.currentTimeMillis()
        PremiumPlan.NONE -> false
    }

    var levels by remember { mutableStateOf(eq.normalizedLevels().ifEmpty { List(eq.bandCount()) { .5f } }) }
    var effects by remember { mutableStateOf(eq.effectState()) }
    var attached by remember { mutableStateOf(eq.bandCount() > 0) }
    var message by remember { mutableStateOf("Play music to activate the live audio engine.") }

    val presets = remember {
        listOf(
            "Flat" to listOf(.50f, .50f, .50f, .50f, .50f, .50f, .50f, .50f, .50f, .50f),
            "Rock" to listOf(.78f, .66f, .52f, .46f, .56f, .72f, .84f, .78f, .68f, .60f),
            "Vocal" to listOf(.40f, .44f, .52f, .66f, .78f, .82f, .72f, .62f, .56f, .52f),
            "Jazz" to listOf(.58f, .56f, .52f, .58f, .70f, .76f, .70f, .60f, .56f, .52f),
            "Club" to listOf(.72f, .62f, .54f, .56f, .66f, .76f, .72f, .64f, .60f, .64f),
            "Deep Bass" to listOf(.96f, .88f, .76f, .62f, .54f, .50f, .50f, .52f, .56f, .60f),
            "DJ Punch" to listOf(.90f, .74f, .60f, .52f, .68f, .86f, .76f, .64f, .58f, .62f),
            "Hip-Hop" to listOf(.90f, .78f, .60f, .50f, .58f, .72f, .84f, .76f, .66f, .60f),
            "EDM" to listOf(.88f, .74f, .58f, .56f, .66f, .84f, .92f, .84f, .72f, .66f),
            "Bright" to listOf(.44f, .46f, .52f, .62f, .72f, .80f, .86f, .90f, .86f, .80f),
            "Lo-Fi" to listOf(.78f, .70f, .58f, .50f, .48f, .46f, .50f, .56f, .66f, .72f),
            "Acoustic" to listOf(.40f, .48f, .64f, .76f, .82f, .76f, .68f, .60f, .54f, .50f)
        )
    }
    val freePresets = remember { setOf("Flat", "Rock", "Vocal", "Jazz") }

    fun fitPreset(values: List<Float>, count: Int): List<Float> {
        if (count <= 1) return listOf(values.first())
        return List(count) { index ->
            val source = (index.toFloat() / (count - 1)) * (values.size - 1)
            values[source.toInt().coerceIn(0, values.lastIndex)]
        }
    }

    fun refreshFromEngine() {
        attached = eq.bandCount() > 0
        if (attached) {
            levels = eq.normalizedLevels().ifEmpty { levels }
            effects = eq.effectState()
        }
    }

    fun resetAll() {
        eq.applyCustomPreset(fitPreset(List(10) { .5f }, eq.bandCount()))
        eq.setBassBoost(0f)
        eq.setSurround(0f)
        eq.setLoudness(0f)
        eq.setReverb(0f)
        refreshFromEngine()
        message = "Equalizer reset."
    }

    fun applyPreset(name: String, values: List<Float>) {
        if (!attached) {
            message = "Start playback to activate the equalizer."
            return
        }
        val free = name in freePresets
        if (!premiumActive && !free) {
            message = name + " is available with Premium."
            return
        }
        val count = if (premiumActive) eq.bandCount() else minOf(5, eq.bandCount())
        eq.applyCustomPreset(fitPreset(values, count))
        if (name == "DJ Punch" && premiumActive) {
            eq.setBassBoost(.82f)
            eq.setSurround(.66f)
            eq.setLoudness(.42f)
            eq.setReverb(.18f)
        }
        refreshFromEngine()
        message = if (premiumActive) name + " loaded." else name + " loaded in Free mode."
    }

    LaunchedEffect(Unit) {
        refreshFromEngine()
        message = if (attached) "Equalizer ready. Audio effects stay active while you browse."
        else "Play music to activate the equalizer."
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Equalizer",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                if (attached) I18n.t("Live engine connected") else I18n.t("Start playback to connect"),
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .78f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = .18f)
                        ) {
                            Text(
                                if (premiumActive) I18n.t("PREMIUM") else I18n.t("FREE"),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (premiumActive)
                            "All advanced bands and effects are unlocked for this account."
                        else
                            "5-band EQ is free. Premium unlocks the full mixer and effects.",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .86f)
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1015))
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(I18n.t("LIVE EQ"), color = Color.White, fontWeight = FontWeight.Black)
                            Text(
                                if (premiumActive) I18n.t("10-band studio control") else I18n.t("5-band free control"),
                                color = Color.White.copy(alpha = .65f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            "RESET",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { resetAll() }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (!attached) {
                        Box(
                            Modifier.fillMaxWidth().height(260.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.Tune,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(I18n.t("Play a track first"), color = Color.White, fontWeight = FontWeight.Bold)
                                Text(
                                    "Your settings stay active after leaving this page.",
                                    color = Color.White.copy(alpha = .65f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    } else {
                        val visibleCount = if (premiumActive) minOf(levels.size, 10) else minOf(levels.size, 5)
                        Row(
                            Modifier.fillMaxWidth().height(286.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(visibleCount) { index ->
                                val frequency = eq.bandFrequencyHz(index)
                                Column(
                                    Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth().height(218.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            Modifier
                                                .width(4.dp)
                                                .height(192.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF252832))
                                        )
                                        Slider(
                                            value = levels[index],
                                            onValueChange = {
                                                levels = levels.toMutableList().also { list -> list[index] = it }
                                                val range = (eq.upperBound() - eq.lowerBound()).coerceAtLeast(1)
                                                val level = eq.lowerBound() + (range * it).toInt()
                                                eq.setBand(index, level)
                                            },
                                            modifier = Modifier
                                                .width(205.dp)
                                                .height(36.dp)
                                                .graphicsLayer { rotationZ = 270f },
                                            colors = androidx.compose.material3.SliderDefaults.colors(
                                                thumbColor = MaterialTheme.colorScheme.primary,
                                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                                inactiveTrackColor = Color.Transparent
                                            )
                                        )
                                    }
                                    Text(
                                        if (frequency >= 1000) (frequency / 1000).toString() + "k" else frequency.toString(),
                                        color = Color.White.copy(alpha = .58f),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        "%+.1f".format((levels[index] * 2f - 1f) * 12f),
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(presets) { (name, values) ->
                    val free = name in freePresets
                    AssistChip(
                        onClick = { applyPreset(name, values) },
                        label = {
                            Text(if (premiumActive || free) name else name + " • " + I18n.t("Premium"))
                        },
                        leadingIcon = if (!free && !premiumActive) {
                            { Icon(Icons.Filled.Lock, null, modifier = Modifier.size(15.dp)) }
                        } else null
                    )
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "FREE",
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Playback, Flat, Rock, Vocal and Jazz presets plus 5 live bands are free.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (premiumActive)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            Modifier.size(44.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                if (premiumActive) Icons.Filled.CheckCircle else Icons.Filled.Lock,
                                null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (premiumActive) I18n.t("PREMIUM MIXER UNLOCKED") else I18n.t("PREMIUM MIXER"),
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                if (premiumActive)
                                    "Advanced processing is ready."
                                else
                                    "Unlock advanced processing, 10-band control and premium presets.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(I18n.t("Bass Boost"), fontWeight = FontWeight.Bold)
                    Slider(
                        value = effects.bassBoost,
                        enabled = attached && premiumActive,
                        onValueChange = {
                            effects = effects.copy(bassBoost = it)
                            eq.setBassBoost(it)
                        }
                    )

                    Text(I18n.t("3D Space"), fontWeight = FontWeight.Bold)
                    Slider(
                        value = effects.surround,
                        enabled = attached && premiumActive,
                        onValueChange = {
                            effects = effects.copy(surround = it)
                            eq.setSurround(it)
                        }
                    )

                    Text(I18n.t("Loudness"), fontWeight = FontWeight.Bold)
                    Slider(
                        value = effects.loudness,
                        enabled = attached && premiumActive,
                        onValueChange = {
                            effects = effects.copy(loudness = it)
                            eq.setLoudness(it)
                        }
                    )

                    Text(I18n.t("Reverb"), fontWeight = FontWeight.Bold)
                    Slider(
                        value = effects.reverb,
                        enabled = attached && premiumActive,
                        onValueChange = {
                            effects = effects.copy(reverb = it)
                            eq.setReverb(it)
                        }
                    )

                    if (!premiumActive) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onOpenPremium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Star, null)
                            Spacer(Modifier.width(8.dp))
                            Text(I18n.t("Unlock Premium Equalizer"))
                        }
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Bass Boost • 3D Space • Loudness • Reverb • 10-band mixer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun PremiumScreen(
    modifier: Modifier,
    account: AccountSnapshot?,
    premium: PremiumSnapshot,
    processing: Boolean,
    onAccount: () -> Unit,
    onRefresh: () -> Unit,
    onPurchase: (PremiumPlan) -> Unit,
    quarterlyCheckoutReady: Boolean,
    lifetimeCheckoutReady: Boolean,
    refreshToken: Int
) {
    val premiumActive = premium.cloudSynced && premium.verified && when (premium.plan) {
        PremiumPlan.LIFETIME -> true
        PremiumPlan.QUARTERLY ->
            premium.expiresAtMillis == null || premium.expiresAtMillis > System.currentTimeMillis()
        PremiumPlan.NONE -> false
    }
    val context = LocalContext.current
    val hasPendingPurchase = remember(refreshToken) {
        PayPalCheckout.loadPending(context) != null
    }

    val pulse = rememberInfiniteTransition(label = "premium_page").animateFloat(
        initialValue = 0.94f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "premiumPulse"
    ).value

    LaunchedEffect(refreshToken) { }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (processing) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(I18n.t("Processing"), fontWeight = FontWeight.ExtraBold)
                                Text(
                                    "Checking PayPal status and synchronizing your Premium access.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }

        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            ),
                            RoundedCornerShape(32.dp)
                        )
                        .padding(22.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                Modifier.size(62.dp).graphicsLayer {
                                    scaleX = if (premiumActive) pulse else 1f
                                    scaleY = if (premiumActive) pulse else 1f
                                },
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.20f)
                            ) {
                                Icon(
                                    if (premiumActive) Icons.Filled.CheckCircle else Icons.Filled.Star,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (premiumActive) I18n.t("PREMIUM ACTIVE") else I18n.t("EQUALIZER PREMIUM"),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    if (premiumActive) I18n.t("Advanced equalizer unlocked") else I18n.t("Unlock advanced equalizer controls"),
                                    color = Color.White,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        if (account != null) {
                            Text(
                                "Signed in as " + (account.email ?: account.displayName.orEmpty()),
                                color = Color.White.copy(alpha = 0.88f)
                            )
                        } else {
                            Text(
                                "Sign in to connect purchases to your Music Player account.",
                                color = Color.White.copy(alpha = 0.88f)
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = onAccount,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(I18n.t("Sign in / create account"), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (premiumActive) {
            item {
                PremiumUnlockedCard(premium)
            }
            item {
                Text(
                    "Advanced Equalizer Premium",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            item {
                Card(shape = RoundedCornerShape(28.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        PremiumFeatureTile(I18n.t("Advanced equalizer"), I18n.t("Full-band live frequency control."), Icons.Filled.Tune)
                        PremiumFeatureTile(I18n.t("Bass Boost"), I18n.t("Premium low-end enhancement inside the equalizer."), Icons.Filled.VolumeUp)
                        PremiumFeatureTile(I18n.t("3D Space"), I18n.t("Premium spatial effect inside the equalizer."), Icons.Filled.Repeat)
                        PremiumFeatureTile(I18n.t("Loudness"), I18n.t("Premium gain enhancement inside the equalizer."), Icons.Filled.VolumeUp)
                        PremiumFeatureTile(I18n.t("DJ preset library"), I18n.t("Club, Deep Bass, DJ Punch, Hip-Hop, EDM, Bright and Lo-Fi."), Icons.Filled.Star)
                        PremiumFeatureTile(I18n.t("Verified access"), I18n.t("Your Premium equalizer entitlement is linked to your account."), Icons.Filled.AccountCircle)
                    }
                }
            }
            item {
                Card(
                    Modifier.fillMaxWidth().clickable(onClick = onRefresh),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Refresh, null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(I18n.t("Verify Premium again"), fontWeight = FontWeight.ExtraBold)
                            Text(I18n.t("Refresh your PayPal entitlement now."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    "Choose your plan",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            item {
                PlanCard(
                    "Quarterly",
                    "$5",
                    "Every 3 months",
                    true,
                    quarterlyCheckoutReady && !hasPendingPurchase && !processing
                ) { onPurchase(PremiumPlan.QUARTERLY) }
            }
            item {
                PlanCard(
                    "Lifetime",
                    "$30",
                    "One-time payment • permanent",
                    false,
                    lifetimeCheckoutReady && !hasPendingPurchase && !processing
                ) { onPurchase(PremiumPlan.LIFETIME) }
            }
            if (hasPendingPurchase) {
                item {
                    Card(
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text(
                                if (processing) I18n.t("Processing payment") else I18n.t("Payment waiting for verification"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                if (processing)
                                    "PayPal payment is being checked. Keep this purchase pending until confirmation."
                                else
                                    "Your PayPal checkout was started. Verify it here if the browser did not return to the app.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = onRefresh,
                                enabled = !processing,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Refresh, null)
                                Spacer(Modifier.width(8.dp))
                                Text(I18n.t("Verify Premium payment"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumUnlockedCard(premium: PremiumSnapshot) {
    val accent = MaterialTheme.colorScheme.primary
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(48.dp), shape = CircleShape, color = accent) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(I18n.t("VERIFIED PURCHASE"), style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (premium.plan == PremiumPlan.LIFETIME) I18n.t("Lifetime Premium") else I18n.t("Quarterly Premium"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                AnimatedBars(true)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (premium.plan == PremiumPlan.LIFETIME)
                    "Permanent access to every Premium feature."
                else
                    "Premium access is active and will renew automatically."
            )
            premium.expiresAtMillis?.takeIf { it > 0L }?.let {
                Spacer(Modifier.height(5.dp))
                Text(
                    "Next billing: " + java.text.DateFormat.getDateInstance().format(java.util.Date(it)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PremiumFeatureTile(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(Modifier.size(38.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(icon, null, modifier = Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
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
    val transition = rememberInfiniteTransition(label = "plan_card")
    val scale by transition.animateFloat(
        0.985f,
        1f,
        infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "planScale"
    )

    Card(
        Modifier.fillMaxWidth().graphicsLayer {
            scaleX = if (highlight) scale else 1f
            scaleY = if (highlight) scale else 1f
        }.clickable(enabled = checkoutReady, onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                Modifier.size(54.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Filled.Star,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(14.dp)
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (highlight) {
                        Spacer(Modifier.width(7.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                "POPULAR",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    detail,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    if (checkoutReady) "Secure PayPal checkout • Tap to continue"
                    else "Checkout unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (checkoutReady)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    price,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    if (checkoutReady) I18n.t("PayPal") else I18n.t("Offline"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AccountScreen(
    modifier: Modifier,
    account: AccountSnapshot?,
    premium: PremiumSnapshot,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onPremium: () -> Unit,
    refreshToken: Int
) {
    LaunchedEffect(refreshToken) { }

    val premiumActive = premium.cloudSynced && premium.verified && when (premium.plan) {
        PremiumPlan.LIFETIME -> true
        PremiumPlan.QUARTERLY ->
            premium.expiresAtMillis == null || premium.expiresAtMillis > System.currentTimeMillis()
        PremiumPlan.NONE -> false
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
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
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            ),
                            RoundedCornerShape(30.dp)
                        )
                        .padding(22.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            Modifier.size(68.dp),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = .18f)
                        ) {
                            Icon(
                                Icons.Filled.AccountCircle,
                                null,
                                tint = Color.White,
                                modifier = Modifier.padding(12.dp)                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (premiumActive) I18n.t("PRO ACCOUNT") else I18n.t("MUSIC PLAYER ACCOUNT"),
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                account?.displayName?.takeIf { it.isNotBlank() }
                                    ?: account?.email?.substringBefore("@")
                                    ?: "Your personal music space",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                if (account != null) account.email.orEmpty()
                                else "Sync Premium purchases and settings.",
                                color = Color.White.copy(alpha = .85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (account == null) {
            item {
                Card(shape = RoundedCornerShape(26.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text(I18n.t("Private & local-first"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Your local music library stays on the device. An account is only needed for Premium purchases and cloud entitlement.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Person, null)
                            Spacer(Modifier.width(8.dp))
                            Text(I18n.t("Sign in / create account"))
                        }
                    }
                }
            }
        } else {
            item {
                Card(shape = RoundedCornerShape(26.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text(I18n.t("Account status"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                Modifier.size(44.dp),
                                shape = CircleShape,
                                color = if (premiumActive)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Icon(
                                    if (premiumActive) Icons.Filled.CheckCircle else Icons.Filled.Person,
                                    null,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (premiumActive) I18n.t("Premium account") else I18n.t("Free account"),
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    when (premium.plan) {
                                        PremiumPlan.LIFETIME -> "Lifetime Premium"
                                        PremiumPlan.QUARTERLY -> "Quarterly Premium"
                                        PremiumPlan.NONE -> "No active Premium plan"
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(26.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text(I18n.t("Premium control center"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Manage advanced equalizer controls and your verified PayPal entitlement.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onPremium, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Star, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (premiumActive) I18n.t("Open Premium") else I18n.t("Explore Premium"))
                        }
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(26.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text(I18n.t("Security"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            if (premiumActive)
                                "Your Premium entitlement is verified against the account linked to your purchase."
                            else
                                "Your account is ready for Premium purchases. Verified entitlements appear here automatically.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onSignOut) { Text(I18n.t("Sign out")) }
                    }
                }
            }
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
        title = { Text(if (current == null) I18n.t("Optional account") else I18n.t("Your account"), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (current != null) {
                    Text(current.email ?: current.displayName ?: current.uid)
                    Text(I18n.t("Account is used to sync premium entitlement."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(I18n.t("Music Player works without an account. Sign in only when you need premium purchases and cloud entitlement."))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(I18n.t("Email")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = configured && !busy
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(I18n.t("Password")) },
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
                    ) { Text(I18n.t("Sign in")) }

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
                    ) { Text(I18n.t("Create account")) }

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
                    ) { Text(I18n.t("Continue with Google")) }

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
                TextButton(onClick = { repo.signOut(); onDismiss() }) { Text(I18n.t("Sign out")) }
            } else {
                TextButton(onClick = onDismiss) { Text(I18n.t("Continue without account")) }
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
    languageName: String,
    onThemeChange: (AppThemeStyle) -> Unit,
    onBackgroundChange: (AppBackgroundStyle) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onOpenPremium: () -> Unit,
    onAccount: () -> Unit,
    notificationsAllowed: Boolean,
    onRequestNotifications: () -> Unit,
    checkingUpdate: Boolean,
    onCheckUpdates: () -> Unit
) {
    var autoScan by rememberSaveable { mutableStateOf(true) }
    val selectedTheme = runCatching { AppThemeStyle.valueOf(themeName) }.getOrDefault(AppThemeStyle.VIOLET)
    val selectedBackground = runCatching { AppBackgroundStyle.valueOf(backgroundName) }.getOrDefault(AppBackgroundStyle.GRADIENT)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsSection(I18n.t("Account"), I18n.t("Optional. Used for premium purchases and cloud entitlement.")) {
                Button(onClick = onAccount, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Person, null)
                    Spacer(Modifier.width(8.dp))
                    Text(I18n.t("Manage account"))
                }
            }
        }
        item {
            SettingsSection(I18n.t("Premium"), I18n.t("Quarterly $5 or Lifetime $30.")) {
                Button(onClick = onOpenPremium, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Star, null)
                    Spacer(Modifier.width(8.dp))
                    Text(I18n.t("Open Premium"))
                }
            }
        }
        item {
            SettingsSection(I18n.t("Library"), I18n.t("Local songs stay on the device.")) {
                SwitchRow(I18n.t("Auto-scan new music"), I18n.t("Refresh the library when the app opens."), autoScan) {
                    autoScan = it
                    if (it) vm.scan()
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = vm::scan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text(I18n.t("Scan library now"))
                }
            }
        }
        item {
            SettingsSection(I18n.t("Appearance"), I18n.t("Choose the visual identity, background and light/dark mode.")) {
                Text(I18n.t("Theme"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                ThemeChoices(selectedTheme, onThemeChange)
                Spacer(Modifier.height(13.dp))
                Text(I18n.t("Background"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                BackgroundChoices(selectedBackground, onBackgroundChange)
                Spacer(Modifier.height(5.dp))
                SwitchRow(I18n.t("Dark mode"), I18n.t("Use the darker color system."), darkMode, onDarkMode)
            }
        }
        item {
            SettingsSection(I18n.t("Language"), I18n.t("Choose the interface language.")) {
                Text(I18n.t("Language"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                LanguageChoices(
                    selected = runCatching { AppLanguage.valueOf(languageName) }.getOrDefault(AppLanguage.ENGLISH),
                    onSelected = onLanguageChange
                )
            }
        }
        item {
            SettingsSection(I18n.t("Notifications"), I18n.t("Playback and update notifications.")) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (notificationsAllowed) I18n.t("Notifications enabled") else I18n.t("Notifications disabled"),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (notificationsAllowed)
                                I18n.t("Music Player can automatically report updates and playback status.")
                            else
                                I18n.t("Allow notifications so automatic update checks can alert you.")
                        )
                    }
                    Surface(
                        Modifier.size(36.dp),
                        shape = CircleShape,
                        color = if (notificationsAllowed)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Icon(
                            if (notificationsAllowed) Icons.Filled.CheckCircle else Icons.Filled.Update,
                            null,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onRequestNotifications,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (notificationsAllowed) I18n.t("Open notification settings") else I18n.t("Enable notifications"))
                }
            }
        }
        item {
            SettingsSection(I18n.t("Updates"), I18n.t("Keep Music Player current.")) {
                Button(
                    onClick = onCheckUpdates,
                    enabled = !checkingUpdate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (checkingUpdate) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(I18n.t("Checking for updates…"))
                    } else {
                        Icon(Icons.Filled.Update, null)
                        Spacer(Modifier.width(8.dp))
                        Text(I18n.t("Check for updates"))
                    }
                }
            }
        }
        item {
            SettingsSection(I18n.t("About"), I18n.t("Music Player is a local-first Android audio player.")) {
                Text(I18n.t("Music Player ") + BuildConfig.VERSION_NAME, fontWeight = FontWeight.Bold)
                Text(I18n.t("Offline-first playback powered by AndroidX Media3."), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                label = { Text(I18n.t(theme.label)) },
                leadingIcon = {
                    Surface(
                        Modifier.size(16.dp),
                        shape = CircleShape,
                        color = themeColors(theme, false).primary
                    ) {}
                }
            )
        }
    }
}

@Composable
private fun LanguageChoices(selected: AppLanguage, onSelected: (AppLanguage) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(AppLanguage.values().toList()) { language ->
            AssistChip(
                onClick = { onSelected(language) },
                label = { Text(language.label) }
            )
        }
    }
}

@Composable
private fun BackgroundChoices(selected: AppBackgroundStyle, onSelected: (AppBackgroundStyle) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(AppBackgroundStyle.values().toList()) { background ->
            AssistChip(onClick = { onSelected(background) }, label = { Text(I18n.t(background.label)) })
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
private fun DrivingModeDialog(
    song: Song,
    playing: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                Modifier.fillMaxSize().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(I18n.t("Driving mode"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(I18n.t("Close")) }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Artwork(song, Modifier.size(250.dp))
                    Spacer(Modifier.height(22.dp))
                    Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(72.dp)) {
                        Icon(Icons.Filled.SkipPrevious, I18n.t("Previous track"), modifier = Modifier.size(42.dp))
                    }
                    IconButton(onClick = onPlayPause, modifier = Modifier.size(88.dp)) {
                        Surface(Modifier.fillMaxSize(), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(20.dp))
                        }
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(72.dp)) {
                        Icon(Icons.Filled.SkipNext, I18n.t("Next track"), modifier = Modifier.size(42.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FirstRunTutorial(onFinish: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf("Welcome to Music Player", "Your music", "Professional audio", "Premium & updates")
    val details = listOf(
        "A local-first player designed for fast, private playback.",
        "Scan your device, search your library and use the mini player from anywhere.",
        "Use the equalizer, playback effects, driving mode and persistent audio settings.",
        "Premium unlocks advanced audio processing. PayPal verification and updates are handled securely."
    )
    AlertDialog(
        onDismissRequest = {},
        title = { Text(I18n.t(titles[page]), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(I18n.t(details[page]))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    repeat(titles.size) { index ->
                        Surface(
                            Modifier.padding(horizontal = 4.dp).size(if (index == page) 10.dp else 7.dp),
                            shape = CircleShape,
                            color = if (index == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ) {}
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (page == titles.lastIndex) onFinish() else page++
            }) {
                Text(I18n.t(if (page == titles.lastIndex) "Get started" else "Next"))
            }
        }
    )
}

@Composable
private fun AppearanceSetupDialog(
    theme: AppThemeStyle,
    background: AppBackgroundStyle,
    darkMode: Boolean,
    language: AppLanguage,
    onThemeChange: (AppThemeStyle) -> Unit,
    onBackgroundChange: (AppBackgroundStyle) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(I18n.t("Make Music Player yours"), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(I18n.t("Choose your theme and background before you start."))
                Text(I18n.t("Theme"), fontWeight = FontWeight.Bold)
                ThemeChoices(theme, onThemeChange)
                Text(I18n.t("Background"), fontWeight = FontWeight.Bold)
                BackgroundChoices(background, onBackgroundChange)
                Spacer(Modifier.height(6.dp))
                Text(I18n.t("Language"), fontWeight = FontWeight.Bold)
                LanguageChoices(language, onLanguageChange)
                Spacer(Modifier.height(6.dp))
                SwitchRow(I18n.t("Dark mode"), I18n.t("You can change this later in Settings."), darkMode, onDarkModeChange)
            }
        },
        confirmButton = { Button(onClick = onSave) { Text(I18n.t("Continue")) } }
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
private fun PremiumHomeBadge() {
    val transition = rememberInfiniteTransition(label = "premium_home_badge")
    val alpha by transition.animateFloat(
        0.55f,
        1f,
        infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "premiumBadgeAlpha"
    )

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                Modifier.size(42.dp).alpha(alpha),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Filled.Star,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Text(I18n.t("Premium active"), fontWeight = FontWeight.ExtraBold)
                Text(
                    "Ad-free playback • unlimited effects",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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
    error: String,
    onDismiss: () -> Unit,
    onInstall: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(I18n.t("Update available")) },
        text = {
            Column {
                Text(I18n.t("Music Player ") + info.versionName)
                Spacer(Modifier.height(7.dp))
                Text(info.changelog, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (updating) {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(I18n.t("$progress%"), modifier = Modifier.padding(top = 6.dp))
                }
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = { TextButton(onClick = onInstall, enabled = !updating) { Text(I18n.t("Download & install")) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !updating) { Text(I18n.t("Later")) } }
    )
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}