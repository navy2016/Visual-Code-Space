/*
 * This file is part of Visual Code Space.
 *
 * Visual Code Space is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 */

package com.teixeira.vcspace.ui.gestures

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Opens a drawer with a deliberate left-to-right drag that can start inside the
 * left part of the content area, not only on the system edge.
 */
fun Modifier.openDrawerOnSwipe(
    drawerState: DrawerState,
    enabled: Boolean = true,
    startZoneFraction: Float = 0.62f
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val ignoredSystemEdgePx = with(density) { 18.dp.toPx() }
    val openThresholdPx = with(density) { 72.dp.toPx() }

    pointerInput(drawerState, enabled, startZoneFraction) {
        var tracking = false
        var draggedX = 0f

        detectHorizontalDragGestures(
            onDragStart = { offset ->
                draggedX = 0f
                tracking = enabled &&
                    drawerState.currentValue == DrawerValue.Closed &&
                    offset.x > ignoredSystemEdgePx &&
                    offset.x < size.width * startZoneFraction
            },
            onHorizontalDrag = { _, dragAmount ->
                if (!tracking) return@detectHorizontalDragGestures
                draggedX += dragAmount
                if (draggedX > openThresholdPx) {
                    tracking = false
                    scope.launch { drawerState.open() }
                } else if (draggedX < -openThresholdPx / 2f) {
                    tracking = false
                }
            },
            onDragEnd = {
                tracking = false
                draggedX = 0f
            },
            onDragCancel = {
                tracking = false
                draggedX = 0f
            }
        )
    }
}
