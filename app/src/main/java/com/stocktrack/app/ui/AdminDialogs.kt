package com.stocktrack.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Fires [onTrigger] once the finger has stayed down for [durationMs] (default 3 seconds).
 * Lifting the finger earlier does nothing. Used for the hidden Admin entrance.
 */
fun Modifier.holdToTrigger(durationMs: Long = 3_000L, onTrigger: () -> Unit): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val latest = rememberUpdatedState(onTrigger)
    pointerInput(durationMs) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var releasedInTime = false
            withTimeoutOrNull(durationMs) {
                waitForUpOrCancellation()
                releasedInTime = true
            }
            if (!releasedInTime) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                latest.value.invoke()
                waitForUpOrCancellation()
            }
        }
    }
}

/**
 * Admin password prompt. When no password exists yet it asks the person to create one,
 * otherwise it asks for the password. [signIn] reports back whether it worked.
 */
@Composable
fun AdminLoginDialog(
    passwordSet: Boolean,
    signIn: (password: String, createNew: Boolean, onResult: (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val creating = !passwordSet
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (creating) {
            if (password.isEmpty()) {
                error = "Enter a password"
                return
            }
            if (password != repeat) {
                error = "The two passwords are not the same"
                return
            }
        }
        signIn(password, creating) { ok ->
            if (ok) onDismiss() else error = if (creating) "Could not create the password" else "Wrong password"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "Create admin password" else "Admin sign-in") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (creating) {
                    Text(
                        "No admin password has been set yet. Choose one now. You can change it later in Settings.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text(if (creating) "New password" else "Password") },
                    singleLine = true,
                    isError = error != null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                if (creating) {
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it; error = null },
                        label = { Text("Repeat password") },
                        singleLine = true,
                        isError = error != null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let { Text(it, color = Red, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            Button(onClick = { submit() }) { Text(if (creating) "Create and sign in" else "Sign in") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
