package com.atomvpn.mobile

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uacspoofer.mobile.core.ConnectionState
import com.uacspoofer.mobile.core.ConnectionStateStore
import com.uacspoofer.mobile.engine.pow.*
import com.uacspoofer.mobile.settings.AdvancedSettingsStore
import com.uacspoofer.mobile.settings.NetworkGuardStore
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@Immutable
private class AtomPalette(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val cardRaised: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val error: Color,
    val warn: Color
)

private val DarkAtom = AtomPalette(
    dark = true,
    bg = Color(0xFF151413),
    card = Color(0xFF211F1D),
    cardRaised = Color(0xFF2B2825),
    line = Color(0x17FFFFFF),
    text = Color(0xFFF4F0E8),
    muted = Color(0xFF948F86),
    accent = Color(0xFFFF8A3D),
    onAccent = Color(0xFF1A1210),
    error = Color(0xFFFF5C7A),
    warn = Color(0xFFE8B04A)
)

private val LightAtom = AtomPalette(
    dark = false,
    bg = Color(0xFFF6F2EB),
    card = Color(0xFFFFFFFF),
    cardRaised = Color(0xFFEFE9DF),
    line = Color(0x1F1C1A17),
    text = Color(0xFF1C1A17),
    muted = Color(0xFF7B756B),
    accent = Color(0xFFE8590C),
    onAccent = Color(0xFFFFFFFF),
    error = Color(0xFFC81E3A),
    warn = Color(0xFFB7791F)
)

private val LocalAtom = staticCompositionLocalOf { DarkAtom }

// این‌ها فقط داخل Composable خوانده می‌شوند و با تم گوشی (تیره/روشن) خودکار عوض می‌شوند
private val Bg: Color @Composable @ReadOnlyComposable get() = LocalAtom.current.bg
private val Panel: Color @Composable @ReadOnlyComposable get() = LocalAtom.current.card
private val PanelRaised: Color @Composable @ReadOnlyComposable get() = LocalAtom.current.cardRaised
private val Line: Color @Composable @ReadOnlyComposable get() = LocalAtom.current.line
private val Ink: Color @Composable @ReadOnlyComposable get() = LocalAtom.current.text
private val Muted: Color @Composable @ReadOnlyComposable get() = LocalAtom.current.muted
private val Accent: Color @Composable @ReadOnlyComposable get() = LocalAtom.current.accent
private val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalAtom.current.onAccent
private val ErrorC: Color @Composable @ReadOnlyComposable get() = LocalAtom.current.error
private val Warn: Color @Composable @ReadOnlyComposable get() = LocalAtom.current.warn

private enum class UiState { IDLE, CONNECTING, CONNECTED, FAILED }

private fun pageDepth(page: String) = when (page) {
    "settings" -> 1
    "countries" -> 2
    else -> 0
}

class MainActivity : ComponentActivity() {
    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK || VpnService.prepare(this) == null) {
            startService(Intent(this, PowVpnService::class.java).setAction(PowVpnService.ACTION_CONNECT))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AtomPowApp(
            onConnect = {
                val intent = VpnService.prepare(this)
                if (intent == null) startService(Intent(this, PowVpnService::class.java).setAction(PowVpnService.ACTION_CONNECT))
                else vpnPermission.launch(intent)
            },
            onDisconnect = {
                startService(Intent(this, PowVpnService::class.java).setAction(PowVpnService.ACTION_DISCONNECT))
            }
        ) }
    }
}

// ───────────────────────────── Root ─────────────────────────────

