package com.lowell.tvkumanda

import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class AppSettings(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var pad by mutableIntStateOf(p.getInt("pad", 10)); private set
    var step by mutableIntStateOf(p.getInt("step", 56)); private set
    var nat by mutableStateOf(p.getBoolean("nat", false)); private set
    var vib by mutableStateOf(p.getBoolean("vib", true)); private set
    var left by mutableStateOf(p.getBoolean("left", false)); private set
    var mode by mutableStateOf(runCatching { Mode.valueOf(p.getString("mode", "Nav")!!) }.getOrDefault(Mode.Nav)); private set
    var lang by mutableStateOf(runCatching { AppLanguage.valueOf(p.getString("lang", "En")!!) }.getOrDefault(AppLanguage.En)); private set
    var theme by mutableStateOf(runCatching { AppTheme.valueOf(p.getString("theme", "System")!!) }.getOrDefault(AppTheme.System)); private set

    val isTr: Boolean
        get() = when (lang) {
            AppLanguage.Tr -> true
            AppLanguage.En -> false
            AppLanguage.Auto -> java.util.Locale.getDefault().language.lowercase().startsWith("tr")
        }

    val strings: Strings
        get() = Strings(isTr)

    fun updatePad(v: Int) { pad = v; p.edit().putInt("pad", v).apply() }
    fun updateStep(v: Int) { step = v; p.edit().putInt("step", v).apply() }
    fun updateNat(v: Boolean) { nat = v; p.edit().putBoolean("nat", v).apply() }
    fun updateVib(v: Boolean) { vib = v; p.edit().putBoolean("vib", v).apply() }
    fun updateLeft(v: Boolean) { left = v; p.edit().putBoolean("left", v).apply() }
    fun updateMode(v: Mode) { mode = v; p.edit().putString("mode", v.name).apply() }
    fun updateLang(v: AppLanguage) { lang = v; p.edit().putString("lang", v.name).apply() }
    fun updateTheme(v: AppTheme) { theme = v; p.edit().putString("theme", v.name).apply() }
}

private val DOCK_SPACE = 100.dp

@Composable
fun ConfirmDialog(title: String, text: String, ok: String, danger: Boolean, cancelText: String, onYes: () -> Unit, onNo: () -> Unit) {
    IosAlert(title, text, listOf(
        Act(cancelText, Role.Normal, onNo),
        Act(ok, if (danger) Role.Danger else Role.Bold, onYes),
    ), onNo)
}

/** Icerik kisaysa alta yaslar (tek el erisimi), uzunsa kayar. */
@Composable
private fun BottomAnchored(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val h = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.heightIn(min = h).padding(horizontal = 16.dp), verticalArrangement = Arrangement.Bottom) {
                content()
                Spacer(Modifier.height(DOCK_SPACE))
            }
        }
    }
}

// ---------------- Kumanda ----------------

