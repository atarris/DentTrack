package com.stocktrack.app.ui

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stocktrack.app.data.ActionType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The mint and emerald design tokens. Screens use these (or the colour scheme built from them). */
object DentTrackColors {
    val Primary = Color(0xFF087F5B)
    val PrimaryDark = Color(0xFF075C46)
    val Mint = Color(0xFFDDF5E9)
    val MintDark = Color(0xFFBDEBD6)
    val AppBackground = Color(0xFFF8FBF9)
    val Surface = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF17352E)
    val TextSecondary = Color(0xFF71817C)
    val Border = Color(0xFFE2EAE5)
    val Error = Color(0xFFB42318)
}

val Green = Color(0xFF2E7D32)
val Orange = Color(0xFFE65100)
val Red = DentTrackColors.Error
val Accent = DentTrackColors.Primary
val Grey = Color(0xFF5B6B8C)

private val LightColors = lightColorScheme(
    primary = DentTrackColors.Primary,
    onPrimary = Color.White,
    primaryContainer = DentTrackColors.Mint,
    onPrimaryContainer = DentTrackColors.PrimaryDark,
    secondary = DentTrackColors.PrimaryDark,
    secondaryContainer = DentTrackColors.Mint,
    onSecondaryContainer = DentTrackColors.PrimaryDark,
    background = DentTrackColors.AppBackground,
    onBackground = DentTrackColors.TextPrimary,
    surface = DentTrackColors.Surface,
    onSurface = DentTrackColors.TextPrimary,
    surfaceVariant = DentTrackColors.Mint,
    onSurfaceVariant = DentTrackColors.TextSecondary,
    outline = DentTrackColors.TextSecondary,
    outlineVariant = DentTrackColors.Border,
    error = DentTrackColors.Error
)

@Composable
fun DentTrackTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, typography = Typography(), content = content)
}

/** White rounded card used for every block on screen. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
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
    dismissLabel: String? = "Cancel",
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
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).uppercase(),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            style = if (size >= 56.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun EmptyText(text: String, modifier: Modifier = Modifier) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

fun actionLabel(type: String): String = when (type) {
    ActionType.CHECK_IN -> "Check in"
    ActionType.CHECK_OUT -> "Check out"
    ActionType.ITEM_ADDED -> "Item added"
    ActionType.ITEM_EDITED -> "Item edited"
    ActionType.ITEM_REMOVED -> "Item removed"
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