@Composable
private fun AtomPowApp(onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val context = LocalContext.current
    val connection by ConnectionStateStore.state.collectAsStateWithLifecycle()
    val status by PowStatusStore.status.collectAsStateWithLifecycle()
    val exitIpState by ExitIpInfoRepository.state.collectAsStateWithLifecycle()
    val trafficSpeedState by TrafficSpeedRepository.state.collectAsStateWithLifecycle()
    val engine = remember(context) { PowEngineStore.get(context) }
    val settings by engine.settings.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf("home") }
    var countryQuery by remember { mutableStateOf("") }

    val connected = connection == ConnectionState.CONNECTED
    val exitCountryLabel = when {
        !connected -> "—"
        status.exitRegion.isNotBlank() -> PowRegions.name(status.exitRegion, Locale("fa")).ifBlank { status.exitRegion }
        !exitIpState.info?.countryCode.isNullOrBlank() -> PowRegions.name(exitIpState.info!!.countryCode, Locale("fa")).ifBlank { exitIpState.info!!.countryCode }
        exitIpState.isLoading -> "در حال شناسایی…"
        else -> "نامشخص"
    }
    val connecting = connection == ConnectionState.CONNECTING ||
        status.phase in setOf(PowPhase.STARTING, PowPhase.OUTER, PowPhase.INNER, PowPhase.BRIDGING)
    // پیام "Job was cancelled" یعنی کاربر خودش لغو کرده؛ شکست حساب نمی‌شود
    val cancelled = status.detail.contains("cancel", ignoreCase = true)
    val failed = (connection == ConnectionState.ERROR || status.phase == PowPhase.FAILED) && !cancelled
    val ui = when {
        connected -> UiState.CONNECTED
        connecting -> UiState.CONNECTING
        failed -> UiState.FAILED
        else -> UiState.IDLE
    }

    val exitCountryCode = status.exitRegion.ifBlank { exitIpState.info?.countryCode.orEmpty() }

    // زمان شروع اتصال؛ تایمر صفحه‌ی اصلی از روی آن شمرده می‌شود
    var connectedAt by remember { mutableStateOf(0L) }
    LaunchedEffect(connected) { connectedAt = if (connected) System.currentTimeMillis() else 0L }

    // دکمه Back سیستم: قبلاً نبود و برنامه مستقیم بسته می‌شد
    BackHandler(enabled = page != "home") {
        page = if (page == "countries") "settings" else "home"
    }

    val atom = if (isSystemInDarkTheme()) DarkAtom else LightAtom
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = atom.bg.toArgb()
        window.navigationBarColor = atom.bg.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !atom.dark
            isAppearanceLightNavigationBars = !atom.dark
        }
    }
    val scheme = if (atom.dark) {
        darkColorScheme(
            primary = atom.accent, onPrimary = atom.onAccent, background = atom.bg, onBackground = atom.text,
            surface = atom.card, onSurface = atom.text, surfaceVariant = atom.cardRaised,
            onSurfaceVariant = atom.muted, outline = atom.line, error = atom.error
        )
    } else {
        lightColorScheme(
            primary = atom.accent, onPrimary = atom.onAccent, background = atom.bg, onBackground = atom.text,
            surface = atom.card, onSurface = atom.text, surfaceVariant = atom.cardRaised,
            onSurfaceVariant = atom.muted, outline = atom.line, error = atom.error
        )
    }

    MaterialTheme(colorScheme = scheme) {
        // رنگ متن پیش‌فرض بیرون از Card هم از تم گوشی می‌آید
        CompositionLocalProvider(LocalAtom provides atom, LocalContentColor provides atom.text) {
            Box(Modifier.fillMaxSize().background(atom.bg)) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val dir = if (pageDepth(targetState) > pageDepth(initialState)) 1 else -1
                        val ease = FastOutSlowInEasing
                        (fadeIn(tween(300, delayMillis = 70, easing = ease)) +
                            slideInHorizontally(tween(380, easing = ease)) { (it * 0.14f * dir).toInt() }) togetherWith
                            (fadeOut(tween(150)) +
                                slideOutHorizontally(tween(380, easing = ease)) { (-it * 0.14f * dir).toInt() })
                    },
                    label = "page-transition"
                ) { currentPage ->
                    when (currentPage) {
                        "settings" -> PowSettingsPage(
                            settings = settings,
                            onBack = { page = "home" },
                            onOpenCountries = { page = "countries" },
                            onSave = { engine.save(it) }
                        )
                        "countries" -> {
                            val countries = remember { PowRegions.options(engine.availableRegions()) }
                            val faLocale = remember { Locale("fa") }
                            // نام‌ها یک بار محاسبه می‌شوند، نه در هر فریم اسکرول
                            val names = remember(countries) { countries.associateWith { PowRegions.name(it, faLocale) } }
                            val filtered = remember(countries, countryQuery) {
                                countries.filter { PowRegions.matches(it, countryQuery) }
                            }
                            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(18.dp)) {
                                ScreenHeader("کشور خروجی", "انتخاب مسیر خروجی PoW") { page = "settings" }
                                OutlinedTextField(
                                    value = countryQuery,
                                    onValueChange = { countryQuery = it },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    placeholder = { Text("جستجوی کشور", color = Muted) },
                                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Muted) },
                                    trailingIcon = {
                                        AnimatedVisibility(countryQuery.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                                            IconButton(onClick = { countryQuery = "" }) {
                                                Icon(Icons.Outlined.Close, "پاک کردن", tint = Muted)
                                            }
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Accent,
                                        unfocusedBorderColor = Line,
                                        cursorColor = Accent,
                                        focusedContainerColor = Panel,
                                        unfocusedContainerColor = Panel
                                    )
                                )
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 16.dp)
                                ) {
                                    item(key = "auto-row") {
                                        CountryRow("خودکار", "AUTO", settings.exitCountryCode.isBlank()) {
                                            engine.save(settings.copy(exitCountryCode = "").validated())
                                            page = "settings"
                                        }
                                    }
                                    items(filtered, key = { it }) { code ->
                                        CountryRow(names[code] ?: code, code, settings.exitCountryCode == code) {
                                            engine.save(settings.copy(exitCountryCode = code).validated())
                                            page = "settings"
                                        }
                                    }
                                    if (filtered.isEmpty()) {
                                        item(key = "empty-row") {
                                            Text(
                                                "کشوری پیدا نشد",
                                                color = Muted,
                                                fontSize = 13.sp,
                                                modifier = Modifier.fillMaxWidth().padding(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        else -> HomePage(
                            ui = ui,
                            progress = status.progressPercent / 100f,
                            detail = if (cancelled && !connecting && !connected) "اتصال لغو شد" else status.detail,
                            settings = settings,
                            exitCountryLabel = exitCountryLabel,
                            exitCountryCode = exitCountryCode,
                            exitIpState = exitIpState,
                            trafficSpeedState = trafficSpeedState,
                            connectedAt = connectedAt,
                            onConnect = onConnect,
                            onDisconnect = onDisconnect,
                            onOpenSettings = { page = "settings" },
                            onOpenCountries = { page = "countries" }
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Logo ─────────────────────────────

/** لوگوی Letter A: حرف A که هسته‌ی اتم نقش خط افقی آن را دارد. */
@Composable
private fun AtomMark(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 64f
        val path = Path().apply {
            moveTo(15f * s, 50f * s)
            lineTo(32f * s, 13f * s)
            lineTo(49f * s, 50f * s)
        }
        drawPath(path, tint, style = Stroke(width = 5.5f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(tint, radius = 5.5f * s, center = Offset(32f * s, 40f * s))
    }
}

@Composable
private fun AtomLogoTile(size: Dp) {
    Box(
        Modifier.size(size).background(Accent, RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        AtomMark(OnAccent, Modifier.fillMaxSize(0.64f))
    }
}

// ───────────────────────────── Home ─────────────────────────────

@Composable
private fun HomePage(
    ui: UiState,
    progress: Float,
    detail: String,
    settings: PowEngineSettings,
    exitCountryLabel: String,
    exitCountryCode: String,
    exitIpState: ExitIpInfoState,
    trafficSpeedState: TrafficSpeedState,
    connectedAt: Long,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCountries: () -> Unit
) {
    val connected = ui == UiState.CONNECTED
    val connecting = ui == UiState.CONNECTING
    val active = connected || connecting

    val statusColor by animateColorAsState(
        when (ui) {
            UiState.CONNECTED -> Accent
            UiState.CONNECTING -> Warn
            UiState.FAILED -> ErrorC
            UiState.IDLE -> Muted
        },
        tween(400), label = "status-color"
    )
    val animatedProgress by animateFloatAsState(
        progress.coerceIn(0f, 1f), tween(500, easing = FastOutSlowInEasing), label = "progress"
    )
    val percent = (progress.coerceIn(0f, 1f) * 100).roundToInt()
    val routeLabel = when (settings.exitMode) {
        PowEngineSettings.EXIT_WARP -> "WARP مستقیم"
        PowEngineSettings.EXIT_PSIPHON -> "Psiphon مستقیم"
        else -> "Psiphon روی WARP"
    }
    val section = when (ui) {
        UiState.CONNECTED -> 1
        UiState.FAILED -> 2
        else -> 0
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Reveal(0) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AtomLogoTile(36.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "ATOM",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 3.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Tune, "تنظیمات", tint = Ink)
                }
            }
        }
        Spacer(Modifier.height(28.dp))

        Reveal(1) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(
                    targetState = ui,
                    transitionSpec = { fadeIn(tween(240, delayMillis = 60)) togetherWith fadeOut(tween(140)) },
                    label = "status-text"
                ) { s ->
                    Text(
                        when (s) {
                            UiState.CONNECTED -> "متصل"
                            UiState.CONNECTING -> "در حال برقراری اتصال"
                            UiState.FAILED -> "اتصال ناموفق بود"
                            UiState.IDLE -> "آماده برای اتصال"
                        },
                        color = statusColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(2.dp))
                SessionTimer(connectedAt = connectedAt, active = connected)
                Spacer(Modifier.height(14.dp))
                ConnectButton(ui = ui) {
                    if (active) onDisconnect() else onConnect()
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    when (ui) {
                        UiState.CONNECTED -> "برای قطع اتصال لمس کنید"
                        UiState.CONNECTING -> "در حال اتصال · ${percent}٪ · برای لغو لمس کنید"
                        UiState.FAILED -> "برای تلاش دوباره لمس کنید"
                        UiState.IDLE -> "برای اتصال لمس کنید"
                    },
                    color = Muted,
                    fontSize = 12.sp
                )
                AnimatedVisibility(connecting) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.padding(top = 12.dp).fillMaxWidth(0.55f).height(4.dp).clip(CircleShape),
                        color = Accent,
                        trackColor = Line
                    )
                }
                if (ui != UiState.FAILED) {
                    AnimatedContent(
                        targetState = detail.ifBlank { "موتور اتصال آماده است" },
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                        label = "detail-text"
                    ) { text ->
                        Text(
                            text,
                            color = Muted,
                            fontSize = 11.sp,
                            maxLines = 2,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        Reveal(2) {
            AnimatedContent(
                targetState = section,
                transitionSpec = { fadeIn(tween(300, delayMillis = 80)) togetherWith fadeOut(tween(150)) },
                label = "home-section"
            ) { s ->
                when (s) {
                    1 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CountryIpCard(
                            countryLabel = exitCountryLabel,
                            countryCode = exitCountryCode,
                            routeLabel = routeLabel,
                            ipState = exitIpState
                        )
                        SpeedSection(trafficSpeedState)
                    }
                    2 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Panel),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, ErrorC.copy(alpha = .6f), RoundedCornerShape(16.dp))
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Warning, null, tint = ErrorC, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("اتصال برقرار نشد", color = ErrorC, fontWeight = FontWeight.Medium)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    detail.ifBlank { "پاسخی از مسیر انتخابی دریافت نشد. دوباره تلاش کن یا روش اتصال را عوض کن." },
                                    color = Muted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        InfoTilesRow(settings, onOpenCountries, onOpenSettings)
                    }
                    else -> InfoTilesRow(settings, onOpenCountries, onOpenSettings)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("ATOM  •  PRIVATE NETWORK", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun InfoTilesRow(settings: PowEngineSettings, onOpenCountries: () -> Unit, onOpenSettings: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        InfoTile("کشور خروجی", settings.exitCountryCode.ifBlank { "خودکار" }, Modifier.weight(1f), Icons.Outlined.Public) { onOpenCountries() }
        InfoTile("پروتکل اتصال", settings.linkRecipe.uppercase(), Modifier.weight(1f), Icons.Outlined.Hub) { onOpenSettings() }
    }
}

/** تایمر فقط خودش هر ثانیه بازترکیب می‌شود، نه کل صفحه. */
@Composable
private fun SessionTimer(connectedAt: Long, active: Boolean) {
    val seconds by produceState(0L, connectedAt) {
        if (connectedAt <= 0L) {
            value = 0L
            return@produceState
        }
        while (true) {
            value = (System.currentTimeMillis() - connectedAt) / 1000
            delay(1000)
        }
    }
    val textColor by animateColorAsState(if (active) Ink else Muted, tween(400), label = "timer-color")
    Text(
        String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60),
        color = textColor,
        fontSize = 40.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp,
        style = TextStyle(fontFeatureSettings = "tnum")
    )
}

// ───────────────────── Country + IP, speed ─────────────────────

@Composable
private fun CountryIpCard(countryLabel: String, countryCode: String, routeLabel: String, ipState: ExitIpInfoState) {
    val clipboard = LocalClipboardManager.current
    var revealed by rememberSaveable { mutableStateOf(true) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1200)
            copied = false
        }
    }
    val ip = ipState.info?.ipAddress
    val ipText = when {
        ip != null -> if (revealed) ip else maskIp(ip)
        ipState.isLoading -> "در حال دریافت…"
        ipState.errorMessage != null -> "ناموفق: ${ipState.errorMessage}"
        else -> "IP در دسترس نیست"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, Line, RoundedCornerShape(16.dp))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).border(1.5.dp, Accent, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        countryCode.uppercase().take(2).ifBlank { "··" },
                        color = Accent,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(countryLabel, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(routeLabel, color = Muted, fontSize = 12.sp)
                }
                if (ipState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Line))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("IP", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    ipText,
                    modifier = Modifier.weight(1f),
                    color = if (ip != null) Ink else Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = { revealed = !revealed }, enabled = ip != null, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (revealed) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        "نمایش یا مخفی کردن IP", tint = Muted, modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { ip?.let { clipboard.setText(AnnotatedString(it)); copied = true } },
                    enabled = ip != null,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                        "کپی IP", tint = if (copied) Accent else Muted, modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun maskIp(ip: String): String {
    val v4 = ip.split('.')
    if (v4.size == 4) return "${v4[0]}.${v4[1]}.•••.•••"
    return ip.take(9) + ":••••:••••"
}

@Composable
private fun SpeedSection(state: TrafficSpeedState) {
    val latest by rememberUpdatedState(state)
    val samples = 20
    val down = remember { mutableStateListOf<Long>().apply { repeat(samples) { add(0L) } } }
    val up = remember { mutableStateListOf<Long>().apply { repeat(samples) { add(0L) } } }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            down.removeAt(0); down.add(latest.downloadBytesPerSecond)
            up.removeAt(0); up.add(latest.uploadBytesPerSecond)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        SpeedTile("دانلود", Icons.Outlined.ArrowDownward, Accent, state.downloadBytesPerSecond, down, Accent, Modifier.weight(1f))
        SpeedTile("آپلود", Icons.Outlined.ArrowUpward, Ink, state.uploadBytesPerSecond, up, Muted, Modifier.weight(1f))
    }
}

@Composable
private fun SpeedTile(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    bytesPerSecond: Long,
    history: List<Long>,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val (number, unit) = rateParts(bytesPerSecond)
    Column(
        modifier
            .background(Panel, RoundedCornerShape(16.dp))
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(title, color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(number, fontSize = 24.sp, fontWeight = FontWeight.Medium, style = TextStyle(fontFeatureSettings = "tnum"))
            Spacer(Modifier.width(3.dp))
            Text(unit, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        SpeedBars(history, barColor, Modifier.fillMaxWidth().height(30.dp))
    }
}

/** میله‌ها فقط در مرحله‌ی draw خوانده می‌شوند. */
@Composable
private fun SpeedBars(history: List<Long>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = history.size
        if (n == 0) return@Canvas
        val gap = 2.dp.toPx()
        val barW = (size.width - gap * (n - 1)) / n
        val max = (history.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
        for (i in 0 until n) {
            val h = (history[i] / max).coerceIn(0.08f, 1f) * size.height
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barW + gap), size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
    }
}

private fun rateParts(bytesPerSecond: Long): Pair<String, String> = when {
    bytesPerSecond < 1_000L -> bytesPerSecond.toString() to "B/s"
    bytesPerSecond < 1_000_000L -> String.format(Locale.US, "%.1f", bytesPerSecond / 1_000.0) to "KB/s"
    else -> String.format(Locale.US, "%.2f", bytesPerSecond / 1_000_000.0) to "MB/s"
}

// ───────────────────────── Connect button ─────────────────────────

@Composable
private fun ConnectButton(ui: UiState, onClick: () -> Unit) {
    val connected = ui == UiState.CONNECTED
    val connecting = ui == UiState.CONNECTING
    val failed = ui == UiState.FAILED
    val active = connected || connecting
    val haptic = LocalHapticFeedback.current
    val accent = Accent

    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed) 0.93f else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "button-press"
    )

    // لرزش کوتاه و لرزش لمسی هنگام ورود به حالت ناموفق
    val shake = remember { Animatable(0f) }
    LaunchedEffect(ui) {
        if (ui == UiState.FAILED) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            for (v in listOf(-12f, 10f, -8f, 6f, -3f, 0f)) shake.animateTo(v, tween(60))
        }
    }

    val faceColor by animateColorAsState(if (connected) Accent else Panel, tween(450), label = "face")
    val borderColor by animateColorAsState(if (failed) ErrorC else Accent, tween(450), label = "border")
    val iconColor by animateColorAsState(
        when {
            connected -> OnAccent
            failed -> ErrorC
            else -> Accent
        },
        tween(450), label = "icon"
    )

    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        // حلقه‌ها فقط وقتی فعال‌اند ساخته می‌شوند، پس در حالت بیکار هزینه‌ای ندارند
        AnimatedVisibility(
            visible = active,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(400))
        ) {
            ConnectionRings(accent = accent, connecting = connecting, buttonRadius = 56.dp, modifier = Modifier.fillMaxSize())
        }

        Box(
            Modifier.size(112.dp)
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale; translationX = shake.value.dp.toPx() }
                .background(faceColor, CircleShape)
                .border(2.dp, borderColor, CircleShape)
                .clip(CircleShape)
                .clickable(interactionSource = source, indication = null) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Crossfade(targetState = failed, animationSpec = tween(250), label = "button-icon") { isFailed ->
                Icon(
                    if (isFailed) Icons.Outlined.Refresh else Icons.Outlined.PowerSettingsNew,
                    null,
                    tint = iconColor,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
    }
}

/**
 * انیمیشن‌ها فقط در مرحله‌ی draw خوانده می‌شوند، پس هیچ بازترکیبی (recomposition) در هر فریم رخ نمی‌دهد.
 * حالت connecting: کمان چرخان. حالت connected: موج‌های آرام و نازک.
 */
@Composable
private fun ConnectionRings(accent: Color, connecting: Boolean, buttonRadius: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rings")
    val ripple by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearOutSlowInEasing)),
        label = "ripple"
    )
    val spin by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "spin"
    )
    val spinAlpha by animateFloatAsState(if (connecting) 1f else 0f, tween(400), label = "spin-alpha")

    Canvas(modifier) {
        val c = center
        val r0 = buttonRadius.toPx()
        val maxRadius = size.minDimension / 2f

        for (i in 0..1) {
            val p = (ripple + i * 0.5f) % 1f
            drawCircle(
                color = accent.copy(alpha = (1f - p) * .30f * (1f - spinAlpha)),
                radius = r0 + (maxRadius - r0) * p,
                center = c,
                style = Stroke(1.5.dp.toPx())
            )
        }

        if (spinAlpha > 0.01f) {
            val r = r0 + 8.dp.toPx()
            val head = accent.copy(alpha = spinAlpha)
            rotate(spin, pivot = c) {
                drawArc(
                    brush = Brush.sweepGradient(0f to Color.Transparent, .83f to head, 1f to head, center = c),
                    startAngle = 0f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

// ───────────────────────── Settings & countries ─────────────────────────

@Composable
private fun PowSettingsPage(
    settings: PowEngineSettings,
    onBack: () -> Unit,
    onOpenCountries: () -> Unit,
    onSave: (PowEngineSettings) -> Unit
) {
    val context = LocalContext.current
    val networkGuard = remember(context) { NetworkGuardStore.get(context) }
    val guardSettings by networkGuard.settings.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(18.dp)) {
        ScreenHeader("تنظیمات", "مدیریت اتصال", onBack)
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                Reveal(0) {
                    SettingCard("مسیر خروجی", "کشور انتخاب‌شده: ${settings.exitCountryCode.ifBlank { "خودکار" }}", "انتخاب کشور", onOpenCountries)
                }
            }
            item {
                val carrierModeLabel = when {
                    settings.carrierMode == PowCoreConfig.CARRIER_OFF -> "خاموش · مسیر عمومی"
                    settings.carrierMode == PowCoreConfig.CARRIER_MANUAL &&
                        settings.firewallProfile == PowCoreConfig.FIREWALL_MCI -> "همراه اول · دستی"
                    settings.carrierMode == PowCoreConfig.CARRIER_MANUAL -> "ایرانسل · دستی"
                    else -> "خودکار · بدون تشخیص اپراتور"
                }
                Reveal(1) {
                    SettingCard("اپراتور شبکه", carrierModeLabel, "تغییر", {
                        val next = when {
                            settings.carrierMode == PowCoreConfig.CARRIER_AUTO ->
                                settings.copy(carrierMode = PowCoreConfig.CARRIER_MANUAL, firewallProfile = PowCoreConfig.FIREWALL_MCI)
                            settings.carrierMode == PowCoreConfig.CARRIER_MANUAL &&
                                settings.firewallProfile == PowCoreConfig.FIREWALL_MCI ->
                                settings.copy(carrierMode = PowCoreConfig.CARRIER_MANUAL, firewallProfile = PowCoreConfig.FIREWALL_IRANCELL)
                            settings.carrierMode == PowCoreConfig.CARRIER_MANUAL ->
                                settings.copy(carrierMode = PowCoreConfig.CARRIER_OFF)
                            else -> settings.copy(carrierMode = PowCoreConfig.CARRIER_AUTO)
                        }
                        onSave(next.validated())
                    })
                }
            }
            item {
                Reveal(2) {
                    SettingCard("روش اتصال", settings.linkRecipe, "تغییر روش", {
                        val next = if (settings.linkRecipe == PowCoreConfig.RECIPE_MASQUE_H2) PowCoreConfig.RECIPE_MASQUE_H3 else PowCoreConfig.RECIPE_MASQUE_H2
                        onSave(settings.copy(linkRecipe = next).validated())
                    })
                }
            }
            item {
                Reveal(3) {
                    SettingCard("حالت خروجی", settings.exitMode, "تغییر حالت", {
                        val next = when (settings.exitMode) {
                            PowEngineSettings.EXIT_CHAIN -> PowEngineSettings.EXIT_WARP
                            PowEngineSettings.EXIT_WARP -> PowEngineSettings.EXIT_PSIPHON
                            else -> PowEngineSettings.EXIT_CHAIN
                        }
                        onSave(settings.copy(exitMode = next).validated())
                    })
                }
            }
            item {
                Reveal(4) {
                    SettingCard("حمل‌ونقل بیرونی", settings.outerTransport, "تغییر transport", {
                        val options = listOf(PowCoreConfig.OUTER_AUTO) + PowCoreConfig.OUTER_LADDER
                        val next = options[(options.indexOf(settings.outerTransport).coerceAtLeast(0) + 1) % options.size]
                        onSave(settings.copy(outerTransport = next).validated())
                    })
                }
            }
            item {
                Reveal(5) {
                    SettingCard("بهینه‌سازی اتصال", if (settings.optimizedMode) "فعال" else "غیرفعال", "تغییر", {
                        onSave(settings.copy(optimizedMode = !settings.optimizedMode).validated())
                    })
                }
            }
            item {
                Reveal(7) {
                    val enabled = guardSettings.quicBlock
                    val source = remember { MutableInteractionSource() }
                    Card(
                        onClick = { networkGuard.setQuicBlock(!enabled) },
                        interactionSource = source,
                        colors = CardDefaults.cardColors(containerColor = Panel),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                            .pressScale(source)
                            .border(1.dp, Line, RoundedCornerShape(18.dp))
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("مسدودسازی QUIC", fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (enabled) "فعال · مسدودسازی UDP پورت ۴۴۳" else "غیرفعال · عبور عادی ترافیک QUIC",
                                    color = Muted,
                                    fontSize = 12.sp
                                )
                                Text(
                                    "تغییر وضعیت بدون قطع اتصال VPN اعمال می‌شود",
                                    color = Muted.copy(alpha = .78f),
                                    fontSize = 10.sp
                                )
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = { networkGuard.setQuicBlock(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = OnAccent,
                                    checkedTrackColor = Accent,
                                    uncheckedThumbColor = Muted,
                                    uncheckedTrackColor = PanelRaised
                                )
                            )
                        }
                    }
                }
            }
            item {
                val fragmentModeLabel = when (settings.h2FragmentMode) {
                    PowEngineSettings.H2_FRAG_ON -> "فعال"
                    PowEngineSettings.H2_FRAG_OFF -> "غیرفعال"
                    else -> "خودکار"
                }
                Reveal(6) {
                    SettingCard("تکه‌تکه‌سازی H2", fragmentModeLabel, "تغییر حالت", {
                        val nextMode = when (settings.h2FragmentMode) {
                            PowEngineSettings.H2_FRAG_AUTO -> PowEngineSettings.H2_FRAG_ON
                            PowEngineSettings.H2_FRAG_ON -> PowEngineSettings.H2_FRAG_OFF
                            else -> PowEngineSettings.H2_FRAG_AUTO
                        }
                        onSave(settings.copy(
                            h2FragmentMode = nextMode,
                            h2Fragmentation = nextMode != PowEngineSettings.H2_FRAG_OFF
                        ).validated())
                    })
                }
            }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "بازگشت", tint = Ink) }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
    }
}

