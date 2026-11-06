package com.stocktrack.app.ui

import android.content.res.Configuration
import androidx.compose.ui.res.stringResource
import com.stocktrack.app.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stocktrack.app.data.ActionType
import com.stocktrack.app.data.DEFAULT_UNIT
import com.stocktrack.app.data.GuiScheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The colours of one GUI color scheme. [flat] is the clean look of the web page: no shadows or background waves. */
class Palette(
    val primary: Color,
    val primaryDark: Color,
    val mint: Color,
    val mintDark: Color,
    val appBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val flat: Boolean = false
)

private val MintPalette = Palette(
    primary = Color(0xFF087F5B),
    primaryDark = Color(0xFF075C46),
    mint = Color(0xFFDDF5E9),
    mintDark = Color(0xFFBDEBD6),
    appBackground = Color(0xFFF8FBF9),
    textPrimary = Color(0xFF17352E),
    textSecondary = Color(0xFF71817C),
    border = Color(0xFFE2EAE5)
)

private val BluePalette = Palette(
    primary = Color(0xFF1565C0),
    primaryDark = Color(0xFF0D47A1),
    mint = Color(0xFFE8F1FB),
    mintDark = Color(0xFFCFE0F5),
    appBackground = Color(0xFFF7FAFD),
    textPrimary = Color(0xFF12263F),
    textSecondary = Color(0xFF5A6B80),
    border = Color(0xFFDFE8F2),
    flat = true
)

private val VioletPalette = Palette(
    primary = Color(0xFF6A1B9A),
    primaryDark = Color(0xFF4A148C),
    mint = Color(0xFFF3E5F5),
    mintDark = Color(0xFFE1BEE7),
    appBackground = Color(0xFFFCF8FD),
    textPrimary = Color(0xFF2E1238),
    textSecondary = Color(0xFF7A5F85),
    border = Color(0xFFEEDFF1)
)

private val PinkPalette = Palette(
    primary = Color(0xFFC2185B),
    primaryDark = Color(0xFF880E4F),
    mint = Color(0xFFFCE4EC),
    mintDark = Color(0xFFF8BBD0),
    appBackground = Color(0xFFFFF9FB),
    textPrimary = Color(0xFF3A1226),
    textSecondary = Color(0xFF8A6073),
    border = Color(0xFFF7E1E9)
)

private val AmberPalette = Palette(
    primary = Color(0xFFB45F06),
    primaryDark = Color(0xFF7A3F00),
    mint = Color(0xFFFBEFD9),
    mintDark = Color(0xFFF3D9A8),
    appBackground = Color(0xFFFFFBF4),
    textPrimary = Color(0xFF3B2A14),
    textSecondary = Color(0xFF7C6A52),
    border = Color(0xFFEFE3CF)
)

/** The only two-tone scheme: lagoon-teal buttons and text on warm coral highlights and a sandy background. */
private val LagoonPalette = Palette(
    primary = Color(0xFF007C89),
    primaryDark = Color(0xFF00505A),
    mint = Color(0xFFFFE6DC),
    mintDark = Color(0xFFFFC9B3),
    appBackground = Color(0xFFFFFAF5),
    textPrimary = Color(0xFF0E2F36),
    textSecondary = Color(0xFF5E7377),
    border = Color(0xFFF4E4DA)
)

/** The main color of a scheme as "#rrggbb", for the web page. */
fun schemeColorHex(scheme: GuiScheme): String = "#%06x".format(paletteOf(scheme).primary.toArgb() and 0xFFFFFF)

private fun paletteOf(scheme: GuiScheme): Palette = when (scheme) {
    GuiScheme.MINT -> MintPalette
    GuiScheme.BLUE -> BluePalette
    GuiScheme.AMBER -> AmberPalette
    GuiScheme.VIOLET -> VioletPalette
    GuiScheme.PINK -> PinkPalette
    GuiScheme.LAGOON -> LagoonPalette
}

/** The design tokens of the chosen scheme. Screens use these (or the colour scheme built from them). */
object DentTrackColors {
    /** Held as state, so everything that reads a token redraws when the scheme changes. */
    var palette by mutableStateOf(MintPalette)

    val Primary get() = palette.primary
    val PrimaryDark get() = palette.primaryDark
    val Mint get() = palette.mint
    val MintDark get() = palette.mintDark
    val AppBackground get() = palette.appBackground
    val Surface = Color(0xFFFFFFFF)
    val TextPrimary get() = palette.textPrimary
    val TextSecondary get() = palette.textSecondary
    val Border get() = palette.border
    val Error = Color(0xFFB42318)
}

val Green = Color(0xFF2E7D32)
val Orange = Color(0xFFE65100)
val Red = DentTrackColors.Error
val Accent get() = DentTrackColors.Primary
val Grey = Color(0xFF5B6B8C)

private fun colorSchemeOf(palette: Palette) = lightColorScheme(
    primary = palette.primary,
    onPrimary = Color.White,
    primaryContainer = palette.mint,
    onPrimaryContainer = palette.primaryDark,
    secondary = palette.primaryDark,
    secondaryContainer = palette.mint,
    onSecondaryContainer = palette.primaryDark,
    background = palette.appBackground,
    onBackground = palette.textPrimary,
    surface = DentTrackColors.Surface,
    onSurface = palette.textPrimary,
    // Without these, dialogs and menus take a lavender tint from the Material default palette.
    surfaceContainerLowest = DentTrackColors.Surface,
    surfaceContainerLow = DentTrackColors.Surface,
    surfaceContainer = DentTrackColors.Surface,
    surfaceContainerHigh = DentTrackColors.Surface,
    surfaceContainerHighest = palette.appBackground,
    surfaceTint = palette.mint,
    surfaceVariant = palette.mint,
    onSurfaceVariant = palette.textSecondary,
    outline = palette.textSecondary,
    outlineVariant = palette.border,
    error = DentTrackColors.Error
)

