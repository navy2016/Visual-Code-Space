/*
 * This file is part of Visual Code Space.
 *
 * Visual Code Space is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 */

package com.teixeira.vcspace.file

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.blankj.utilcode.util.PathUtils
import com.teixeira.vcspace.preferences.defaultPrefs
import com.teixeira.vcspace.terminal.appDataDir
import java.io.File as JFile

/** Central allow-list for non-editor filesystem access exposed to agents and terminal helpers. */
object WorkspaceAccessManager {
    private const val KEY_AUTHORIZED_DIRS = "authorized_external_dirs"
    private const val PATH_SEPARATOR = "\n"

    fun appInternalRoot(): JFile = appDataDir

    fun terminalWorkingRoot(context: Context): JFile =
        JFile(context.getExternalFilesDir(null) ?: JFile(PathUtils.getInternalAppFilesPath()), "terminal-workspace")
            .apply { mkdirs() }

    fun rememberAuthorizedDir(path: String?) {
        if (path.isNullOrBlank() || path.startsWith("content://")) return
        val canonical = path.canonicalOrNull() ?: return
        val current = authorizedExternalDirs().toMutableSet()
        current.add(canonical)
        defaultPrefs.edit(commit = true) {
            putString(KEY_AUTHORIZED_DIRS, current.joinToString(PATH_SEPARATOR))
        }
    }

    fun rememberAuthorizedUri(context: Context, uri: Uri?) {
        if (uri == null) return
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
    }

    fun authorizedExternalDirs(): List<String> = defaultPrefs
        .getString(KEY_AUTHORIZED_DIRS, "")
        .orEmpty()
        .split(PATH_SEPARATOR)
        .filter { it.isNotBlank() }
        .mapNotNull { it.canonicalOrNull() }
        .distinct()

    fun roots(context: Context, workspaceRoot: String? = null, terminalWorkingDirectory: String? = null): List<AllowedRoot> {
        val roots = mutableListOf<AllowedRoot>()
        roots.add(AllowedRoot("app_internal", appInternalRoot().absolutePath, "App internal files"))
        roots.add(
            AllowedRoot(
                "terminal_workdir",
                (terminalWorkingDirectory?.canonicalOrNull()
                    ?: terminalWorkingRoot(context).absolutePath),
                "Terminal working directory"
            )
        )
        workspaceRoot
            ?.takeUnless { it.startsWith("content://") }
            ?.canonicalOrNull()
            ?.let { roots.add(AllowedRoot("workspace", it, "Opened workspace")) }
        authorizedExternalDirs().forEachIndexed { index, path ->
            roots.add(AllowedRoot("external_${index + 1}", path, "User authorized external directory"))
        }
        context.contentResolver.persistedUriPermissions.forEachIndexed { index, permission ->
            roots.add(
                AllowedRoot(
                    id = "saf_${index + 1}",
                    path = permission.uri.toString(),
                    label = "Persisted SAF directory"
                )
            )
        }
        return roots.distinctBy { it.path }
    }

    fun requireAllowedPath(
        context: Context,
        path: String?,
        write: Boolean = false,
        workspaceRoot: String? = null,
        terminalWorkingDirectory: String? = null
    ): FileAccessCheck {
        if (path.isNullOrBlank()) {
            return FileAccessCheck.Denied("Missing path")
        }

        if (path.startsWith("content://")) {
            return FileAccessCheck.Denied(
                "content:// paths must be opened through the app UI; raw agent filesystem tools only accept app-internal, terminal workspace, opened workspace, or authorized external file paths."
            )
        }

        val canonical = path.canonicalOrNull()
            ?: return FileAccessCheck.Denied("Invalid path: $path")

        val allowedRoots = roots(
            context = context,
            workspaceRoot = workspaceRoot,
            terminalWorkingDirectory = terminalWorkingDirectory
        ).mapNotNull { root ->
            if (root.path.startsWith("content://")) null else root.path.canonicalOrNull()
        }
        val allowed = allowedRoots.any { canonical.isSameOrChildOf(it) }
        if (!allowed) {
            val action = if (write) "write" else "read"
            return FileAccessCheck.Denied(
                "Refusing to $action outside allowed roots: $canonical. Open the directory in VCSpace or add it in File Manager first."
            )
        }

        return FileAccessCheck.Allowed(JFile(canonical))
    }

    private fun String.canonicalOrNull(): String? = runCatching {
        JFile(this).canonicalPath
    }.getOrNull()

    private fun String.isSameOrChildOf(root: String): Boolean =
        this == root || startsWith("${root.trimEnd('/')}/")
}

data class AllowedRoot(
    val id: String,
    val path: String,
    val label: String
)

sealed class FileAccessCheck {
    data class Allowed(val file: JFile) : FileAccessCheck()
    data class Denied(val reason: String) : FileAccessCheck()
}