@Composable
private fun GestureHelp(s: Strings, onClose: () -> Unit) {
    val c = Ios.c
    IosPage(s.gesturesTitle, onClose, s.done) {
        SectionHeader(s.swipeModeTitle)
        IosGroup {
            listOf(
                s.t("Kaydır","Swipe") to s.t("Yön tuşları (hızlı kaydırınca daha sık)","Arrow keys (faster swipe = more presses)"),
                s.t("Dokun","Tap") to "OK",
                s.t("Basılı tut","Long press") to s.t("Menü","Menu"),
                s.t("İki parmak dokun","Two-finger tap") to s.t("Geri","Back"),
                s.t("Sağdan kenardan kaydır","Swipe from right edge") to s.t("Geri","Back"),
                s.t("Soldan kenardan kaydır","Swipe from left edge") to s.t("Menü","Menu"),
                s.t("Alttan kenardan yukarı kaydır","Swipe up from bottom edge") to s.t("Ana ekran","Home"),
            ).forEachIndexed { i, (g, a) -> IosRow(g, trailing = { T(a, size = 14f, color = c.secondary) }, last = i == 6) }
        }
        SectionHeader(s.twoFingersTitle)
        IosGroup {
            listOf(
                s.t("Yukarı","Up") to s.t("Ana ekran","Home"),
                s.t("Aşağı","Down") to s.t("Menü","Menu"),
                s.t("Sola","Left") to s.t("Geri","Back"),
                s.t("Sağa","Right") to s.t("Oynat / duraklat","Play / pause"),
            ).forEachIndexed { i, (g, a) -> IosRow(g, trailing = { T(a, size = 14f, color = c.secondary) }, last = i == 3) }
        }
        SectionHeader(s.threeFingersTitle)
        IosGroup {
            listOf(
                s.t("Sola","Left") to s.t("Önceki","Previous"),
                s.t("Sağa","Right") to s.t("Sonraki","Next"),
                s.t("Yukarı","Up") to s.t("Ana ekran","Home"),
                s.t("Aşağı","Down") to s.t("Ara","Search"),
                s.t("Dokun","Tap") to s.t("Oynat / duraklat","Play / pause"),
            ).forEachIndexed { i, (g, a) -> IosRow(g, trailing = { T(a, size = 14f, color = c.secondary) }, last = i == 4) }
        }
        SectionHeader(s.mouseModeTitle)
        IosGroup {
            listOf(
                s.t("Kaydır","Swipe") to s.t("İmleç","Cursor"),
                s.t("Dokun","Tap") to s.t("Sol tık","Left click"),
                s.t("İki parmak dokun","Two-finger tap") to s.t("Sağ tık","Right click"),
                s.t("İki parmak kaydır","Two-finger swipe") to s.t("Sayfa kaydır","Scroll"),
                s.t("Çift dokun ve sürükle","Double tap & drag") to s.t("Taşı","Move"),
            ).forEachIndexed { i, (g, a) -> IosRow(g, trailing = { T(a, size = 14f, color = c.secondary) }, last = i == 4) }
        }
        SectionHeader(s.volumeTitle)
        IosGroup {
            IosRow(s.phoneVolKeyTitle, trailing = { T(s.tvVolDesc, size = 14f, color = c.secondary) })
            IosRow(s.volMiniDesc, trailing = { T(s.tvVolDesc, size = 14f, color = c.secondary) }, last = true)
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun RemoteScreen(st: AppSettings, tv: TvClient, buzz: (Long) -> Unit, modifier: Modifier = Modifier) {
    val mode = st.mode
    val c = Ios.c
    val s = st.strings
    var help by remember { mutableStateOf(false) }
    if (help) GestureHelp(s) { help = false }
    val right = !st.left
    // Tum alan hareket yuzeyi; tuslar basparmak yayinda (sag alt) yuzer
    Box(modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, bottom = 108.dp)) {
        Crossfade(mode, label = "mode") { m ->
            if (m == Mode.Dpad) Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(c.card))
            else TouchPad(m, st, tv, buzz, Modifier.fillMaxSize())
        }
        if (mode == Mode.Dpad) {
            DpadCluster(tv, buzz, Modifier.align(if (right) Alignment.BottomStart else Alignment.BottomEnd).padding(start = 8.dp, end = 8.dp, bottom = 120.dp))
        } else {
            Box(
                Modifier.align(Alignment.TopStart).padding(10.dp).size(32.dp).clip(CircleShape).pressable { help = true },
                contentAlignment = Alignment.Center,
            ) { Glyph(Ic.Info, c.secondary, 22.dp) }
        }

        // Sol alt: ses + mod secici (nadir kullanilanlar)
        Column(
            Modifier.align(if (right) Alignment.BottomStart else Alignment.BottomEnd).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = if (right) Alignment.Start else Alignment.End,
        ) {
            if (mode == Mode.Mouse) {
                Row(Modifier.width(216.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MouseBtn(s.mouseLeft, 1, tv, buzz, Modifier.weight(1f))
                    MouseBtn(s.mouseMiddle, 4, tv, buzz, Modifier.weight(1f))
                    MouseBtn(s.mouseRight, 2, tv, buzz, Modifier.weight(1f))
                }
            }
            VolMini(tv, buzz)
            IosSegmented(Mode.entries.map { it.label(s) }, mode.ordinal, { st.updateMode(Mode.entries[it]); buzz(10) }, Modifier.width(216.dp).height(38.dp))
        }

        // Sag alt: basparmak yayi
        val side = if (right) Alignment.BottomEnd else Alignment.BottomStart
        val sgn = if (right) -1 else 1
        Box(Modifier.align(side).size(130.dp, 260.dp)) {
            ArcKey("play_pause", Ic.Play, tv, buzz, side, sgn * 6, -196)
            ArcKey("menu", Ic.Menu, tv, buzz, side, sgn * 52, -134)
            ArcKey("home", Ic.Home, tv, buzz, side, sgn * 52, -72)
            ArcKey("back", Ic.Back, tv, buzz, side, sgn * 6, -10)
        }
    }
}

@Composable
private fun ArcKey(k: String, icon: Solar, tv: TvClient, buzz: (Long) -> Unit, side: Alignment, dx: Int, dy: Int) {
    val c = Ios.c
    Box(Modifier.fillMaxSize(), contentAlignment = side) {
        KeyButton(
            k, tv, buzz,
            Modifier.offset(dx.dp, dy.dp).size(58.dp).shadow(8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f)),
            shape = CircleShape, container = c.card,
        ) { Glyph(icon, c.label, 26.dp, filled = true) }
    }
}

@Composable
private fun VolMini(tv: TvClient, buzz: (Long) -> Unit) {
    val c = Ios.c
    Row(
        Modifier.width(216.dp).height(46.dp).clip(RoundedCornerShape(23.dp)).background(c.fill),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyButton("vol_down", tv, buzz, Modifier.weight(1f).fillMaxHeight(), repeat = true, shape = RoundedCornerShape(23.dp), container = Color.Transparent) { Glyph(Ic.VolDown, c.label, 24.dp, filled = true) }
        KeyButton("mute", tv, buzz, Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(23.dp), container = Color.Transparent) { Glyph(Ic.Mute, c.label, 22.dp, filled = true) }
        KeyButton("vol_up", tv, buzz, Modifier.weight(1f).fillMaxHeight(), repeat = true, shape = RoundedCornerShape(23.dp), container = Color.Transparent) { Glyph(Ic.VolUp, c.label, 24.dp, filled = true) }
    }
}
@Composable
private fun IconKey(k: String, tv: TvClient, buzz: (Long) -> Unit, icon: Solar, modifier: Modifier = Modifier, repeat: Boolean = false) {
    KeyButton(k, tv, buzz, modifier, repeat) { Glyph(icon, Ios.c.label, 26.dp, filled = true) }
}

@Composable
private fun VolKey(k: String, icon: Solar, tv: TvClient, buzz: (Long) -> Unit, rep: Boolean) {
    KeyButton(k, tv, buzz, Modifier.size(52.dp), repeat = rep, shape = CircleShape, container = Color.Transparent) {
        Glyph(icon, Ios.c.label, 26.dp, filled = true)
    }
}

@Composable
private fun MouseBtn(label: String, b: Int, tv: TvClient, buzz: (Long) -> Unit, modifier: Modifier) {
    var held by remember { mutableStateOf(false) }
    val c = Ios.c
    Box(
        modifier.height(52.dp).clip(RoundedCornerShape(16.dp)).background(if (held) c.accent.copy(alpha = 0.30f) else c.fill)
            .pointerInput(b) {
                detectTapGestures(onPress = {
                    held = true
                    tv.send(JSONObject().put("t", "btn").put("b", b).put("down", true)); buzz(8)
                    try { tryAwaitRelease() } finally {
                        held = false
                        tv.send(JSONObject().put("t", "btn").put("b", b).put("down", false))
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) { T(label, size = 16f, maxLines = 1) }
}

@Composable
private fun DpadCluster(tv: TvClient, buzz: (Long) -> Unit, modifier: Modifier) {
    val c = Ios.c
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconKey("up", tv, buzz, Ic.Up, Modifier.size(74.dp), repeat = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconKey("left", tv, buzz, Ic.Left, Modifier.size(74.dp), repeat = true)
                KeyButton("enter", tv, buzz, Modifier.size(84.dp), shape = CircleShape, container = c.accent) {
                    T("OK", color = Color.White, size = 20f, weight = FontWeight.SemiBold)
                }
                IconKey("right", tv, buzz, Ic.Right, Modifier.size(74.dp), repeat = true)
            }
            IconKey("down", tv, buzz, Ic.Down, Modifier.size(74.dp), repeat = true)
        }
    }
}

// ---------------- Guc ----------------

@Composable
fun PowerSheets(st: AppSettings, tv: TvClient, toast: (String) -> Unit, onClose: () -> Unit) {
    val str = st.strings
    var confirm by remember { mutableStateOf<String?>(null) }
    val c = confirm
    if (c == null) {
        IosSheet(str.powerTitle, listOf(
            Act(str.sleepAct) { tv.send(JSONObject().put("t", "power").put("a", "sleep")); toast(str.sleepSent); onClose() },
            Act(str.wakeAct) { tv.send(JSONObject().put("t", "power").put("a", "wake")); toast(str.wakingUp); onClose() },
            Act(str.rebootAct, Role.Danger) { confirm = "reboot" },
            Act(str.powerOffAct, Role.Danger) { confirm = "poweroff" },
        ), str.cancel, onClose)
    } else {
        val reboot = c == "reboot"
        ConfirmDialog(
            if (reboot) str.restartConfirmTitle else str.powerOffConfirmTitle,
            if (reboot) str.restartConfirmMsg else str.powerOffConfirmMsg,
            if (reboot) str.rebootAct else str.powerOffAct, true, str.cancel,
            onYes = { tv.send(JSONObject().put("t", "power").put("a", c)); toast(if (reboot) str.rebooting else str.shuttingDown); onClose() },
            onNo = onClose,
        )
    }
}

// ---------------- Uygulamalar ----------------

private fun hueGradient(s: String): Brush {
    val h = (s.hashCode() and 0x7fffffff) % 360
    return Brush.verticalGradient(listOf(Color.hsv(h.toFloat(), 0.45f, 0.95f), Color.hsv(((h + 28) % 360).toFloat(), 0.65f, 0.72f)))
}

@Composable
fun AppsScreen(st: AppSettings, tv: TvClient, buzz: (Long) -> Unit, toast: (String) -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val str = st.strings
    var apps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var manage by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirm by remember { mutableStateOf<Triple<String, String, String>?>(null) } // action, pkg, name
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(tv.connected, refresh) {
        val r = tv.get("/apps")
        apps = try {
            val a = JSONArray(r)
            (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("pkg") to o.getString("name") } }
        } catch (_: Exception) { null }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        val list = apps?.filter { query.isBlank() || it.second.contains(query.trim(), ignoreCase = true) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    T(if (tv.connected) str.launcherFailed else str.tvNotConnected, color = Ios.c.secondary)
                }
            } else {
                LazyVerticalGrid(
                    GridCells.Fixed(4), contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(list) { (pkg, name) ->
                        Column(
                            Modifier.clip(RoundedCornerShape(16.dp)).pressable {
                                if (!manage) { tv.send(JSONObject().put("t", "app").put("pkg", pkg)); toast(str.openingApp(name)); buzz(12) }
                                else sel = pkg to name
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)).background(hueGradient(pkg)), contentAlignment = Alignment.Center) {
                                T((name.firstOrNull() ?: '?').uppercase(), size = 26f, weight = FontWeight.SemiBold, color = Color.White)
                            }
                            T(name, Modifier.padding(top = 6.dp), size = 11f, maxLines = 2, align = TextAlign.Center, color = Ios.c.label)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        IosField(query, { query = it }, str.searchApps, Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(10.dp))
        IosSegmented(listOf(str.open, str.manage), if (manage) 1 else 0, {
            manage = it == 1
            toast(if (manage) str.manageHintManage else str.manageHintOpen)
        }, Modifier.fillMaxWidth().height(40.dp))
        Spacer(Modifier.height(DOCK_SPACE))
    }

    val s = sel
    if (s != null) {
        val prot = s.first == "com.termux" || s.first == "com.termux.boot"
        val acts = mutableListOf<Act>()
        fun run(a: String, n: String) = scope.launch {
            val j = tv.post(JSONObject().put("t", "appact").put("a", a).put("pkg", s.first))
            toast(if (j?.optBoolean("ok") == true) str.operationDone(n) else str.operationFailed)
        }
        acts += Act(str.open) { sel = null; run("open", str.open) }
        if (!prot) acts += Act(str.stop) { sel = null; run("stop", str.stop) }
        acts += Act(str.appInfoAct) { sel = null; run("info", str.appInfo) }
        if (!prot) {
            acts += Act(str.clearData, Role.Danger) { sel = null; confirm = Triple("clear", s.first, s.second) }
            acts += Act(str.uninstall, Role.Danger) { sel = null; confirm = Triple("uninstall", s.first, s.second) }
        }
        IosSheet(s.second, acts, str.cancel) { sel = null }
    }
    val cf = confirm
    if (cf != null) {
        val un = cf.first == "uninstall"
        ConfirmDialog(
            if (un) str.uninstallConfirmTitle(cf.third) else str.clearConfirmTitle,
            if (un) str.uninstallActualDesc else str.clearActualDesc,
            if (un) str.uninstall else str.clearData, true, str.cancel,
            onYes = {
                confirm = null
                scope.launch {
                    val j = tv.post(JSONObject().put("t", "appact").put("a", cf.first).put("pkg", cf.second))
                    toast(if (j?.optBoolean("ok") == true) (if (un) str.uninstalled else str.cleared) else str.operationFailed)
                    if (un) refresh++
                }
            }, onNo = { confirm = null },
        )
    }
}

// ---------------- TV ----------------

private val TILES = listOf(
    Triple("wifi", "Wi-Fi", "Wi-Fi"), Triple("network", "Ağ", "Network"), Triple("bluetooth", "Bluetooth", "Bluetooth"),
    Triple("display", "Ekran", "Display"), Triple("sound", "Ses", "Sound"), Triple("apps", "Uygulamalar", "Apps"),
    Triple("storage", "Depolama", "Storage"), Triple("access", "Erişilebilirlik", "Accessibility"),
    Triple("defaults", "Varsayılanlar", "Defaults"), Triple("date", "Tarih / saat", "Date & time"),
    Triple("input", "Klavye", "Keyboard"), Triple("dev", "Geliştirici", "Developer"), Triple("all", "Tüm ayarlar", "All settings"),
)

@Composable
fun TvScreen(st: AppSettings, tv: TvClient, buzz: (Long) -> Unit, toast: (String) -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val str = st.strings
    var launchers by remember { mutableStateOf<List<Triple<String, String, Boolean>>?>(null) } // comp, name, current
    var pending by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    var power by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val c = Ios.c

    LaunchedEffect(tv.connected, refresh) {
        val r = tv.get("/launchers")
        launchers = try {
            val a = JSONObject(r).getJSONArray("items")
            (0 until a.length()).map { a.getJSONObject(it).let { o -> Triple(o.getString("c"), o.getString("name"), o.optBoolean("current")) } }
        } catch (_: Exception) { null }
    }

    BottomAnchored(modifier) {
        SectionHeader(str.homeLauncherTitle)
        val ls = launchers
        if (ls == null) T(if (tv.connected) str.launcherFailed else str.tvNotConnected, Modifier.padding(start = 16.dp), color = c.secondary)
        else IosGroup {
            ls.forEachIndexed { i, l ->
                IosRow(l.second, onClick = { if (l.third) toast(str.alreadyDefault(l.second)) else pending = l }, last = i == ls.lastIndex,
                    trailing = { if (l.third) Glyph(Ic.Check, c.accent, 22.dp, filled = true) })
            }
        }
        SectionHeader(str.quickSettingsTitle)
        IosGroup {
            TILES.forEachIndexed { i, (k, tr, en) ->
                val n = str.t(tr, en)
                IosRow(n, onClick = { tv.send(JSONObject().put("t", "settings").put("k", k)); toast(str.settingsOpening(n)); buzz(10) },
                    last = i == TILES.lastIndex, leading = Ic.Gear,
                    trailing = { Glyph(Ic.Chevron, c.secondary, 18.dp) })
            }
        }
        SectionHeader(str.powerTitle)
        IosGroup {
            IosRow(str.sleepRow, { tv.send(JSONObject().put("t", "power").put("a", "sleep")); toast(str.sleepSent) })
            IosRow(str.wakeAct, { tv.send(JSONObject().put("t", "power").put("a", "wake")); toast(str.wakingUp) })
            IosRow(str.rebootAct, { power = "reboot" }, color = c.danger)
            IosRow(str.powerOffAct, { power = "poweroff" }, color = c.danger, last = true)
        }
    }

    pending?.let { l ->
        ConfirmDialog(str.defaultPrompt(l.second), str.defaultDesc, str.makeDefault, false, str.cancel,
            onYes = {
                pending = null
                scope.launch {
                    val j = tv.post(JSONObject().put("t", "home").put("c", l.first))
                    toast(if (j?.optBoolean("ok") == true) str.defaultSuccess(l.second) else str.defaultError)
                    refresh++
                }
            }, onNo = { pending = null })
    }
    power?.let { a ->
        val reboot = a == "reboot"
        ConfirmDialog(
            if (reboot) str.restartConfirmTitle else str.powerOffConfirmTitle,
            if (reboot) str.restartConfirmMsg else str.powerOffConfirmMsg,
            if (reboot) str.rebootAct else str.powerOffAct, true, str.cancel,
            onYes = { power = null; tv.send(JSONObject().put("t", "power").put("a", a)); toast(if (reboot) str.rebooting else str.shuttingDown) },
            onNo = { power = null },
        )
    }
}

// ---------------- Klavye ----------------

@Composable
fun LiveKeyboardControls(
    st: AppSettings, tv: TvClient,
    buzz: (Long) -> Unit,
    toast: (String) -> Unit,
    autoFocus: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val c = Ios.c
    val str = st.strings
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var textVal by remember { mutableStateOf(TextFieldValue("")) }

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            delay(150)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) { }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 90.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.card)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            if (textVal.text.isEmpty()) {
                T(str.keyboardInputPlaceholder, color = c.secondary, size = 16f)
            }
            BasicTextField(
                value = textVal,
                onValueChange = { newVal ->
                    val oldStr = textVal.text
                    val newStr = newVal.text

                    if (newStr.length > oldStr.length) {
                        val added = if (newStr.startsWith(oldStr)) {
                            newStr.substring(oldStr.length)
                        } else {
                            newStr
                        }
                        if (added.length == 1) {
                            val ch = added[0]
                            if (ch.code > 127) {
                                tv.send(JSONObject().put("t", "paste").put("s", added))
                            } else {
                                tv.send(JSONObject().put("t", "text").put("s", added))
                            }
                        } else {
                            tv.send(JSONObject().put("t", "paste").put("s", added))
                        }
                        buzz(5)
                    } else if (newStr.length < oldStr.length) {
                        val delCount = oldStr.length - newStr.length
                        repeat(delCount) {
                            tv.key("backspace")
                        }
                        buzz(8)
                    }
                    textVal = newVal
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = TextStyle(color = c.label, fontSize = 17.sp),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        tv.key("enter")
                        buzz(10)
                        textVal = TextFieldValue("")
                    },
                    onDone = {
                        tv.key("enter")
                        buzz(10)
                        textVal = TextFieldValue("")
                    },
                ),
                singleLine = false,
                maxLines = 4,
            )
        }

        // Action row: Temizle, Panoyu Yapistir, Enter ↵
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IosButton(
                str.clear,
                onClick = { textVal = TextFieldValue(""); buzz(8) },
                modifier = Modifier.weight(1f),
                style = BtnStyle.Tinted,
            )
            IosButton(
                str.pasteClip,
                onClick = {
                    val clip = clipboardManager.getText()?.text
                    if (!clip.isNullOrEmpty()) {
                        tv.send(JSONObject().put("t", "paste").put("s", clip))
                        toast(str.clipPasted)
                        buzz(12)
                    } else {
                        toast(str.clipEmpty)
                    }
                },
                modifier = Modifier.weight(1.4f),
                style = BtnStyle.Filled,
            )
            IosButton(
                str.enterKey,
                onClick = {
                    tv.key("enter")
                    buzz(10)
                    textVal = TextFieldValue("")
                },
                modifier = Modifier.weight(1f),
                style = BtnStyle.Tinted,
            )
        }

        // Control Keys Row 1: Esc, Tab, Del, Backspace
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton("esc", tv, buzz, Modifier.weight(1f).height(48.dp)) { T("Esc", size = 15f) }
            KeyButton("tab", tv, buzz, Modifier.weight(1f).height(48.dp)) { T("Tab", size = 15f) }
            KeyButton("delete", tv, buzz, Modifier.weight(1f).height(48.dp), repeat = true) { T("Del", size = 15f) }
            KeyButton("backspace", tv, buzz, Modifier.weight(1.2f).height(48.dp), repeat = true) {
                Glyph(Ic.Backspace, Ios.c.label, 22.dp, filled = true)
            }
        }

        // Navigation Keys Row 2: Sol, Yukari, Asagi, Sag
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton("left", tv, buzz, Modifier.weight(1f).height(48.dp), repeat = true) { Glyph(Ic.Left, Ios.c.label, 20.dp, filled = true) }
            KeyButton("up", tv, buzz, Modifier.weight(1f).height(48.dp), repeat = true) { Glyph(Ic.Up, Ios.c.label, 20.dp, filled = true) }
            KeyButton("down", tv, buzz, Modifier.weight(1f).height(48.dp), repeat = true) { Glyph(Ic.Down, Ios.c.label, 20.dp, filled = true) }
            KeyButton("right", tv, buzz, Modifier.weight(1f).height(48.dp), repeat = true) { Glyph(Ic.Right, Ios.c.label, 20.dp, filled = true) }
        }
    }
}