/** All text in the app is larger than the Material default, so it reads easily on a clinic tablet. */
private const val TEXT_SCALE = 1.18f

private fun TextStyle.larger(): TextStyle =
    copy(fontSize = fontSize * TEXT_SCALE, lineHeight = lineHeight * TEXT_SCALE)

private val LargeTypography = Typography().run {
    copy(
        displayLarge = displayLarge.larger(),
        displayMedium = displayMedium.larger(),
        displaySmall = displaySmall.larger(),
        headlineLarge = headlineLarge.larger(),
        headlineMedium = headlineMedium.larger(),
        headlineSmall = headlineSmall.larger(),
        titleLarge = titleLarge.larger(),
        titleMedium = titleMedium.larger(),
        titleSmall = titleSmall.larger(),
        bodyLarge = bodyLarge.larger(),
        bodyMedium = bodyMedium.larger(),
        bodySmall = bodySmall.larger(),
        labelLarge = labelLarge.larger(),
        labelMedium = labelMedium.larger(),
        labelSmall = labelSmall.larger()
    )
}

private val SoftShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DentTrackTheme(scheme: GuiScheme, content: @Composable () -> Unit) {
    // Set here, before the screens below are composed, so they never draw a frame in the old colours.
    val colors = remember(scheme) {
        val palette = paletteOf(scheme)
        DentTrackColors.palette = palette
        colorSchemeOf(palette)
    }
    MaterialTheme(colorScheme = colors, typography = LargeTypography, shapes = SoftShapes, content = content)
}

/** White rounded card used for every block on screen. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (DentTrackColors.palette.flat) 2.dp else 1.dp, DentTrackColors.Border),
        shadowElevation = if (DentTrackColors.palette.flat) 0.dp else 2.dp
    ) {
        Column(content = content)
    }
}

/** Called on every touch so the inactivity timer (Settings) knows somebody is still there. */
val LocalActivityPing = staticCompositionLocalOf<() -> Unit> { {} }

/** Put inside a dialog: while it is open nobody is "inactive" (typing a note takes time). */
@Composable
fun KeepAwakeWhileShown() {
    val ping = LocalActivityPing.current
    LaunchedEffect(Unit) {
        while (true) {
            ping()
            delay(10_000)
        }
    }
}

/**
 * A compact dialog for forms. Cancel and Save sit in a bar at the TOP, so the on-screen keyboard
 * can never hide them (on a landscape tablet it covers the bottom half of the screen). The form
 * below the bar scrolls when there is not enough room.
 */
@Composable
fun CompactDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    confirmColor: Color = MaterialTheme.colorScheme.primary,
    /** Text of the left-hand button; null hides it (for windows with a single "Done"). */
    dismissLabel: String? = stringResource(R.string.cancel),
    content: @Composable ColumnScope.() -> Unit
) {
    KeepAwakeWhileShown()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dismissLabel != null) {
                        TextButton(onClick = onDismiss) { Text(dismissLabel) }
                    } else {
                        Spacer(Modifier.width(72.dp))
                    }
                    Text(
                        title,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Button(
                        onClick = onConfirm,
                        enabled = confirmEnabled,
                        colors = ButtonDefaults.buttonColors(containerColor = confirmColor)
                    ) { Text(confirmLabel) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
fun Avatar(name: String, size: Dp = 40.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(DentTrackColors.Mint, DentTrackColors.MintDark))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).uppercase(),
            color = DentTrackColors.PrimaryDark,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.56f).sp,
            lineHeight = (size.value * 0.56f).sp
        )
    }
}

@Composable
fun EmptyText(text: String, modifier: Modifier = Modifier) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The unit as the screens show it: the default unit follows the chosen language, typed units stay as typed. */
@Composable
fun unitLabel(unit: String): String =
    if (unit.equals(DEFAULT_UNIT, ignoreCase = true)) stringResource(R.string.unit_pcs) else unit

@Composable
fun actionLabel(type: String): String = when (type) {
    ActionType.CHECK_IN -> stringResource(R.string.check_in)
    ActionType.CHECK_OUT -> stringResource(R.string.check_out)
    ActionType.ITEM_ADDED -> stringResource(R.string.action_item_added)
    ActionType.ITEM_EDITED -> stringResource(R.string.action_item_edited)
    ActionType.ITEM_REMOVED -> stringResource(R.string.action_item_removed)
    else -> type
}

fun actionColor(type: String): Color = when (type) {
    ActionType.CHECK_IN -> Green
    ActionType.CHECK_OUT -> Orange
    ActionType.ITEM_ADDED -> Accent
    ActionType.ITEM_REMOVED -> Red
    else -> Grey
}

fun formatTime(ms: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))

fun formatMoney(v: Double): String = String.format(Locale.getDefault(), "%,.2f", v)

fun formatChange(change: Int): String = when {
    change > 0 -> "+$change"
    change < 0 -> "$change"
    else -> "—"
}

/** True while the tablet is turned upright: the wide tables leave out a column then. */
@Composable
fun isPortrait(): Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
