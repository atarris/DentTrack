package com.stocktrack.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stocktrack.app.R
import com.stocktrack.app.StockViewModel
import com.stocktrack.app.data.User
import com.stocktrack.app.data.isAdminAccount
import kotlinx.coroutines.delay

private const val LOGO_ASPECT = 1200f / 165f

/** The "DentTrack" name is this much of the logo's width. */
private const val TITLE_PER_LOGO_WIDTH = 0.8f

// Start screen measurements in dp, used to fit two rows of cards on the screen.
private const val SCREEN_PADDING_V = 16f
private const val TITLE_GAP = 4f
private const val SUBTITLE_LINE = 33f
private const val HEADER_BOTTOM = 6f
private const val CARD_PADDING_V = 16f
private const val NAME_GAP = 8f
private const val NAME_LINE = 38f
private const val FULL_AVATAR = 96f

/** Logo height plus the name's line height, per dp of logo width. */
private const val HEADER_PER_LOGO_WIDTH = 1f / LOGO_ASPECT + 0.258f * TITLE_PER_LOGO_WIDTH

/**
 * Start screen: nobody signs in, tapping a name opens that person's menu straight away.
 * Holding the "DentTrack" title for 3 seconds opens the hidden Admin sign-in ([onAdminTrigger]).
 * Users are added and removed by the Admin (Users tab).
 */
@Composable
fun UserSelectScreen(vm: StockViewModel, users: List<User>, onAdminTrigger: () -> Unit) {
    MarbleBackground(Modifier.fillMaxSize()) {
        UserMenuGrid(
            users = users,
            onAdminTrigger = onAdminTrigger,
            onPick = { user -> if (user.isAdminAccount()) onAdminTrigger() else vm.selectUser(user.id) }
        )
    }
}

/**
 * Three columns on a landscape tablet, fewer on narrow screens. A row that is not full is centred.
 * Up to two rows of cards always fit without scrolling: the logo and the avatars shrink as far as the
 * screen height needs. Three or more rows keep the full size and scroll.
 */
@Composable
private fun UserMenuGrid(users: List<User>, onAdminTrigger: () -> Unit, onPick: (User) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = when {
            maxWidth >= 840.dp -> 3
            maxWidth >= 520.dp -> 2
            else -> 1
        }
        val gap = 18.dp
        val contentWidth = minOf(maxWidth - 64.dp, 1096.dp)
        val cardWidth = (contentWidth - gap * (columns - 1)) / columns
        val rows = (users.size + columns - 1) / columns

        // Heights in dp: first what cannot shrink, then what can. Text grows with the tablet's font size setting.
        val fontScale = LocalDensity.current.fontScale
        val fixedHeight = 2 * SCREEN_PADDING_V + TITLE_GAP + SUBTITLE_LINE * fontScale + HEADER_BOTTOM + gap.value * rows +
            rows * (2 * CARD_PADDING_V + NAME_GAP + NAME_LINE * fontScale)
        val fullLogo = minOf(contentWidth, 460.dp)
        val scalableHeight = fullLogo.value * HEADER_PER_LOGO_WIDTH + rows * FULL_AVATAR
        val scale = if (rows > 2) 1f else ((maxHeight.value - fixedHeight) / scalableHeight).coerceIn(0.5f, 1f)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = SCREEN_PADDING_V.dp),
            verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DentTrackHeader(fullLogo * scale, onAdminTrigger)
            if (users.isEmpty()) {
                Text(
                    "No users yet. Hold the DentTrack title for 3 seconds to open the Admin menu, then add them under Users.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            users.chunked(columns).forEachIndexed { row, rowUsers ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rowUsers.forEachIndexed { i, user ->
                        key(user.id) {
                            UserMenuCard(
                                user, row * columns + i, (FULL_AVATAR * scale).dp, Modifier.width(cardWidth),
                                onClick = { onPick(user) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DentTrackHeader(logoWidth: Dp, onAdminTrigger: () -> Unit) {
    val titleStyle = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1.5).sp,
        brush = Brush.linearGradient(listOf(DentTrackColors.Primary, DentTrackColors.PrimaryDark))
    )
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val titleSize = remember(logoWidth, density) {
        val reference = 100.sp
        val measured = measurer.measure("DentTrack", titleStyle.copy(fontSize = reference)).size.width
        reference * (with(density) { logoWidth.toPx() } * TITLE_PER_LOGO_WIDTH / measured)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = HEADER_BOTTOM.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // res/drawable-nodpi/logo_miclinic.png: 1200 x 165, transparent. A much larger file renders with jagged edges.
        Image(
            bitmap = ImageBitmap.imageResource(R.drawable.logo_miclinic),
            contentDescription = "Mi' clinic",
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High,
            modifier = Modifier
                .width(logoWidth)
                .aspectRatio(LOGO_ASPECT)
        )
        Spacer(Modifier.height(TITLE_GAP.dp))
        Text(
            "DentTrack",
            style = titleStyle.copy(fontSize = titleSize, lineHeight = titleSize * 1.15f),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.holdToTrigger(3_000L, onAdminTrigger)
        )
        Row(
            Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(Modifier.weight(1f), thickness = 2.dp, color = DentTrackColors.MintDark)
            Text(
                "Who is using the app?",
                style = MaterialTheme.typography.titleLarge,
                color = DentTrackColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            HorizontalDivider(Modifier.weight(1f), thickness = 2.dp, color = DentTrackColors.MintDark)
        }
    }
}

/** The whole card is the button: one tap opens the menu, so it has no selected state. */
@Composable
private fun UserMenuCard(user: User, index: Int, avatarSize: Dp, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")

    // The cards rise into place one after the other when the screen opens.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        entrance.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }

    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = shape,
        color = DentTrackColors.Surface,
        border = BorderStroke(
            3.dp,
            Brush.linearGradient(listOf(DentTrackColors.Primary, DentTrackColors.MintDark))
        ),
        modifier = modifier
            .graphicsLayer {
                val scale = pressScale * (0.94f + 0.06f * entrance.value)
                scaleX = scale
                scaleY = scale
                alpha = entrance.value
                translationY = (1f - entrance.value) * 28.dp.toPx()
            }
            .shadow(10.dp, shape, spotColor = DentTrackColors.Primary, ambientColor = DentTrackColors.Primary)
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = CARD_PADDING_V.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Avatar(user.name, size = avatarSize)
                Spacer(Modifier.height(NAME_GAP.dp))
                Text(
                    user.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = DentTrackColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = DentTrackColors.Primary,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .size(32.dp)
            )
        }
    }
}
