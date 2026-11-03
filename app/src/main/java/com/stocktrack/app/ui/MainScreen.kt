@file:OptIn(ExperimentalMaterial3Api::class)

package com.stocktrack.app.ui

import androidx.compose.ui.res.stringResource
import com.stocktrack.app.R
import android.content.Intent
import androidx.annotation.StringRes
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stocktrack.app.StockViewModel
import com.stocktrack.app.data.User
import com.stocktrack.app.data.XlsxWriter
import com.stocktrack.app.data.isLow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Tab(@StringRes val label: Int,val icon: ImageVector, val adminOnly: Boolean = false) {
    Dashboard(R.string.tab_dashboard,Icons.Default.Dashboard),
    Items(R.string.tab_items,Icons.Default.Inventory2),
    Activity(R.string.tab_activity,Icons.Default.History),
    Reports(R.string.tab_reports,Icons.Default.Assessment),
    Users(R.string.tab_users,Icons.Default.Group, adminOnly = true),
    Settings(R.string.tab_settings,Icons.Default.Settings, adminOnly = true),
    Backup(R.string.tab_backup, Icons.Default.Backup, adminOnly = true),
    Info(R.string.tab_info, Icons.Default.Info)
}

@Composable
fun MainScreen(vm: StockViewModel, user: User, isAdmin: Boolean, onAdminTrigger: () -> Unit) {
    val items by vm.items.collectAsState()
    val log by vm.log.collectAsState()
    val config by vm.config.collectAsState()
    val threshold = config.lowStockThreshold

    var tab by rememberSaveable { mutableStateOf(Tab.Dashboard) }
    var selectedItemId by rememberSaveable { mutableStateOf<Long?>(null) }
    var startCheckOut by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lowCount = items.count { it.isLow(threshold) }

    // Users, Settings and Backup exist for the Admin only.
    val visibleTabs = Tab.entries.filter { !it.adminOnly || isAdmin }
    val activeTab = if (tab in visibleTabs) tab else Tab.Dashboard

    // ---- inactivity timeout: back to the user list when nobody touched the screen for X minutes
    var lastActive by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val ping: () -> Unit = remember { { lastActive = SystemClock.elapsedRealtime() } }
    LaunchedEffect(config.inactivityMinutes) {
        lastActive = SystemClock.elapsedRealtime()
        val limitMs = config.inactivityMinutes * 60_000L
        if (limitMs > 0L) {
            while (true) {
                delay(2_000)
                if (SystemClock.elapsedRealtime() - lastActive >= limitMs) {
                    vm.switchUser()
                    break
                }
            }
        }
    }

    // Show one-off messages (stock moved, low stock alert, export done ...).
    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            launch {
                val result = snackbar.showSnackbar(
                    message = msg.text,
                    actionLabel = if (msg.shareUri != null) context.getString(R.string.share) else null,
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed && msg.shareUri != null) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = XlsxWriter.MIME
                        putExtra(Intent.EXTRA_STREAM, msg.shareUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(send, context.getString(R.string.share_report))
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                }
            }
        }
    }

    CompositionLocalProvider(LocalActivityPing provides ping) {
        Box(
            Modifier
                .fillMaxSize()
                // Watches every touch without consuming it, to restart the inactivity timer.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            ping()
                        }
                    }
                }
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                containerColor = MaterialTheme.colorScheme.background
            ) { padding ->
                Row(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {
                    NavigationRail(
                        modifier = Modifier.width(104.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        header = {
                            // The app icon leads home: back to the user selection screen.
                            IconButton(onClick = { vm.switchUser() }) {
                                Icon(
                                    Icons.Default.Inventory2,
                                    contentDescription = stringResource(R.string.back_to_users),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    ) {
                        // Scrolls when all the Admin tabs do not fit on a short screen.
                        Column(
                            Modifier.verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            visibleTabs.forEach { t ->
                                NavigationRailItem(
                                    selected = activeTab == t,
                                    onClick = { tab = t },
                                    icon = {
                                        if (t == Tab.Items && lowCount > 0) {
                                            BadgedBox(badge = { Badge { Text("$lowCount") } }) {
                                                Icon(t.icon, contentDescription = stringResource(t.label))
                                            }
                                        } else {
                                            Icon(t.icon, contentDescription = stringResource(t.label))
                                        }
                                    },
                                    label = { Text(stringResource(t.label), maxLines = 1, softWrap = false) },
                                    alwaysShowLabel = true
                                )
                            }
                        }
                    }

                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(24.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                // Press and hold for 3 seconds = hidden Admin sign-in.
                                Text(
                                    "DentTrack",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .holdToTrigger(3_000L, onAdminTrigger)
                                        .padding(end = 24.dp, top = 4.dp, bottom = 4.dp)
                                )
                                Text(
                                    stringResource(activeTab.label),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (isAdmin) {
                                Pill(stringResource(R.string.admin_mode), Accent)
                                Spacer(Modifier.size(12.dp))
                            }
                            FilledTonalButton(onClick = { vm.switchUser() }) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.switch_user, user.name), maxLines = 1)
                            }
                        }
                        Spacer(Modifier.height(16.dp))

                        Column(Modifier.weight(1f).fillMaxWidth()) {
                            when (activeTab) {
                                Tab.Dashboard -> DashboardScreen(
                                    stock = items,
                                    log = log,
                                    threshold = threshold,
                                    onOpenItem = { id ->
                                        selectedItemId = id
                                        tab = Tab.Items
                                    },
                                    onCheckOutItem = { id ->
                                        selectedItemId = id
                                        startCheckOut = true
                                        tab = Tab.Items
                                    }
                                )
                                Tab.Items -> ItemsScreen(
                                    vm = vm,
                                    stock = items,
                                    log = log,
                                    threshold = threshold,
                                    isAdmin = isAdmin,
                                    selectedId = selectedItemId,
                                    onSelect = { selectedItemId = it },
                                    startCheckOut = startCheckOut,
                                    onCheckOutStarted = { startCheckOut = false }
                                )
                                Tab.Activity -> ActivityScreen(log, isAdmin, vm::clearLog)
                                Tab.Reports -> ReportsScreen(
                                    vm = vm,
                                    stock = items,
                                    threshold = threshold,
                                    isAdmin = isAdmin,
                                    config = config,
                                    onOpenItem = { id ->
                                        selectedItemId = id
                                        tab = Tab.Items
                                    }
                                )
                                Tab.Users -> UsersScreen(vm)
                                Tab.Settings -> SettingsScreen(vm, config)
                                Tab.Backup -> BackupScreen(vm, config)
                                Tab.Info -> InfoScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}
