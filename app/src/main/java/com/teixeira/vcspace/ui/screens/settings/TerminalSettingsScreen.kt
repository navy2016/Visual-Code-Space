/*
 * This file is part of Visual Code Space.
 *
 * Visual Code Space is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 */

package com.teixeira.vcspace.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import com.teixeira.vcspace.core.settings.Settings.Terminal.FONT_SIZE
import com.teixeira.vcspace.core.settings.Settings.Terminal.OUTPUT_WIDTH_PERCENT
import com.teixeira.vcspace.core.settings.Settings.Terminal.rememberFontSize
import com.teixeira.vcspace.core.settings.Settings.Terminal.rememberOutputWidthPercent
import me.zhanghai.compose.preference.preferenceCategory
import me.zhanghai.compose.preference.sliderPreference
import me.zhanghai.compose.preference.textFieldPreference

@Composable
fun TerminalSettingsScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fontSize = rememberFontSize()
    val outputWidth = rememberOutputWidthPercent()
    val backgroundColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)

    BackHandler(onBack = onNavigateUp)

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        preferenceCategory(
            key = "terminal_category",
            title = { Text("Terminal") }
        )

        sliderPreference(
            key = FONT_SIZE.name,
            title = { Text("Output font size") },
            defaultValue = fontSize.value,
            rememberState = { fontSize },
            valueRange = 23f..88f,
            valueSteps = 64,
            valueText = { Text("${it.fastRoundToInt()} sp") },
            icon = { Icon(Icons.Default.TextFields, contentDescription = null) },
            modifier = Modifier
                .clip(PreferenceShape.Top)
                .background(backgroundColor)
        )

        textFieldPreference(
            key = OUTPUT_WIDTH_PERCENT.name,
            title = { Text("Output width") },
            summary = { Text("${it.coerceIn(50, 200)}%") },
            rememberState = { outputWidth },
            defaultValue = 100,
            textToValue = { it.toIntOrNull()?.coerceIn(50, 200) },
            icon = { Icon(Icons.Default.Code, contentDescription = null) },
            modifier = Modifier
                .clip(PreferenceShape.Bottom)
                .background(backgroundColor)
        )
    }
}
