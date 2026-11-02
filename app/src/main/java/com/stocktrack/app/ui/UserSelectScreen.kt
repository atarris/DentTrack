@file:OptIn(ExperimentalMaterial3Api::class)

package com.stocktrack.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stocktrack.app.R
import com.stocktrack.app.StockViewModel
import com.stocktrack.app.data.User
import com.stocktrack.app.data.isAdminAccount

/**
 * Start screen: nobody signs in, the person just taps their name.
 * Holding the "DentTrack" title for 3 seconds opens the hidden Admin sign-in ([onAdminTrigger]).
 */
@Composable
fun UserSelectScreen(vm: StockViewModel, users: List<User>, onAdminTrigger: () -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    var manage by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<User?>(null) }

    // Marble background: soft green from the top-right corner, a shine sweeping through from time to time.
    MarbleBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .widthIn(max = 780.dp)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // res/drawable-nodpi/logo_miclinic.png: high resolution (1122 x 429), colours matched to the app.
            Image(
                bitmap = ImageBitmap.imageResource(R.drawable.logo_miclinic),
                contentDescription = "miclinic.lt",
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .height(96.dp)
                    .aspectRatio(1122f / 429f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "DentTrack",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .holdToTrigger(3_000L, onAdminTrigger)
                    .padding(horizontal = 24.dp, vertical = 6.dp)
            )
            Text(
                if (manage) "Tap the bin to remove a user" else "Who is using the app?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))

            if (users.isEmpty()) {
                Text(
                    "No users yet. Tap “Add user” to create the first one.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(170.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(users, key = { it.id }) { user ->
                        UserCard(
                            user = user,
                            manage = manage,
                            onPick = { if (user.isAdminAccount()) onAdminTrigger() else vm.selectUser(user.id) },
                            onDelete = { toDelete = user }
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Add user")
                }
                OutlinedButton(onClick = { manage = !manage }) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(if (manage) "Done" else "Manage users")
                }
            }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add user") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank(),
                    onClick = { vm.addUser(name); showAdd = false }
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }

    toDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Remove ${user.name}?") },
            text = { Text("Their name is removed from the list. Entries they already made stay in the activity log.") },
            confirmButton = {
                Button(
                    onClick = { vm.deleteUser(user); toDelete = null },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Red)
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun UserCard(user: User, manage: Boolean, onPick: () -> Unit, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Box {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !manage, onClick = onPick)
                    .padding(horizontal = 16.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Avatar(user.name, size = 64.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    user.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (manage && !user.isAdminAccount()) {
                IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove ${user.name}", tint = Red)
                }
            }
        }
    }
}
