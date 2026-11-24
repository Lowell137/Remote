package com.lowell.tvkumanda

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.font.FontWeight
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

enum class Mode {
    Dpad, Nav, Mouse;

    fun label(s: Strings): String = when (this) {
        Dpad -> s.modeButtons
        Nav -> s.modeSwipe
        Mouse -> s.modeMouse
    }
    fun title(s: Strings): String = when (this) {
        Dpad -> s.tabPad
        Nav -> s.modeSwipe
        Mouse -> s.modeMouse
    }
    fun hint(s: Strings): String = when (this) {
        Dpad -> ""
        Nav -> s.modeSwipeHint
        Mouse -> s.modeMouseHint
    }
}

class Buzzer(ctx: Context) {
    private val v: Vibrator? = try {
        if (android.os.Build.VERSION.SDK_INT >= 31)
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    } catch (_: Exception) { null }

    fun buzz(ms: Long) {
        try { v?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) } catch (_: Exception) { }
    }
}

@Composable
fun rememberBuzzer(st: AppSettings): (Long) -> Unit {
    val ctx = LocalContext.current
    val b = remember { Buzzer(ctx) }
    val vib = rememberUpdatedState(st.vib)
    return remember { { ms: Long -> if (vib.value) b.buzz(ms) } }
}

/** Basili tutunca tekrar eden tus. */
@Composable
fun KeyButton(
    k: String, tv: TvClient, buzz: (Long) -> Unit, modifier: Modifier = Modifier, repeat: Boolean = false,
    shape: Shape = RoundedCornerShape(20.dp), container: Color = Ios.c.fill,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    var held by remember { mutableStateOf(false) }
    val acc = Ios.c.accent
    Box(
        modifier
            .clip(shape)
            .background(if (held) acc.copy(alpha = 0.30f) else container)
            .pointerInput(k) {
                detectTapGestures(onPress = {
                    held = true
                    tv.key(k); buzz(8)
                    var job: Job? = null
                    if (repeat) job = scope.launch { delay(380); while (true) { tv.key(k); delay(110) } }
                    try { tryAwaitRelease() } finally { job?.cancel(); held = false }
                })
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Dokunmatik yuzey: Kaydir (yon tuslari + gelismis hareketler) ve Fare modlari. */
@Composable
fun TouchPad(mode: Mode, st: AppSettings, tv: TvClient, buzz: (Long) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    var dot by remember { mutableStateOf<Offset?>(null) }
    var edgeHint by remember { mutableIntStateOf(0) } // 1 sol, 2 sag, 3 alt
    var flash by remember { mutableStateOf<String?>(null) }
    val q = remember { floatArrayOf(0f, 0f, 0f) }
    var lastTapEnd by remember { mutableLongStateOf(0L) }
    val curMode = rememberUpdatedState(mode)
    val curSt = rememberUpdatedState(st)

    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            val dx = q[0].roundToInt(); val dy = q[1].roundToInt(); val w = q[2].roundToInt()
            if (dx != 0 || dy != 0 || w != 0) {
                q[0] -= dx; q[1] -= dy; q[2] -= w
                tv.send(JSONObject().put("t", "move").put("dx", dx).put("dy", dy).put("w", w))
            }
        }
    }
    LaunchedEffect(flash) { if (flash != null) { delay(750); flash = null } }

    fun key(k: String) { tv.key(k); buzz(8) }
    fun act(label: String, k: String) { tv.key(k); buzz(18); flash = label }
    fun click(b: Int) = tv.send(JSONObject().put("t", "click").put("b", b))
    fun btn(b: Int, down: Boolean) = tv.send(JSONObject().put("t", "btn").put("b", b).put("down", down))

    Box(
        modifier
            .systemGestureExclusion()
            .clip(RoundedCornerShape(28.dp))
            .background(Ios.c.card)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val m = curMode.value
                    val s = curSt.value
                    val strings = s.strings
                    val edgePx = 56.dp.toPx()
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val t0 = down.uptimeMillis
                    var moved = 0f
                    var maxp = 1
                    var pressed = 1
                    var accX = 0f
                    var accY = 0f
                    var dragging = false
                    var longFired = false
                    var lastT = t0
                    val hist = ArrayList<FloatArray>()
                    // kenardan baslayan hareket (yalniz Kaydir modu)
                    val startEdge = if (m != Mode.Nav) 0 else when {
                        down.position.x < edgePx -> 1
                        down.position.x > size.width - edgePx -> 2
                        down.position.y > size.height - edgePx -> 3
                        else -> 0
                    }
                    if (startEdge != 0) edgeHint = startEdge
                    // cok parmak: agirlik merkezinin yer degistirmesi
                    var gsx = down.position.x; var gsy = down.position.y   // baslangic merkezi (dp degil px)
                    var gcx = gsx; var gcy = gsy
                    var edgeDx = 0f; var edgeDy = 0f
                    dot = down.position

                    val longJob = scope.launch {
                        delay(550)
                        if (pressed == 1 && moved < 10f && !dragging && startEdge == 0) {
                            longFired = true; buzz(20)
                            if (m == Mode.Nav) act(strings.menuAction, "menu") else if (m == Mode.Mouse) click(2)
                        }
                    }
                    if (m == Mode.Mouse && t0 - lastTapEnd < 280) { dragging = true; btn(1, true); buzz(12) }

                    do {
                        val ev = awaitPointerEvent()
                        val prevPressed = pressed
                        pressed = ev.changes.count { it.pressed }
                        if (pressed > maxp) maxp = pressed
                        val act = ev.changes.filter { it.pressed }
                        if (act.isNotEmpty()) {
                            val cx = act.map { it.position.x }.average().toFloat()
                            val cy = act.map { it.position.y }.average().toFloat()
                            if (pressed != prevPressed && pressed >= 2) { gsx = cx; gsy = cy } // parmak sayisi degisince yeniden olc
                            gcx = cx; gcy = cy
                        }
                        for (c in ev.changes) {
                            lastT = c.uptimeMillis
                            if (c.pressed && c.previousPressed) {
                                val d = c.positionChange()
                                val dx = d.x / density
                                val dy = d.y / density
                                moved += abs(dx) + abs(dy)
                                dot = c.position
                                if (startEdge != 0) { edgeDx += dx; edgeDy += dy }
                                if (moved > 10f && !dragging) longJob.cancel()
                                hist.add(floatArrayOf(c.uptimeMillis.toFloat(), c.position.x / density, c.position.y / density))
                                if (hist.size > 6) hist.removeAt(0)
                                if (m == Mode.Nav) {
                                    if (pressed == 1 && startEdge == 0) {
                                        accX += dx; accY += dy
                                        val dtm = maxOf(1L, c.uptimeMillis - c.previousUptimeMillis).toFloat()
                                        val v = hypot(dx, dy) / dtm // dp/ms: hizli kaydirmada daha sik tus
                                        val step = s.step.toFloat() * (if (v > 1.6f) 0.45f else if (v > 0.9f) 0.7f else 1f)
                                        while (abs(accX) >= step) { key(if (accX > 0) "right" else "left"); accX -= sign(accX) * step }
                                        while (abs(accY) >= step) { key(if (accY > 0) "down" else "up"); accY -= sign(accY) * step }
                                    }
                                } else if (m == Mode.Mouse) {
                                    if (pressed == 2) q[2] += (if (s.nat) 1f else -1f) * dy / 12f
                                    else if (pressed == 1) {
                                        val sp = hypot(dx, dy)
                                        val gn = (s.pad / 8f) * (1f + min(sp, 24f) * 0.07f)
                                        q[0] += dx * gn; q[1] += dy * gn
                                    }
                                }
                            }
                            c.consume()
                        }
                    } while (ev.changes.any { it.pressed })

                    longJob.cancel()
                    edgeHint = 0
                    val dt = lastT - t0
                    val gdx = (gcx - gsx) / density
                    val gdy = (gcy - gsy) / density
                    val horiz = abs(gdx) > abs(gdy)
                    var handled = false

                    // kenar hareketleri
                    if (startEdge != 0 && maxp == 1) {
                        when (startEdge) {
                            1 -> if (edgeDx > 60f) { act(strings.menuAction, "menu"); handled = true }
                            2 -> if (edgeDx < -60f) { act(strings.backAction, "back"); handled = true }
                            3 -> if (edgeDy < -60f) { act(strings.homeAction, "home"); handled = true }
                        }
                    }
                    // cok parmakli hareketler
                    if (!handled && maxp >= 2 && maxOf(abs(gdx), abs(gdy)) > 56f) {
                        if (maxp == 2 && m == Mode.Nav) {
                            if (horiz) { if (gdx < 0) act(strings.backAction, "back") else act(strings.playPauseAction, "play_pause") }
                            else { if (gdy < 0) act(strings.homeAction, "home") else act(strings.menuAction, "menu") }
                            handled = true
                        } else if (maxp >= 3) {
                            if (horiz) { if (gdx < 0) act(strings.prevAction, "prev") else act(strings.nextAction, "next") }
                            else { if (gdy < 0) act(strings.homeAction, "home") else act(strings.searchAction, "search") }
                            handled = true
                        }
                    }
                    if (!handled && maxp >= 3 && dt < 300 && moved < 30f) { act(strings.playPauseAction, "play_pause"); handled = true }

                    if (!handled) {
                        if (m == Mode.Nav && maxp == 1 && moved >= 10f && hist.size > 2 && startEdge == 0) { // firlatma
                            val a = hist.first(); val b = hist.last()
                            val tt = maxOf(1f, b[0] - a[0])
                            val vx = (b[1] - a[1]) / tt; val vy = (b[2] - a[2]) / tt
                            val v = hypot(vx, vy)
                            if (v > 1.1f) {
                                val n = min(5, (v * 1.8f).roundToInt())
                                val hz = abs(vx) > abs(vy)
                                val kk = if (hz) (if (vx > 0) "right" else "left") else (if (vy > 0) "down" else "up")
                                scope.launch { repeat(n) { key(kk); delay(60) } }
                            }
                        }
                        if (maxp == 1 && dt < 260 && moved < 10f && !longFired) {
                            if (m == Mode.Nav) key("enter")
                            else if (m == Mode.Mouse && !dragging) { click(1); lastTapEnd = lastT }
                        } else if (maxp == 2 && dt < 260 && moved < 14f) {
                            if (m == Mode.Nav) act(strings.backAction, "back") else click(2)
                        }
                    }
                    if (dragging) btn(1, false)
                    dot = null
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val accent = Ios.c.accent
        Canvas(Modifier.fillMaxSize()) {
            dot?.let { drawCircle(accent.copy(alpha = 0.22f), 34.dp.toPx(), it) }
            val e = 6.dp.toPx(); val len = 56.dp.toPx()
            when (edgeHint) {
                1 -> drawRect(accent.copy(alpha = 0.18f), Offset(0f, 0f), androidx.compose.ui.geometry.Size(len, size.height))
                2 -> drawRect(accent.copy(alpha = 0.18f), Offset(size.width - len, 0f), androidx.compose.ui.geometry.Size(len, size.height))
                3 -> drawRect(accent.copy(alpha = 0.18f), Offset(0f, size.height - len), androidx.compose.ui.geometry.Size(size.width, len))
            }
            @Suppress("UNUSED_VARIABLE") val u = e
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T(mode.title(st.strings), size = 22f, color = Ios.c.label.copy(alpha = 0.9f))
            T(
                mode.hint(st.strings), Modifier.padding(top = 8.dp, start = 20.dp, end = 20.dp), size = 13f,
                align = TextAlign.Center, color = Ios.c.secondary,
            )
        }
        AnimatedVisibility(
            visible = flash != null, modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
            enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut() + scaleOut(targetScale = 0.9f),
        ) {
            T(
                flash ?: "", size = 15f, weight = FontWeight.SemiBold, color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Ios.c.accent).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** Dikey ses seridi: ustte/ortada/altta dokun, ya da sur\u00fckle (kesintisiz ses). */
@Composable
fun VolumeStrip(tv: TvClient, buzz: (Long) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf(false) }
    val c = Ios.c
    Box(
        modifier
            .clip(RoundedCornerShape(32.dp))
            .background(if (active) c.accent.copy(alpha = 0.16f) else c.card)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val h = size.height.toFloat()
                    val zone = when { down.position.y < h * 0.33f -> "vol_up"; down.position.y > h * 0.67f -> "vol_down"; else -> "mute" }
                    var acc = 0f
                    var travel = 0f
                    active = true
                    val rep = scope.launch {
                        delay(380)
                        if (travel < 10f && zone != "mute") while (true) { tv.key(zone); delay(110) }
                    }
                    do {
                        val ev = awaitPointerEvent()
                        for (ch in ev.changes) {
                            if (ch.pressed && ch.previousPressed) {
                                val dy = ch.positionChange().y / density
                                travel += abs(dy); acc += -dy
                                if (travel > 10f) rep.cancel()
                                while (acc >= 20f) { tv.key("vol_up"); buzz(6); acc -= 20f }
                                while (acc <= -20f) { tv.key("vol_down"); buzz(6); acc += 20f }
                            }
                            ch.consume()
                        }
                    } while (ev.changes.any { it.pressed })
                    rep.cancel()
                    if (travel < 10f) { tv.key(zone); buzz(8) }
                    active = false
                }
            },
    ) {
        Column(Modifier.fillMaxSize().padding(vertical = 22.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
            Glyph(Ic.VolUp, c.label, 26.dp, filled = true)
            Glyph(Ic.Mute, c.label.copy(alpha = 0.7f), 24.dp, filled = true)
            Glyph(Ic.VolDown, c.label, 26.dp, filled = true)
        }
    }
}