@Composable
fun KeyboardSheet(
    st: AppSettings, tv: TvClient,
    buzz: (Long) -> Unit,
    toast: (String) -> Unit,
    imePkg: String = "",
    imeHint: String = "",
    onClose: () -> Unit,
) {
    val c = Ios.c
    val str = st.strings
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(c.bg)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Glyph(Ic.Keyboard, c.accent, 20.dp, filled = true)
                            T(str.keyboardTitle, size = 18f, weight = FontWeight.Bold)
                        }
                        val sub = when {
                            imeHint.isNotEmpty() -> str.targetHint(imeHint)
                            imePkg.isNotEmpty() -> str.appPkg(imePkg)
                            else -> str.liveTypingDesc
                        }
                        T(sub, size = 12f, color = c.secondary, maxLines = 1)
                    }
                    T(str.close, Modifier.pressable(onClose).padding(8.dp), color = Ios.c.accent, weight = FontWeight.SemiBold)
                }

                LiveKeyboardControls(st, tv, buzz, toast, autoFocus = true)
            }
        }
    }
}

@Composable
fun KeyboardScreen(st: AppSettings, tv: TvClient, buzz: (Long) -> Unit, toast: (String) -> Unit, modifier: Modifier = Modifier) {
    val str = st.strings
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.Bottom) {
        T(str.keyboardHint, Modifier.padding(horizontal = 4.dp, vertical = 8.dp), size = 13f, color = Ios.c.secondary)
        LiveKeyboardControls(st, tv, buzz, toast, autoFocus = false)
        Spacer(Modifier.height(DOCK_SPACE))
    }
}

