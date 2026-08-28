package com.marisbyte.invest.ui.screens.assistant

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marisbyte.invest.assistant.AlfredActivity
import com.marisbyte.invest.assistant.model.AssistantTask
import com.marisbyte.invest.assistant.text.SpokenTime
import com.marisbyte.invest.assistant.ui.AlfredViewModel
import com.marisbyte.invest.ui.AppViewModels
import com.marisbyte.invest.ui.components.EmptyState
import com.marisbyte.invest.ui.components.SectionHeader

/**
 * Alfreds Karteikarte in der App: Weckwort ein- und ausschalten, alles einstellen,
 * was er ueber Maris wissen muss, und die Aufgaben nachlesen, die er entgegengenommen hat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    viewModel: AlfredViewModel = viewModel(factory = AppViewModels.Factory)
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] == true) {
            viewModel.setWakeWordEnabled(true)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Alfred") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = "Sag \"${settings.wakeWord}\" – er begrüßt dich, liest Wetter, " +
                            "Depot und Immobilienpreise vor und nimmt danach Aufgaben entgegen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                context.startActivity(
                                    AlfredActivity.intent(context, startSession = true)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Bericht anhören")
                        }
                        OutlinedButton(
                            onClick = { context.startActivity(AlfredActivity.intent(context)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Gespräch öffnen")
                        }
                    }
                }
            }

            item { SectionHeader("Weckwort", subtitle = "Dauerhaft zuhören") }
            item {
                SwitchRow(
                    title = "Auf \"${settings.wakeWord}\" hören",
                    description = "Läuft als sichtbarer Dienst. Kostet spürbar Akku – " +
                        "schalte es aus, wenn du es nicht brauchst.",
                    checked = settings.wakeWordEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            microphonePermission.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                        } else {
                            viewModel.setWakeWordEnabled(false)
                        }
                    }
                )
            }

            item { SectionHeader("Wer und wo") }
            item {
                TextSetting(
                    label = "Dein Name",
                    value = settings.userName,
                    onDone = viewModel::setUserName
                )
            }
            item {
                TextSetting(
                    label = "Weckwort",
                    value = settings.wakeWord,
                    onDone = viewModel::setWakeWord
                )
            }
            item {
                TextSetting(
                    label = "Ort für das Wetter",
                    placeholder = "leer = Standort des Geräts",
                    value = settings.weatherCity,
                    onDone = viewModel::setWeatherCity
                )
            }

            item { SectionHeader("Im Bericht", subtitle = "Was Alfred morgens vorliest") }
            item {
                SwitchRow(
                    title = "Wetter",
                    checked = settings.briefingWeather,
                    onCheckedChange = viewModel::setBriefingWeather
                )
            }
            item {
                SwitchRow(
                    title = "Depot und Märkte",
                    checked = settings.briefingMarket,
                    onCheckedChange = viewModel::setBriefingMarket
                )
            }
            item {
                SwitchRow(
                    title = "Immobilienpreise",
                    checked = settings.briefingRealEstate,
                    onCheckedChange = viewModel::setBriefingRealEstate
                )
            }

            item { SectionHeader("Stimme") }
            item {
                SpeechRateSetting(
                    rate = settings.speechRate,
                    onChange = viewModel::setSpeechRate
                )
            }

            item {
                SectionHeader(
                    "Immobilienindex",
                    subtitle = "Reihenschlüssel im Datenportal der EZB"
                )
            }
            item {
                TextSetting(
                    label = "Reihe (Datensatz RESR)",
                    value = settings.realEstateSeriesKey,
                    supportingText = "Standard ist die deutsche Wohnimmobilienreihe. " +
                        "Zurücksetzen mit leerem Feld.",
                    onDone = viewModel::setRealEstateSeriesKey
                )
            }

            item {
                SectionHeader(
                    "Aufgaben",
                    subtitle = "Was Alfred sich gemerkt hat",
                    action = {
                        if (tasks.any { it.done }) {
                            TextButton(onClick = viewModel::deleteCompletedTasks) {
                                Text("Erledigte löschen")
                            }
                        }
                    }
                )
            }
            if (tasks.isEmpty()) {
                item {
                    EmptyState(
                        title = "Noch nichts notiert",
                        description = "Sag \"${settings.wakeWord}, merk dir …\" oder " +
                            "\"erinnere mich morgen um acht an den Zahnarzt\"."
                    )
                }
            } else {
                items(tasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        onToggle = { viewModel.setTaskDone(task, !task.done) },
                        onDelete = { viewModel.deleteTask(task) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Textfeld, das die Aenderung erst beim Verlassen des Feldes speichert. */
@Composable
private fun TextSetting(
    label: String,
    value: String,
    onDone: (String) -> Unit,
    placeholder: String? = null,
    supportingText: String? = null
) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
    LaunchedEffect(text) {
        if (text != value) {
            // Kurz warten, damit nicht jeder Tastendruck einen Schreibvorgang ausloest.
            kotlinx.coroutines.delay(600)
            onDone(text)
        }
    }
}

@Composable
private fun SpeechRateSetting(rate: Float, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = "Sprechtempo: ${"%.1f".format(rate)}×",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = rate,
            onValueChange = onChange,
            valueRange = 0.7f..1.5f,
            steps = 7
        )
    }
}

@Composable
private fun TaskRow(
    task: AssistantTask,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = task.done, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.text,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null
                )
                task.dueAt?.let { due ->
                    Text(
                        text = SpokenTime.dateTime(due),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Löschen")
            }
        }
    }
}
