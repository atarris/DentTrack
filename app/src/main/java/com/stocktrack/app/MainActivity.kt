package com.stocktrack.app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stocktrack.app.data.Language
import com.stocktrack.app.data.activeScheme
import com.stocktrack.app.ui.AdminLoginDialog
import com.stocktrack.app.ui.DentTrackTheme
import com.stocktrack.app.ui.MainScreen
import com.stocktrack.app.ui.UserSelectScreen
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var shownLanguage = ""
    private var firstScreenReady = false

    /** The screens use the language chosen in Settings, not the tablet's own language. */
    override fun attachBaseContext(newBase: Context) {
        val localized = Language.context(newBase)
        shownLanguage = localized.resources.configuration.locales[0].language
        Locale.setDefault(localized.resources.configuration.locales[0]) // for dates and numbers
        super.attachBaseContext(localized)
    }

    /**
     * Keeps Android's start window with the title on screen until the first real screen can be drawn,
     * so the app's window never appears before it has anything on it.
     */
    private fun holdFirstFrame() {
        val content = findViewById<View>(android.R.id.content)
        val started = SystemClock.uptimeMillis()
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!firstScreenReady && SystemClock.uptimeMillis() - started < MAX_FIRST_FRAME_HOLD_MS) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.WHITE)) // the theme's has the title, for the start only
        holdFirstFrame()
        setContent {
            val vm: StockViewModel = viewModel()
            val users by vm.pickableUsers.collectAsState()
            if (users != null) SideEffect { firstScreenReady = true }
            val config by vm.config.collectAsState()
            // Restarts the screen with the new language, whether Settings or the web page changed it.
            LaunchedEffect(config.language) {
                if (config.language != shownLanguage) recreate()
            }
            // "Random every day" changes at midnight, also while the app stays open.
            val now by produceState(System.currentTimeMillis()) {
                while (true) {
                    delay(60_000)
                    value = System.currentTimeMillis()
                }
            }
            DentTrackTheme(config.activeScheme(now)) {
                AppRoot(vm)
            }
        }
    }
}

private const val MAX_FIRST_FRAME_HOLD_MS = 3_000L

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
