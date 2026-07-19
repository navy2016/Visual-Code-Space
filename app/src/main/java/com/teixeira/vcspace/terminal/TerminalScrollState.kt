/*
 * This file is part of Visual Code Space.
 *
 * Visual Code Space is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 */

package com.teixeira.vcspace.terminal

import com.termux.view.TerminalView

object TerminalScrollState {
    private val topRowFieldRef by lazy {
        runCatching {
            TerminalView::class.java.getDeclaredField("mTopRow").apply { isAccessible = true }
        }.getOrNull()
    }

    fun preserveUserScrollOnUpdate(terminal: TerminalView) {
        val emulator = terminal.mEmulator ?: run {
            terminal.onScreenUpdated()
            return
        }
        val topRowField = topRowFieldRef ?: run {
            terminal.onScreenUpdated()
            return
        }
        val previousTopRow = runCatching { topRowField.getInt(terminal) }.getOrDefault(0)
        val isReadingHistory = previousTopRow < 0 && !terminal.isSelectingText

        if (!isReadingHistory) {
            terminal.onScreenUpdated()
            return
        }

        val scrollCounter = runCatching { emulator.getScrollCounter() }.getOrDefault(0)
        val rowsInHistory = runCatching { emulator.getScreen().getActiveTranscriptRows() }.getOrDefault(0)
        val restoredTopRow = (previousTopRow - scrollCounter).coerceIn(-rowsInHistory, 0)

        runCatching {
            if (restoredTopRow != previousTopRow) {
                topRowField.setInt(terminal, restoredTopRow)
            }
            emulator.clearScrollCounter()
            terminal.postInvalidateOnAnimation()
        }.onFailure {
            terminal.onScreenUpdated()
        }
    }
}
