package kz.okno.proyom

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Системные цвета iOS (светлая и тёмная тема), чтобы Android выглядел так же, как iPhone. */
@Immutable
data class Palette(
    val dark: Boolean,
    val bg: Color,          // systemGroupedBackground
    val card: Color,        // secondarySystemGroupedBackground
    val text: Color,
    val secondary: Color,
    val tertiary: Color,
    val separator: Color,
    val fill: Color,        // поле поиска, дорожка сегментов, степпер
    val selected: Color,    // выбранный сегмент
    val bar: Color,         // нижняя панель
)

val LightPalette = Palette(
    dark = false, bg = Color(0xFFF2F2F7), card = Color.White, text = Color.Black, secondary = Color(0xFF8A8A8E),
    tertiary = Color(0xFFC4C4C6), separator = Color(0xFFD9D9DC), fill = Color(0xFFE9E9EB), selected = Color.White, bar = Color(0xFFF9F9F9),
)
val DarkPalette = Palette(
    dark = true, bg = Color.Black, card = Color(0xFF1C1C1E), text = Color.White, secondary = Color(0xFF8D8D93),
    tertiary = Color(0xFF48484A), separator = Color(0xFF38383A), fill = Color(0xFF2C2C2E), selected = Color(0xFF636366), bar = Color(0xFF121212),
)
val LocalPalette = staticCompositionLocalOf { LightPalette }

@Composable fun pal(): Palette = LocalPalette.current

// ---------- Заголовки ----------

/** Заголовок экрана по центру, как в навигации iOS. */
@Composable
fun TopBar(title: String, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp)) {
        Box(Modifier.align(Alignment.CenterStart)) { leading?.invoke() }
        Text(title, Modifier.align(Alignment.Center).padding(horizontal = if (leading != null || trailing != null) 84.dp else 0.dp),
            fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = pal().text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.align(Alignment.CenterEnd)) { trailing?.invoke() }
    }
}

@Composable
fun LargeTitle(text: String) {
    Text(text, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = pal().text, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp))
}

// ---------- Группы (inset grouped list) ----------

@Composable
fun Group(header: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = pal()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (header != null) {
            Text(header.uppercase(), fontSize = 13.sp, color = p.secondary, modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 7.dp))
        } else Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.card)) { content() }
        if (footer != null) {
            Text(footer, fontSize = 13.sp, color = p.secondary, lineHeight = 17.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp))
        }
    }
}

/** Разделитель между строками группы (с отступом слева, как в iOS). */
@Composable
fun Sep(inset: Dp = 16.dp) = HorizontalDivider(Modifier.padding(start = inset), thickness = 0.5.dp, color = pal().separator)

@Composable
fun RowBase(onClick: (() -> Unit)? = null, minHeight: Dp = 44.dp, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = minHeight).let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun ValueRow(label: String, value: String, bold: Boolean = false, valueColor: Color? = null) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxValue = (maxWidth - 44.dp) * 0.58f
        RowBase {
            Text(label, color = pal().text, fontSize = 17.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(value, color = valueColor ?: pal().secondary, fontSize = 17.sp, textAlign = TextAlign.End,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.widthIn(max = maxValue))
        }
    }
}

@Composable
fun NoteRow(text: String) = RowBase(minHeight = 0.dp) { Text(text, color = pal().secondary, fontSize = 13.sp, lineHeight = 17.sp) }

/** Выбор из списка: значение акцентным цветом и значок ⌃⌄, как Picker в iOS. */
@Composable
fun <T> PickerRow(label: String, value: String, options: List<T>, title: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RowBase(onClick = { open = true }) {
            Text(label, color = pal().text, fontSize = 17.sp, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(12.dp))
            Text(value, color = Accent, fontSize = 17.sp, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.UnfoldMore, null, tint = Accent, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(pal().card)) {
            options.forEach { o -> DropdownMenuItem(text = { Text(title(o), color = pal().text) }, onClick = { onPick(o); open = false }) }
        }
    }
}

/** Где сейчас поле ввода с фокусом (в координатах корня окна) — чтобы тап по нему не прятал клавиатуру. */
object FocusedField { var bounds: Rect? = null }

