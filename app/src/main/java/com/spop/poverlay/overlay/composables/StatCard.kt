package com.spop.poverlay.overlay

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp


@Composable
fun StatCard(
    name: String,
    value: String,
    unit: String,
    modifier: Modifier,
    compact: Boolean = false
) {
    val nameSize = if (compact) 11.sp else 14.sp
    val valueSize = when {
        compact -> 26.sp
        value.length >= 5 -> 34.sp
        else -> 42.sp
    }
    val unitSize = if (compact) 10.sp else 12.sp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = name,
            color = Color.White,
            fontSize = nameSize,
            fontWeight = FontWeight.Normal
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = valueSize,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = unit,
            fontSize = unitSize,
            color = Color.White,
            fontWeight = FontWeight.Light
        )
    }
}

