package com.lowell.tvkumanda

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/** iOS tarzi renk paleti (acik/koyu). Material 3 kullanilmaz. */
data class IosColors(
    val dark: Boolean,
    val bg: Color, val card: Color, val fill: Color, val label: Color, val secondary: Color,
    val accent: Color, val danger: Color, val sep: Color, val green: Color,
)

val IosLight = IosColors(false, Color(0xFFF2F2F7), Color.White, Color(0xFFE5E5EA), Color.Black, Color(0xFF8E8E93),
    Color(0xFF007AFF), Color(0xFFFF3B30), Color(0xFFC6C6C8), Color(0xFF34C759))
val IosDark = IosColors(true, Color(0xFF121212), Color(0xFF1E1E1E), Color(0xFF2C2C2E), Color.White, Color(0xFF98989F),
    Color(0xFF0A84FF), Color(0xFFFF453A), Color(0xFF3A3A3C), Color(0xFF30D158))

val LocalIos = staticCompositionLocalOf { IosLight }

object Ios {
    val c: IosColors @Composable get() = LocalIos.current
}

@Composable
fun IosTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIos provides if (dark) IosDark else IosLight, content = content)
}

@Composable
fun T(
    text: String, modifier: Modifier = Modifier, size: Float = 17f, weight: FontWeight = FontWeight.Normal,
    color: Color = Ios.c.label, align: TextAlign = TextAlign.Start, maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text, modifier,
        style = TextStyle(color = color, fontSize = size.sp, fontWeight = weight, textAlign = align, letterSpacing = (-0.2).sp),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Glyph(icon: ImageVector, tint: Color = Ios.c.label, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Image(icon, null, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** Solar ikonu: filled = Bold, degilse Linear. */
@Composable
fun Glyph(icon: Solar, tint: Color = Ios.c.label, size: Dp = 24.dp, modifier: Modifier = Modifier, filled: Boolean = false) {
    Glyph(if (filled) icon.bold else icon.linear, tint, size, modifier)
}

/** Basinca yayli kuculme + hafif solma. */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val a by animateFloatAsState(if (pressed) 0.6f else 1f, label = "pa")
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "ps")
    return this.graphicsLayer { alpha = a; scaleX = s; scaleY = s }.clickable(interactionSource = src, indication = null, onClick = onClick)
}
enum class BtnStyle { Filled, Tinted, Gray, Danger }

@Composable
fun IosButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: BtnStyle = BtnStyle.Tinted, enabled: Boolean = true) {
    val c = Ios.c
    val (bg, fg) = when (style) {
        BtnStyle.Filled -> c.accent to Color.White
        BtnStyle.Tinted -> c.accent.copy(alpha = 0.15f) to c.accent
        BtnStyle.Gray -> c.fill to c.label
        BtnStyle.Danger -> c.danger.copy(alpha = 0.15f) to c.danger
    }
    Box(
        modifier.height(50.dp).graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(RoundedCornerShape(14.dp)).background(bg).then(if (enabled) Modifier.pressable(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { T(text, color = fg, weight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
fun IosSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Ios.c
    BoxWithConstraints(modifier.clip(RoundedCornerShape(10.dp)).background(c.fill).padding(2.dp)) {
        val itemW = maxWidth / options.size
        val x by animateDpAsState(itemW * selected, spring(dampingRatio = 0.78f, stiffness = 450f), label = "seg")
        Box(
            Modifier.offset(x).width(itemW).fillMaxHeight().shadow(2.dp, RoundedCornerShape(8.dp))
                .background(if (c.dark) Color(0xFF636366) else Color.White, RoundedCornerShape(8.dp)),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, o ->
                val sel = i == selected
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) { T(o, size = 14f, weight = if (sel) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1) }
            }
        }
    }
}

@Composable
fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Ios.c
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, label = "sw")
    Box(
        Modifier.size(51.dp, 31.dp).clip(CircleShape).background(if (checked) c.green else c.fill)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!checked) }.padding(2.dp),
    ) {
        Box(Modifier.offset(x).size(27.dp).shadow(2.dp, CircleShape).background(Color.White, CircleShape))
    }
}

