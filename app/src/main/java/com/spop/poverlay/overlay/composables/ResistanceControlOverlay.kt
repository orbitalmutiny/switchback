package com.spop.poverlay.overlay.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ResistanceControlOverlay(
    resistance: String,
    onStep: (Int) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = Color(20, 20, 20).copy(alpha = 0.94f),
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ResistanceStepZone(
                text = "-",
                stepDirection = -1,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onStep = onStep
            )
            ResistanceStepZone(
                text = "+",
                stepDirection = 1,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onStep = onStep
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Resistance",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal
            )
            Text(
                text = resistance,
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .requiredSize(34.dp)
                .background(
                    color = Color(70, 70, 70),
                    shape = RoundedCornerShape(17.dp)
                )
                .pointerInput(onClose) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onClose()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "x",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ResistanceStepZone(
    text: String,
    stepDirection: Int,
    modifier: Modifier,
    onStep: (Int) -> Unit
) {
    Box(
        modifier = modifier
            .padding(6.dp)
            .background(
                color = Color(45, 45, 45),
                shape = RoundedCornerShape(8.dp)
            )
            .resistanceStepInput(stepDirection = stepDirection, onStep = onStep),
        contentAlignment = if (stepDirection < 0) {
            Alignment.CenterStart
        } else {
            Alignment.CenterEnd
        }
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 54.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 22.dp)
        )
    }
}

private fun Modifier.resistanceStepInput(
    stepDirection: Int,
    onStep: (Int) -> Unit
) = pointerInput(stepDirection, onStep) {
    coroutineScope {
        var tapCount = 0
        var tapJob: Job? = null

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)

            val holdJob = launch {
                var elapsedMs = 750L
                delay(elapsedMs)
                while (true) {
                    onStep(stepDirection)
                    val nextDelayMs = when {
                        elapsedMs < 2000L -> 1000L
                        else -> 500L
                    }
                    delay(nextDelayMs)
                    elapsedMs += nextDelayMs
                }
            }

            var upTimeMillis: Long? = null
            while (upTimeMillis == null) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    break
                }
                if (!change.pressed) {
                    upTimeMillis = change.uptimeMillis
                }
            }
            holdJob.cancel()

            val pressDuration = upTimeMillis?.minus(down.uptimeMillis)
            if (pressDuration != null && pressDuration < 750L) {
                tapJob?.cancel()
                tapCount += 1
                tapJob = launch {
                    delay(325L)
                    val step = when (tapCount) {
                        1 -> 1
                        2 -> 5
                        else -> 10
                    }
                    onStep(stepDirection * step)
                    tapCount = 0
                }
            } else {
                tapJob?.cancel()
                tapCount = 0
            }
        }
    }
}
