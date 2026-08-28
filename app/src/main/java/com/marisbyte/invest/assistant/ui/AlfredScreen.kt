package com.marisbyte.invest.assistant.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.marisbyte.invest.assistant.AlfredPhase
import com.marisbyte.invest.assistant.AlfredTurn

/**
 * Alfreds Gespraechsbildschirm. Er ist bewusst schlicht: waehrend man mit ihm redet,
 * schaut man nicht aufs Handy. Er zeigt, ob Alfred spricht oder zuhoert, und den
 * Verlauf zum Nachlesen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlfredScreen(
    viewModel: AlfredViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.turns.size) {
        if (state.turns.isNotEmpty()) listState.animateScrollToItem(state.turns.lastIndex)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(settings.wakeWord) },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PhaseIndicator(
                phase = state.phase,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp)
            )

            state.hint?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.turns) { turn -> TurnBubble(turn) }
            }

            if (state.partial.isNotBlank()) {
                Text(
                    text = state.partial,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            Controls(
                running = state.phase != AlfredPhase.IDLE,
                onSpeak = viewModel::startListening,
                onBriefing = viewModel::startBriefing,
                onStop = viewModel::stop,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun PhaseIndicator(phase: AlfredPhase, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "puls")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (phase == AlfredPhase.LISTENING) 1.18f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "groesse"
    )
    val color = when (phase) {
        AlfredPhase.LISTENING -> MaterialTheme.colorScheme.primary
        AlfredPhase.SPEAKING -> MaterialTheme.colorScheme.tertiary
        AlfredPhase.THINKING, AlfredPhase.PREPARING -> MaterialTheme.colorScheme.secondary
        AlfredPhase.IDLE -> MaterialTheme.colorScheme.surfaceVariant
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(scale)
                .background(color.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = if (phase == AlfredPhase.IDLE) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    color
                },
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = phaseLabel(phase),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun phaseLabel(phase: AlfredPhase): String = when (phase) {
    AlfredPhase.IDLE -> "Bereit"
    AlfredPhase.PREPARING -> "Einen Moment …"
    AlfredPhase.SPEAKING -> "Spricht"
    AlfredPhase.LISTENING -> "Hört zu"
    AlfredPhase.THINKING -> "Denkt nach"
}

@Composable
private fun TurnBubble(turn: AlfredTurn) {
    val alignment = if (turn.fromAlfred) Alignment.CenterStart else Alignment.CenterEnd
    val container = if (turn.fromAlfred) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(
            color = container,
            contentColor = if (turn.fromAlfred) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = turn.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun Controls(
    running: Boolean,
    onSpeak: () -> Unit,
    onBriefing: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (running) {
            Button(
                onClick = onStop,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White
                )
            ) {
                Text("Stopp")
            }
        } else {
            Button(onClick = onSpeak, modifier = Modifier.weight(1f)) {
                Text("Sprechen")
            }
            OutlinedButton(onClick = onBriefing, modifier = Modifier.weight(1f)) {
                Text("Bericht")
            }
        }
    }
}
