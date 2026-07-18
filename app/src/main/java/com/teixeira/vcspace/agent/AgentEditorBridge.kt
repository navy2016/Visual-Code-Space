/*
 * This file is part of Visual Code Space.
 *
 * Visual Code Space is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Visual Code Space is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Visual Code Space.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package com.teixeira.vcspace.agent

import com.blankj.utilcode.util.ToastUtils
import com.itsvks.monaco.MonacoEditor
import com.teixeira.vcspace.activities.EditorActivity
import com.teixeira.vcspace.file.wrapFile
import com.teixeira.vcspace.ui.screens.editor.EditorViewModel
import com.teixeira.vcspace.ui.screens.editor.components.view.CodeEditorView
import com.teixeira.vcspace.ui.screens.file.FileExplorerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import java.io.File as JFile

object AgentEditorBridge {
    private var activityRef: WeakReference<EditorActivity>? = null
    private var editorViewModelRef: WeakReference<EditorViewModel>? = null
    private var fileExplorerViewModelRef: WeakReference<FileExplorerViewModel>? = null

    fun attach(
        activity: EditorActivity,
        editorViewModel: EditorViewModel,
        fileExplorerViewModel: FileExplorerViewModel
    ) {
        activityRef = WeakReference(activity)
        editorViewModelRef = WeakReference(editorViewModel)
        fileExplorerViewModelRef = WeakReference(fileExplorerViewModel)
    }

    fun detach(activity: EditorActivity) {
        if (activityRef?.get() === activity) {
            activityRef = null
            editorViewModelRef = null
            fileExplorerViewModelRef = null
        }
    }

    suspend fun getCurrentFilePath(): AgentToolResult = withContext(Dispatchers.Main.immediate) {
        val selectedFile = editorViewModel()?.uiState?.value?.selectedFile?.file
            ?: return@withContext AgentToolResult.error("No active editor file")

        AgentToolResult.text(selectedFile.absolutePath)
    }

    suspend fun getCurrentEditorText(): AgentToolResult = withContext(Dispatchers.Main.immediate) {
        val viewModel = editorViewModel()
            ?: return@withContext AgentToolResult.error("No active editor activity")

        val editor = runCatching { viewModel.getSelectedEditor() }.getOrNull()
            ?: return@withContext AgentToolResult.error("No active editor")

        val text = when (editor) {
            is CodeEditorView -> editor.editor.text.toString()
            is MonacoEditor -> editor.currentText()
            else -> return@withContext AgentToolResult.error(
                "Unsupported active editor: ${editor::class.java.simpleName}"
            )
        }

        AgentToolResult.text(text)
    }

    suspend fun replaceCurrentEditorText(text: String): AgentToolResult =
        withContext(Dispatchers.Main.immediate) {
            val viewModel = editorViewModel()
                ?: return@withContext AgentToolResult.error("No active editor activity")

            val selectedFile = viewModel.uiState.value.selectedFile?.file
            val editor = runCatching { viewModel.getSelectedEditor() }.getOrNull()
                ?: return@withContext AgentToolResult.error("No active editor")

            when (editor) {
                is CodeEditorView -> {
                    editor.editor.setText(text, null)
                    editor.setModified(true)
                    selectedFile?.let { viewModel.setModified(it, true) }
                }

                is MonacoEditor -> {
                    editor.text = text
                    selectedFile?.let { viewModel.setModified(it, true) }
                }

                else -> return@withContext AgentToolResult.error(
                    "Unsupported active editor: ${editor::class.java.simpleName}"
                )
            }

            AgentToolResult.text("Replaced current editor text")
        }

    suspend fun saveCurrentFile(): AgentToolResult = withContext(Dispatchers.Main.immediate) {
        val viewModel = editorViewModel()
            ?: return@withContext AgentToolResult.error("No active editor activity")

        viewModel.saveFile()
        AgentToolResult.text("Saved current file")
    }

    suspend fun openFile(path: String): AgentToolResult = withContext(Dispatchers.Main.immediate) {
        val activity = activity()
            ?: return@withContext AgentToolResult.error("No active editor activity")

        val file = JFile(path)
        if (!file.exists() || !file.isFile) {
            return@withContext AgentToolResult.error("File does not exist: $path")
        }

        activity.openFile(file.wrapFile())
        AgentToolResult.text("Opened file: ${file.absolutePath}")
    }

    fun getWorkspaceRootPath(): String? =
        fileExplorerViewModelRef?.get()?.openedFolder?.value?.absolutePath

    suspend fun showToast(message: String, long: Boolean) = withContext(Dispatchers.Main.immediate) {
        if (long) ToastUtils.showLong(message) else ToastUtils.showShort(message)
        AgentToolResult.text("Toast shown")
    }

    private suspend fun MonacoEditor.currentText(): String = suspendCancellableCoroutine { cont ->
        getTextAsync { value ->
            if (cont.isActive) cont.resume(value)
        }
    }

    private fun activity(): EditorActivity? = activityRef?.get()

    private fun editorViewModel(): EditorViewModel? = editorViewModelRef?.get()
}
