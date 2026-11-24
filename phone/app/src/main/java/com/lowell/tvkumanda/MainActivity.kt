package com.lowell.tvkumanda

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

enum class Tab(val icon: Solar) {
    Pad(Ic.Pad),
    Apps(Ic.Apps),
    Tv(Ic.Tv),
    Keyboard(Ic.Keyboard);

    fun label(s: Strings): String = when (this) {
        Pad -> s.tabPad
        Apps -> s.tabApps
        Tv -> s.tabTv
        Keyboard -> s.tabKeyboard
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var tv: TvClient

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        tv = TvClient(applicationContext)
        setContent { App() }
    }

    // Telefonun fiziksel ses tuslari TV sesini ayarlar
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) { tv.key("vol_up"); return true }
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) { tv.key("vol_down"); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onStart() { super.onStart(); tv.start() }
    override fun onStop() { super.onStop(); tv.stop() }

    @Composable
    private fun App() {
        val st = remember { AppSettings(applicationContext) }
        val s = st.strings
        val sysDark = isSystemInDarkTheme()
        val dark = when (st.theme) {
            AppTheme.System -> sysDark
            AppTheme.Light -> false
            AppTheme.Dark -> true
        }
        IosTheme(dark) {
            val c = Ios.c
            val buzz = rememberBuzzer(st)
            var tab by remember { mutableStateOf(Tab.Pad) }
            var showSettings by remember { mutableStateOf(false) }
            var showPower by remember { mutableStateOf(false) }
            var showKeyboardSheet by remember { mutableStateOf(false) }
            var imePkg by remember { mutableStateOf("") }
            var imeHint by remember { mutableStateOf("") }
            var snack by remember { mutableStateOf<String?>(null) }
            val toast: (String) -> Unit = { snack = it }
            val backdrop = rememberLayerBackdrop()

            LaunchedEffect(snack) { if (snack != null) { delay(2200); snack = null } }
            LaunchedEffect(tv.connected) {
                if (!tv.connected) return@LaunchedEffect
                var lastActive = false
                while (isActive) {
                    delay(1000)
                    try {
                        val res = tv.get("/ime")
                        if (res != null) {
                            val j = org.json.JSONObject(res)
                            val act = j.optBoolean("active")
                            imePkg = j.optString("pkg")
                            imeHint = j.optString("hint")
                            if (act && !lastActive) {
                                showKeyboardSheet = true
                                buzz(15)
                            }
                            lastActive = act
                        }
                    } catch (_: Exception) { }
                }
            }
            LaunchedEffect(Unit) {
                if (tv.ip.isEmpty()) {
                    toast(s.searchingTv)
                    val f = tv.scan()
                    if (f != null) { tv.updateIp(f); toast(s.tvFound(f)) } else { toast(s.tvNotFound); showSettings = true }
                }
            }

            Box(Modifier.fillMaxSize().background(c.bg)) {
                Column(Modifier.fillMaxSize().layerBackdrop(backdrop).statusBarsPadding().imePadding()) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(tab.label(s), Modifier.weight(1f), size = 34f, weight = FontWeight.Bold)
                        Row(
                            Modifier.clip(CircleShape).background(c.card).pressable { showSettings = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(if (tv.connected) c.green else c.danger))
                            T(if (tv.connected) "  " + s.connected else "  " + s.notConnected, size = 13f)
                        }
                        if (tab == Tab.Pad) {
                            Box(
                                Modifier.padding(start = 8.dp).size(36.dp).clip(CircleShape).background(c.card).pressable { showPower = true },
                                contentAlignment = Alignment.Center,
                            ) { Glyph(Ic.Power, c.danger, 20.dp, filled = true) }
                        }
                    }
                    AnimatedContent(
                        targetState = tab, modifier = Modifier.weight(1f).fillMaxWidth(), label = "tab",
                        transitionSpec = {
                            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 380f)) { it / 5 * dir } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(spring(dampingRatio = 0.85f, stiffness = 380f)) { -it / 5 * dir } + fadeOut(tween(140)))
                        },
                    ) { t ->
                        when (t) {
                            Tab.Pad -> RemoteScreen(st, tv, buzz, Modifier.fillMaxSize())
                            Tab.Apps -> AppsScreen(st, tv, buzz, toast, Modifier.fillMaxSize())
                            Tab.Tv -> TvScreen(st, tv, buzz, toast, Modifier.fillMaxSize())
                            Tab.Keyboard -> KeyboardScreen(st, tv, buzz, toast, Modifier.fillMaxSize())
                        }
                    }
                }
                AnimatedVisibility(
                    visible = snack != null, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp),
                    enter = fadeIn(), exit = fadeOut(),
                ) {
                    T(
                        snack ?: "", size = 14f, color = if (c.dark) Color.Black else Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (c.dark) Color.White else Color(0xFF1C1C1E)).padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }

                Row(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).height(64.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().dockSurface(backdrop, dark).padding(4.dp)) {
                        val itemW = maxWidth / Tab.entries.size
                        val x by animateDpAsState(itemW * tab.ordinal, spring(dampingRatio = 0.72f, stiffness = 420f), label = "pill")
                        Box(
                            Modifier.offset(x).width(itemW).fillMaxHeight().clip(CircleShape)
                                .background((if (dark) Color.White else Color.Black).copy(alpha = 0.12f)),
                        )
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            Tab.entries.forEach { t -> DockItem(t, s, selected = t == tab, dark = dark) { tab = t; buzz(8) } }
                        }
                    }
                }
            }

            if (showSettings) SettingsDialog(st, tv, toast) { showSettings = false }
            if (showPower) PowerSheets(st, tv, toast) { showPower = false }
            if (showKeyboardSheet) KeyboardSheet(st, tv, buzz, toast, imePkg, imeHint) { showKeyboardSheet = false }
        }
    }
}

@Composable
private fun RowScope.DockItem(tab: Tab, s: Strings, selected: Boolean, dark: Boolean, onClick: () -> Unit) {
    val fg = if (dark) Color.White else Color.Black
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.55f, stiffness = 600f), label = "press")
    val lift by animateFloatAsState(if (selected) 1.12f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "lift")
    val a by animateFloatAsState(if (selected) 1f else 0.6f, label = "alpha")
    Column(
        Modifier.weight(1f).fillMaxHeight().graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Glyph(tab.icon, fg.copy(alpha = a), 22.dp, Modifier.graphicsLayer { scaleX = lift; scaleY = lift }, filled = selected)
        T(tab.label(s), size = 10f, color = fg.copy(alpha = a), maxLines = 1)
    }
}

/** Cam dock: Backdrop (Android 12+), yoksa yari saydam yuzey. */
private fun Modifier.dockSurface(backdrop: Backdrop, dark: Boolean): Modifier {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val surface = if (dark) Color(0xFF1E1E1E).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.45f)
        val edge = if (dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f)
        return drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = {
                vibrancy()
                blur(8.dp.toPx())
            },
            onDrawSurface = { drawRect(surface) },
        ).border(0.5.dp, edge, CircleShape)
    }
    val surface = if (dark) Color(0xFF1E1E1E).copy(alpha = 0.88f) else Color(0xFFF2F2F7).copy(alpha = 0.9f)
    val edge = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    return this
        .shadow(16.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
        .clip(CircleShape).background(surface).border(0.5.dp, edge, CircleShape)
}
