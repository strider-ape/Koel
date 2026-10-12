package com.koel.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.koel.app.data.Recording
import com.koel.app.data.Recordings
import com.koel.app.service.CallRecorderService
import com.koel.app.service.ShizukuManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()
        setContent { MaterialTheme { Home() } }
    }

    override fun onResume() {
        super.onResume()
        ShizukuManager.refresh()
    }

    private fun requestRuntimePermissions() {
        val needed = buildList {
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    @Composable
    private fun Home() {
        val state by ShizukuManager.state.collectAsStateWithLifecycle()
        val message by ShizukuManager.message.collectAsStateWithLifecycle()
        var watching by remember { mutableStateOf(false) }
        var recordings by remember { mutableStateOf(Recordings.list(this)) }

        Scaffold { pad ->
            Column(
                Modifier.fillMaxSize().padding(pad).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Koel", style = MaterialTheme.typography.headlineMedium)

                StatusCard(state.name, message)

                when (state) {
                    ShizukuManager.State.NEEDS_PERMISSION ->
                        Button(onClick = { ShizukuManager.requestPermission() }) {
                            Text("Grant Shizuku permission")
                        }
                    ShizukuManager.State.SHIZUKU_MISSING ->
                        Text("Open the Shizuku app and start it, then return here.")
                    else -> {}
                }

                val ready = state == ShizukuManager.State.READY
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            "Auto-record calls",
                            trailing = {
                                Switch(
                                    checked = watching,
                                    enabled = ready,
                                    onCheckedChange = {
                                        watching = it
                                        if (it) CallRecorderService.start(this@MainActivity)
                                        else CallRecorderService.stop(this@MainActivity)
                                    },
                                )
                            },
                        )
                        if (!ready) Text(
                            "Needs Shizuku ready first.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Row("Recordings", trailing = {
                    Button(onClick = { recordings = Recordings.list(this@MainActivity) }) {
                        Text("Refresh")
                    }
                })

                if (recordings.isEmpty()) {
                    Text("No recordings yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recordings) { RecordingRow(it) }
                    }
                }
            }
        }
    }

    @Composable
    private fun StatusCard(state: String, message: String?) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Shizuku: $state", fontWeight = FontWeight.SemiBold)
                if (message != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    @Composable
    private fun RecordingRow(r: Recording) {
        val fmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    (if (r.incoming) "↓ " else "↑ ") + r.displayName,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "${fmt.format(Date(r.startedAt))}  ·  ${r.sizeBytes / 1024} KB",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    @Composable
    private fun Row(label: String, trailing: @Composable () -> Unit) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            trailing()
        }
    }
}