/** Запоминает положение поля, пока оно в фокусе. */
fun Modifier.trackFocus(onFocus: (Boolean) -> Unit = {}): Modifier = composed {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var focused by remember { mutableStateOf(false) }
    onGloballyPositioned { coords = it; if (focused) FocusedField.bounds = it.boundsInRoot() }
        .onFocusChanged { f ->
            if (f.isFocused == focused) return@onFocusChanged
            focused = f.isFocused
            FocusedField.bounds = if (f.isFocused) coords?.boundsInRoot() else null
            onFocus(f.isFocused)
        }
}

/**
 * Как на iPhone: тап мимо поля или прокрутка прячут клавиатуру и фиксируют введённое число.
 * Срабатывает до кнопок (проход Initial), поэтому кнопка уже видит новое значение.
 */
fun Modifier.dismissKeyboard(): Modifier = composed {
    val focus = LocalFocusManager.current
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    onGloballyPositioned { coords = it }.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val inField = { p: Offset -> FocusedField.bounds?.contains(coords?.localToRoot(p) ?: p) == true }
            if (inField(down.position)) return@awaitEachGesture
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    if (FocusedField.bounds != null) focus.clearFocus()
                    break
                }
                if (!ch.pressed) {
                    if (FocusedField.bounds != null) focus.clearFocus()
                    break
                }
            }
        }
    }
}

/**
 * Числовое поле справа: только цифровая клавиатура (как номеронабиратель).
 * Значение применяется по «Готово», тапу мимо или прокрутке — как на iPhone, без пересчёта на каждую цифру.
 */
@Composable
fun NumberField(value: Long, min: Long, width: Dp, onCommit: (Long) -> Unit) {
    val focus = LocalFocusManager.current
    var text by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) { if (!focused) text = value.toString() }
    BasicTextField(
        value = text,
        onValueChange = { t -> text = t.filter { it.isDigit() }.take(9) },
        singleLine = true,
        textStyle = TextStyle(color = if (focused) Accent else pal().text, fontSize = 17.sp, textAlign = TextAlign.End),
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = Modifier.width(width).trackFocus { f ->
            focused = f
            if (!f) {
                val v = text.toLongOrNull()
                if (v != null && v >= min && v != value) onCommit(v) else text = value.toString()
            }
        },
    )
}

@Composable
fun NumberRow(label: String, value: Int, suffix: String = "", min: Int = 1, onChange: (Int) -> Unit) = RowBase {
    Text(label, color = pal().text, fontSize = 17.sp, modifier = Modifier.weight(1f))
    NumberField(value.toLong(), min.toLong(), 96.dp) { onChange(it.toInt()) }
    if (suffix.isNotEmpty()) Text(" $suffix", color = pal().secondary, fontSize = 17.sp)
}

/** Ставка в настройках (целое число, можно 0). */
@Composable
fun RateRow(label: String, value: Double, suffix: String, onChange: (Double) -> Unit) = RowBase {
    Text(label, color = pal().text, fontSize = 17.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f))
    Spacer(Modifier.width(8.dp))
    NumberField(Math.round(value), 0, 96.dp) { onChange(it.toDouble()) }
    Text(" $suffix", color = pal().secondary, fontSize = 17.sp)
}

/** Форма скругления для строки ленивого списка внутри группы. */
fun groupShape(index: Int, count: Int): RoundedCornerShape {
    val r = 10.dp
    return RoundedCornerShape(
        topStart = if (index == 0) r else 0.dp, topEnd = if (index == 0) r else 0.dp,
        bottomStart = if (index == count - 1) r else 0.dp, bottomEnd = if (index == count - 1) r else 0.dp,
    )
}

/** Обёртка строки ленивого списка: фон карточки, скругления у первой/последней, разделитель. */
@Composable
fun GroupItem(index: Int, count: Int, inset: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(groupShape(index, count)).background(pal().card)) {
        if (index > 0) Sep(inset)
        content()
    }
}

@Composable
fun GroupHeader(text: String) =
    Text(text.uppercase(), fontSize = 13.sp, color = pal().secondary, modifier = Modifier.padding(start = 32.dp, top = 18.dp, bottom = 7.dp, end = 32.dp))

@Composable
fun GroupFooter(text: String) =
    Text(text, fontSize = 13.sp, color = pal().secondary, lineHeight = 17.sp, modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 7.dp))