// ---------------- Ayarlar ----------------

@Composable
fun SettingsDialog(st: AppSettings, tv: TvClient, toast: (String) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var ipText by remember { mutableStateOf(tv.ip) }
    val c = Ios.c
    val str = st.strings
    IosPage(str.settingsTitle, onClose, str.done) {
        SectionHeader(str.tvSection)
        IosField(ipText, { ipText = it }, str.tvIpPlaceholder, Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IosButton(str.save, { tv.updateIp(ipText); toast(str.addrSaved) }, Modifier.weight(1f), BtnStyle.Tinted)
            IosButton(if (tv.scanning) str.scanning else str.scanTv, {
                scope.launch {
                    toast(str.searchingTv)
                    val f = tv.scan()
                    if (f != null) { tv.updateIp(f); ipText = f; toast(str.tvFound(f)) } else toast(str.notFound)
                }
            }, Modifier.weight(1f), BtnStyle.Filled, enabled = !tv.scanning)
        }
        Spacer(Modifier.height(8.dp))
        IosGroup { IosRow(str.connection, trailing = { T(if (tv.connected) str.connected else str.notConnected, color = if (tv.connected) c.green else c.danger) }, last = true) }

        SectionHeader(str.touchpadSection)
        IosGroup {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row { T(str.speed, Modifier.weight(1f)); T("${st.pad}", color = c.secondary) }
                IosSlider(st.pad.toFloat(), 1f..30f, { st.updatePad(it.toInt()) }, Modifier.fillMaxWidth())
            }
            Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(c.sep))
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row { T(str.stepSwipe, Modifier.weight(1f)); T("${st.step}", color = c.secondary) }
                IosSlider(st.step.toFloat(), 24f..120f, { st.updateStep(it.toInt()) }, Modifier.fillMaxWidth())
            }
        }
        SectionHeader(str.themeTitle)
        IosGroup {
            IosSegmented(AppTheme.entries.map { it.label(str.isTr) }, st.theme.ordinal, { st.updateTheme(AppTheme.entries[it]) }, Modifier.fillMaxWidth().height(40.dp))
        }
        SectionHeader(str.languageTitle)
        IosGroup {
            IosSegmented(AppLanguage.entries.map { it.label }, st.lang.ordinal, { st.updateLang(AppLanguage.entries[it]) }, Modifier.fillMaxWidth().height(40.dp))
        }
        SectionHeader(str.preferencesSection)
        IosGroup {
            IosRow(str.naturalScroll, trailing = { IosSwitch(st.nat) { st.updateNat(it) } })
            IosRow(str.hapticFeedback, trailing = { IosSwitch(st.vib) { st.updateVib(it) } })
            IosRow(str.leftHandMode, trailing = { IosSwitch(st.left) { st.updateLeft(it) } }, last = true)
        }
        Spacer(Modifier.height(40.dp))
    }
}
