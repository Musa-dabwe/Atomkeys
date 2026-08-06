package com.musa.atomkeys

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.musa.atomkeys.ime.CrashGuard
import com.musa.atomkeys.ui.theme.AtomKeysTheme

/**
 * Phase 1 host for the app side.
 *
 * Currently just enough to enable the keyboard and type against it, plus the reset/diagnostics path
 * that roadmap 1.4 requires be reachable from the app rather than only from the keyboard. The real
 * setup wizard is Phase 5.3 and the settings screens are Phase 5.2.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AtomKeysTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    SetupScreen(Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var crashLog by remember { mutableStateOf(readCrashLog(context)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("AtomKeys", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Phase 1: the keyboard registers, types, and contains its own crashes. " +
                "Layout, themes, gestures and correction come in later phases.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("1 · Enable AtomKeys in system settings") }

        Button(
            onClick = {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                    as InputMethodManager
                imm.showInputMethodPicker()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("2 · Switch to AtomKeys") }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("3 · Type here to test") },
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
        Text(
            text = crashLog?.let { "Crash log present (${it.length} chars)" }
                ?: "No crashes recorded.",
            style = MaterialTheme.typography.bodySmall,
        )
        crashLog?.let { log ->
            Text(
                text = log.take(1200),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        }
        OutlinedButton(
            onClick = {
                CrashGuard.clearLog(context)
                crashLog = null
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Clear crash log") }
    }
}

private fun readCrashLog(context: Context): String? =
    CrashGuard.logFile(context)?.runCatching { readText() }?.getOrNull()