// ───────────────────────────── Components ─────────────────────────────

/** ورود تدریجی (fade + حرکت از پایین) بدون جابه‌جایی layout، چون محتوا همیشه ساخته شده است. */
@Composable
private fun Reveal(index: Int, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        progress.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier.fillMaxWidth().graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 28.dp.toPx()
        }
    ) { content() }
}

/** حالت فشرده شدن نرم و فنری برای کارت‌ها. */
@Composable
private fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.97f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press-scale"
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
private fun CountryRow(title: String, code: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val borderColor by animateColorAsState(if (selected) Accent.copy(alpha = .7f) else Line, tween(250), label = "country-border")
    val tint by animateColorAsState(if (selected) Accent.copy(alpha = .10f) else Color.Transparent, tween(250), label = "country-tint")
    Row(
        Modifier.fillMaxWidth()
            .background(Panel, shape)
            .background(tint, shape)
            .border(1.dp, borderColor, shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(code, color = Muted, fontSize = 11.sp)
        }
        AnimatedVisibility(selected, enter = fadeIn() + scaleIn(initialScale = 0.5f), exit = fadeOut() + scaleOut(targetScale = 0.5f)) {
            Icon(Icons.Outlined.CheckCircle, null, tint = Accent)
        }
    }
}

@Composable
private fun InfoTile(title: String, value: String, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = source,
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.pressScale(source).border(1.dp, Line, RoundedCornerShape(18.dp))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, color = Muted, fontSize = 10.sp)
                Text(value, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SettingCard(title: String, value: String, action: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = source,
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().pressScale(source).border(1.dp, Line, RoundedCornerShape(18.dp))
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                // وقتی مقدار با لمس عوض می‌شود، با اسلاید نرم جایگزین می‌شود
                AnimatedContent(
                    targetState = value,
                    transitionSpec = {
                        (fadeIn(tween(220, delayMillis = 40)) + slideInVertically(tween(260)) { it / 2 }) togetherWith
                            (fadeOut(tween(120)) + slideOutVertically(tween(200)) { -it / 2 })
                    },
                    label = "setting-value"
                ) { v ->
                    Text(v, color = Muted, fontSize = 12.sp)
                }
            }
            Text(
                action, color = Accent, fontSize = 12.sp,
                modifier = Modifier.background(Accent.copy(alpha = .12f), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun PathLine(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 12.sp, color = Muted, modifier = Modifier.width(112.dp))
        AnimatedContent(
            targetState = value,
            modifier = Modifier.weight(1f),
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "path-value"
        ) { v ->
            Text(v, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