@Composable
fun IosSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val c = Ios.c
    var w by remember { androidx.compose.runtime.mutableIntStateOf(1) }
    fun frac(x: Float) = (x / w).coerceIn(0f, 1f)
    fun set(x: Float) = onChange(range.start + frac(x) * (range.endInclusive - range.start))
    val f = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    Box(
        modifier.height(32.dp).onSizeChanged { w = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) { detectTapGestures { set(it.x) } }
            .pointerInput(Unit) { detectHorizontalDragGestures(onDragStart = { set(it.x) }) { ch, _ -> set(ch.position.x) } },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val h = 4.dp.toPx(); val y = size.height / 2
            drawRoundRect(c.fill, Offset(0f, y - h / 2), Size(size.width, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
            drawRoundRect(c.accent, Offset(0f, y - h / 2), Size(size.width * f, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
            val r = 14.dp.toPx(); val cx = (size.width - 2 * r) * f + r
            drawCircle(Color.Black.copy(alpha = 0.18f), r + 1.dp.toPx(), Offset(cx, y + 1.dp.toPx()))
            drawCircle(Color.White, r, Offset(cx, y))
        }
    }
}

@Composable
fun IosField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, singleLine: Boolean = false) {
    val c = Ios.c
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(c.card).padding(horizontal = 14.dp, vertical = 12.dp)) {
        if (value.isEmpty()) T(placeholder, color = c.secondary)
        BasicTextField(
            value, onChange, Modifier.fillMaxWidth(), singleLine = singleLine,
            textStyle = TextStyle(color = c.label, fontSize = 17.sp), cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        )
    }
}

/** Gruplu (inset) liste kutusu. */
@Composable
fun IosGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ios.c.card)) { content() }
}

@Composable
fun IosRow(
    text: String, onClick: (() -> Unit)? = null, color: Color = Ios.c.label, last: Boolean = false,
    leading: Solar? = null, trailing: @Composable (() -> Unit)? = null,
) {
    val c = Ios.c
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.pressable(onClick) else Modifier)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) { Glyph(leading, c.accent, 20.dp); Box(Modifier.width(12.dp)) }
            T(text, Modifier.weight(1f), color = color, maxLines = 1)
            trailing?.invoke()
        }
        if (!last) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(c.sep))
    }
}

@Composable
fun SectionHeader(text: String) {
    T(text.uppercase(), Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp), size = 13f, color = Ios.c.secondary)
}

enum class Role { Normal, Bold, Danger }
data class Act(val label: String, val role: Role = Role.Normal, val onClick: () -> Unit)

/** iOS uyari penceresi. */
@Composable
fun IosAlert(title: String, message: String?, actions: List<Act>, onDismiss: () -> Unit) {
    val c = Ios.c
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(290.dp).clip(RoundedCornerShape(20.dp)).background(if (c.dark) Color(0xFF2C2C2E) else Color(0xFFF9F9F9)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T(title, weight = FontWeight.SemiBold, align = TextAlign.Center)
                if (message != null) T(message, size = 13f, align = TextAlign.Center, color = c.label.copy(alpha = 0.85f))
            }
            actions.forEach { a ->
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.sep))
                Box(Modifier.fillMaxWidth().height(46.dp).pressable(a.onClick), contentAlignment = Alignment.Center) {
                    T(a.label, color = if (a.role == Role.Danger) c.danger else c.accent, weight = if (a.role == Role.Bold) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

/** Altta acilan iOS aksiyon sayfasi. */
@Composable
fun IosSheet(title: String?, actions: List<Act>, cancelText: String = "Cancel", onDismiss: () -> Unit) {
    val c = Ios.c
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (c.dark) Color(0xFF2C2C2E) else Color(0xFFF9F9F9))) {
                    if (title != null) {
                        T(title, Modifier.fillMaxWidth().padding(14.dp), size = 13f, color = c.secondary, align = TextAlign.Center)
                    }
                    actions.forEachIndexed { i, a ->
                        if (i > 0 || title != null) Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.sep))
                        Box(Modifier.fillMaxWidth().height(56.dp).pressable(a.onClick), contentAlignment = Alignment.Center) {
                            T(a.label, size = 20f, color = if (a.role == Role.Danger) c.danger else c.accent)
                        }
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(if (c.dark) Color(0xFF2C2C2E) else Color.White).pressable(onDismiss),
                    contentAlignment = Alignment.Center,
                ) { T(cancelText, size = 20f, weight = FontWeight.SemiBold, color = c.accent) }
            }
        }
    }
}

/** Genis iOS sayfasi (ayarlar). */
@Composable
fun IosPage(title: String, onClose: () -> Unit, doneText: String = "Done", content: @Composable () -> Unit) {
    val c = Ios.c
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(c.bg),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                T(title, Modifier.weight(1f), size = 22f, weight = FontWeight.Bold)
                T(doneText, Modifier.pressable(onClose), color = c.accent, weight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) { content() }
        }
    }
}

