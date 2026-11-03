package com.stocktrack.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stocktrack.app.ui.AdminLoginDialog
import com.stocktrack.app.ui.DentTrackTheme
import com.stocktrack.app.ui.MainScreen
import com.stocktrack.app.ui.UserSelectScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DentTrackTheme {
                val vm: StockViewModel = viewModel()
                AppRoot(vm)
            }
        }
    }
}

@Composable
private fun AppRoot(vm: StockViewModel) {
    val users by vm.pickableUsers.collectAsState()
    val current by vm.currentUser.collectAsState()
    val isAdmin by vm.isAdmin.collectAsState()
    val config by vm.config.collectAsState()
    var showAdminPrompt by remember { mutableStateOf(false) }

    // Holding the "DentTrack" title for 3 seconds (start screen or main screen) lands here.
    val openAdminPrompt: () -> Unit = {
        when {
            isAdmin -> Unit
            config.adminPasswordRemoved -> vm.adminEnterWithoutPassword()
            else -> showAdminPrompt = true
        }
    }

    val userList = users
    val user = current

    when {
        userList == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        user == null -> UserSelectScreen(vm, userList, openAdminPrompt)
        else -> MainScreen(vm, user, isAdmin, openAdminPrompt)
    }

    if (showAdminPrompt) {
        AdminLoginDialog(
            passwordSet = config.adminPasswordSet,
            signIn = vm::adminSignIn,
            onDismiss = { showAdminPrompt = false }
        )
    }
}
