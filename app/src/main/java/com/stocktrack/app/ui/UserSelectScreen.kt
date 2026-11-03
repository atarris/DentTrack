package com.stocktrack.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stocktrack.app.R
import com.stocktrack.app.StockViewModel
import com.stocktrack.app.data.User
import com.stocktrack.app.data.isAdminAccount

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

/** Three columns on a landscape tablet, fewer on narrow screens. Everything scrolls when it does not fit. */
@Composable
private fun UserMenuGrid(users: List<User>, onAdminTrigger: () -> Unit, onPick: (User) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val columns = when {
            maxWidth >= 840.dp -> 3
            maxWidth >= 520.dp -> 2
            else -> 1
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            modifier = Modifier
                .widthIn(max = 1120.dp)
                .fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { DentTrackHeader(onAdminTrigger) }
            if (users.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "No users yet. Hold the DentTrack title for 3 seconds to open the Admin menu, then add them under Users.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(users, key = { it.id }) { user ->
                    UserMenuCard(user, onClick = { onPick(user) })
                }
            }
        }
    }
}

@Composable
private fun DentTrackHeader(onAdminTrigger: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // res/drawable-nodpi/logo_miclinic.png: high resolution (1600 x 726), transparent.
        Image(
            bitmap = ImageBitmap.imageResource(R.drawable.logo_miclinic),
            contentDescription = "Mi' clinic, family dental home since 2020",
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High,
            modifier = Modifier
                .height(112.dp)
                .aspectRatio(1600f / 726f)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "DentTrack",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = DentTrackColors.PrimaryDark,
            modifier = Modifier
                .holdToTrigger(3_000L, onAdminTrigger)
                .padding(horizontal = 24.dp, vertical = 2.dp)
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(Modifier.weight(1f), color = DentTrackColors.MintDark)
            Text(
                "Who is using the app?",
                style = MaterialTheme.typography.titleMedium,
                color = DentTrackColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            HorizontalDivider(Modifier.weight(1f), color = DentTrackColors.MintDark)
        }
    }
}

/** The whole card is the button: one tap opens the menu, so it has no selected state. */
@Composable
private fun UserMenuCard(user: User, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = DentTrackColors.Surface,
        border = BorderStroke(1.dp, DentTrackColors.Border),
        shadowElevation = 2.dp
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Avatar(user.name, size = 72.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    user.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    userRole(user),
                    style = MaterialTheme.typography.bodyMedium,
                    color = DentTrackColors.TextSecondary
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = DentTrackColors.TextSecondary,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .size(24.dp)
            )
        }
    }
}