/** Текстовое поле во всю строку (название компании, формула стеклопакета). */
@Composable
fun TextRow(placeholder: String, value: String, label: String? = null, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    var text by remember { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) { if (!focused) text = value }
    RowBase {
        if (label != null) {
            Text(label, color = pal().text, fontSize = 17.sp)
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) Text(placeholder, color = pal().tertiary, fontSize = 17.sp, maxLines = 1,
                textAlign = if (label != null) TextAlign.End else TextAlign.Start, modifier = Modifier.fillMaxWidth())
            BasicTextField(
                value = text, onValueChange = { text = it; onChange(it) }, singleLine = true,
                textStyle = TextStyle(color = pal().text, fontSize = 17.sp, textAlign = if (label != null) TextAlign.End else TextAlign.Start),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().trackFocus { focused = it },
            )
        }
    }
}

/** Степпер «− | +», как в iOS. */
@Composable
fun StepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) = RowBase {
    val p = pal()
    Text(label, color = p.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
    Text(value, color = p.secondary, fontSize = 17.sp)
    Spacer(Modifier.width(12.dp))
    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(p.fill).height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(47.dp).fillMaxHeight().clickable(onClick = onMinus), contentAlignment = Alignment.Center) { Text("−", color = p.text, fontSize = 22.sp) }
        Box(Modifier.width(1.dp).height(18.dp).background(p.tertiary))
        Box(Modifier.width(47.dp).fillMaxHeight().clickable(onClick = onPlus), contentAlignment = Alignment.Center) { Text("+", color = p.text, fontSize = 22.sp) }
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) = RowBase {
    Text(label, color = pal().text, fontSize = 17.sp, modifier = Modifier.weight(1f))
    Switch(
        checked = checked, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = Accent, checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent,
            uncheckedTrackColor = pal().fill, uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** Строка-переход со стрелкой «›». */
@Composable
fun NavRow(label: String, value: String = "", sub: String? = null, leading: (@Composable () -> Unit)? = null,
           valueColor: Color? = null, onClick: () -> Unit) = RowBase(onClick = onClick) {
    if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
    Column(Modifier.weight(1f)) {
        Text(label, color = pal().text, fontSize = 17.sp)
        if (sub != null) Text(sub, color = pal().secondary, fontSize = 13.sp, lineHeight = 16.sp)
    }
    if (value.isNotEmpty()) Text(value, color = valueColor ?: pal().secondary, fontSize = 17.sp)
    Text("  ›", color = pal().tertiary, fontSize = 22.sp)
}

/** Строка-кнопка (акцентный текст, иконка слева). */
@Composable
fun ButtonRow(text: String, color: Color = Accent, enabled: Boolean = true, icon: (@Composable () -> Unit)? = null, onClick: () -> Unit) =
    RowBase(onClick = if (enabled) onClick else null) {
        if (icon != null) { icon(); Spacer(Modifier.width(12.dp)) }
        Text(text, color = if (enabled) color else pal().secondary, fontSize = 17.sp)
    }

// ---------- Сегменты и поиск ----------

/** Сегментированный переключатель, как UISegmentedControl. */
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val p = pal()
    BoxWithConstraints(modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(9.dp)).background(p.fill).padding(2.dp)) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, label = "seg")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().shadow(2.dp, RoundedCornerShape(7.dp)).clip(RoundedCornerShape(7.dp)).background(p.selected))
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, t ->
                Box(Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center) {
                    Text(t, color = p.text, fontSize = 13.sp, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun SearchField(query: String, placeholder: String, onChange: (String) -> Unit) {
    val p = pal()
    val focus = LocalFocusManager.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(36.dp).clip(RoundedCornerShape(10.dp)).background(p.fill).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Filled.Search, null, tint = p.secondary, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(placeholder, color = p.secondary, fontSize = 17.sp, maxLines = 1)
            BasicTextField(query, onChange, singleLine = true, textStyle = TextStyle(color = p.text, fontSize = 17.sp),
                cursorBrush = SolidColor(Accent), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }), modifier = Modifier.fillMaxWidth().trackFocus())
        }
        if (query.isNotEmpty()) Text("✕", color = p.secondary, fontSize = 15.sp, modifier = Modifier.clickable { onChange("") }.padding(4.dp))
    }
}
