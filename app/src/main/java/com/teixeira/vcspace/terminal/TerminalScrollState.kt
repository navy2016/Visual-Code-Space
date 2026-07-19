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
    private val topRowField by lazy {
        TerminalView::class.java.getDeclaredField("mTopRow").apply { isAccessible = true }
    }

    fun preserveUserScrollOnUpdate(terminal: TerminalView) {
        val emulator = terminal.mEmulator ?: run {
            terminal.onScreenUpdated()
            return
        }
        val previousTopRow = runCatching { topRowField.getInt(terminal) }.getOrDefault(0)
        val scrollCounter = runCatching { emulator.scrollCounter }.getOrDefault(0)
        val preserveHistoryPosition = previousTopRow < 0 && !terminal.isSelectingText
        val rowsInHistory = runCatching { emulator.screen.activeTranscriptRows }.getOrDefault(0)

        terminal.onScreenUpdated()

        if (preserveHistoryPosition) {
            val restoredTopRow = (previousTopRow - scrollCounter).coerceIn(-rowsInHistory, 0)
            runCatching {
                topRowField.setInt(terminal, restoredTopRow)
                terminal.invalidate()
            }
        }
    }
}
