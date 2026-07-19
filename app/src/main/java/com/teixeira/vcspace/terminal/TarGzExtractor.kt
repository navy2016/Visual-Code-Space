/*
 * This file is part of Visual Code Space.
 *
 * Visual Code Space is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 */

package com.teixeira.vcspace.terminal

import android.system.Os
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.GZIPInputStream

/**
 * Minimal tar.gz extractor for Android app-private rootfs archives.
 *
 * Android's system tar/toybox refuses Alpine minirootfs absolute symlinks like
 * `/bin/busybox` with "not under destination". A Linux rootfs needs those
 * symlinks to remain absolute for proot, so extraction is handled here instead
 * of shelling out to `/system/bin/tar`.
 */
object TarGzExtractor {
    private const val BLOCK_SIZE = 512

    fun extract(archive: File, destination: File) {
        destination.mkdirs()
        val destinationCanonical = destination.canonicalFile
        val buffer = ByteArray(64 * 1024)
        var pendingLongName: String? = null
        var pendingLongLink: String? = null
        var pendingPaxHeaders: Map<String, String> = emptyMap()

        GZIPInputStream(BufferedInputStream(FileInputStream(archive))).use { input ->
            while (true) {
                val header = ByteArray(BLOCK_SIZE)
                val read = input.readFullOrEnd(header)
                if (read == -1 || header.isZeroBlock()) break
                if (read != BLOCK_SIZE) {
                    throw IllegalStateException("Truncated tar header in ${archive.name}")
                }

                var name = pendingLongName ?: header.pathName()
                var linkName = pendingLongLink ?: header.stringField(157, 100)
                val size = header.octal(124, 12)
                val mode = header.octal(100, 8).toInt()
                val type = header[156].toInt().toChar().takeIf { it.code != 0 } ?: '0'

                pendingPaxHeaders["path"]?.let { name = it }
                pendingPaxHeaders["linkpath"]?.let { linkName = it }

                pendingLongName = null
                pendingLongLink = null
                pendingPaxHeaders = emptyMap()

                when (type) {
                    'x' -> {
                        pendingPaxHeaders = readEntryBytes(input, size, buffer)
                            .toString(Charsets.UTF_8)
                            .parsePaxHeaders()
                        skipPadding(input, size)
                    }

                    'g' -> {
                        readEntryBytes(input, size, buffer)
                        skipPadding(input, size)
                    }

                    'L' -> {
                        pendingLongName = readEntryBytes(input, size, buffer)
                            .toString(Charsets.UTF_8)
                            .trimEnd('\u0000', '\n')
                        skipPadding(input, size)
                    }

                    'K' -> {
                        pendingLongLink = readEntryBytes(input, size, buffer)
                            .toString(Charsets.UTF_8)
                            .trimEnd('\u0000', '\n')
                        skipPadding(input, size)
                    }

                    else -> {
                        val target = destinationCanonical.safeChild(name)
                        when (type) {
                            '5' -> {
                                target.mkdirs()
                                target.applyMode(mode, directory = true)
                                skipEntry(input, size, buffer)
                            }

                            '2' -> {
                                target.parentFile?.mkdirs()
                                if (target.exists() && !Files.isSymbolicLink(target.toPath())) {
                                    target.deleteRecursively()
                                }
                                Files.deleteIfExists(target.toPath())
                                runCatching {
                                    Os.symlink(linkName, target.absolutePath)
                                }.onFailure { error ->
                                    throw IllegalStateException(
                                        "Failed to create symlink ${target.absolutePath} -> $linkName",
                                        error
                                    )
                                }
                                skipEntry(input, size, buffer)
                            }

                            '1' -> {
                                target.parentFile?.mkdirs()
                                val source = destinationCanonical.safeLinkChild(linkName)
                                runCatching {
                                    Os.link(source.absolutePath, target.absolutePath)
                                }.onFailure {
                                    if (source.isFile) source.copyTo(target, overwrite = true)
                                }
                                target.applyMode(mode, directory = false)
                                skipEntry(input, size, buffer)
                            }

                            '0', '\u0000' -> {
                                target.parentFile?.mkdirs()
                                FileOutputStream(target).use { output ->
                                    copyExactly(input, output, size, buffer)
                                }
                                target.applyMode(mode, directory = false)
                                skipPadding(input, size)
                            }

                            else -> skipEntry(input, size, buffer)
                        }
                    }
                }
            }
        }
    }

    private fun File.safeChild(entryName: String): File {
        val normalized = entryName
            .replace('\\', '/')
            .removePrefix("./")
            .trimStart('/')

        if (normalized.isBlank() || normalized == ".") return this
        val parts = normalized.split('/').filter { it.isNotBlank() }
        require(parts.none { it == "." || it == ".." }) {
            "Unsafe tar entry path: $entryName"
        }

        val child = parts.fold(this) { parent, part -> File(parent, part) }
        val parent = (if (entryName.endsWith('/')) child else child.parentFile ?: this).canonicalFile
        val rootPath = canonicalPath
        val parentPath = parent.canonicalPath
        require(parentPath == rootPath || parentPath.startsWith("$rootPath/")) {
            "Tar entry escapes destination: $entryName"
        }
        return child
    }

    private fun File.safeLinkChild(entryName: String): File {
        val normalized = entryName
            .replace('\\', '/')
            .trimStart('/')
            .removePrefix("./")
        require(normalized.isNotBlank()) { "Empty hard link target" }
        require(!normalized.startsWith("../") && normalized != "..") {
            "Unsafe hard link target: $entryName"
        }
        return safeChild(normalized)
    }

    private fun ByteArray.pathName(): String {
        val name = stringField(0, 100)
        val prefix = stringField(345, 155)
        return if (prefix.isBlank()) name else "$prefix/$name"
    }

    private fun ByteArray.stringField(offset: Int, length: Int): String {
        val end = (offset until offset + length).firstOrNull { this[it] == 0.toByte() } ?: (offset + length)
        return copyOfRange(offset, end).toString(Charsets.UTF_8)
    }

    private fun ByteArray.octal(offset: Int, length: Int): Long {
        val raw = stringField(offset, length).trim('\u0000', ' ', '\n')
        if (raw.isBlank()) return 0L
        return raw.toLongOrNull(8) ?: 0L
    }

    private fun ByteArray.isZeroBlock(): Boolean = all { it == 0.toByte() }

    private fun java.io.InputStream.readFullOrEnd(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val count = read(buffer, total, buffer.size - total)
            if (count == -1) return if (total == 0) -1 else total
            total += count
        }
        return total
    }

    private fun readEntryBytes(
        input: java.io.InputStream,
        size: Long,
        buffer: ByteArray
    ): ByteArray {
        require(size <= Int.MAX_VALUE) { "Tar entry is too large: $size bytes" }
        val bytes = ByteArray(size.toInt())
        var offset = 0
        var remaining = size
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count == -1) throw IllegalStateException("Unexpected EOF in tar entry")
            System.arraycopy(buffer, 0, bytes, offset, count)
            offset += count
            remaining -= count
        }
        return bytes
    }

    private fun copyExactly(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        size: Long,
        buffer: ByteArray
    ) {
        var remaining = size
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count == -1) throw IllegalStateException("Unexpected EOF in tar entry")
            output.write(buffer, 0, count)
            remaining -= count
        }
    }

    private fun skipEntry(input: java.io.InputStream, size: Long, buffer: ByteArray) {
        var remaining = size
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count == -1) throw IllegalStateException("Unexpected EOF while skipping tar entry")
            remaining -= count
        }
        skipPadding(input, size)
    }

    private fun skipPadding(input: java.io.InputStream, size: Long) {
        val padding = (BLOCK_SIZE - (size % BLOCK_SIZE)) % BLOCK_SIZE
        var remaining = padding
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() == -1) throw IllegalStateException("Unexpected EOF in tar padding")
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun String.parsePaxHeaders(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var index = 0
        while (index < length) {
            val space = indexOf(' ', startIndex = index)
            if (space <= index) break
            val recordLength = substring(index, space).toIntOrNull() ?: break
            val recordEnd = (index + recordLength).coerceAtMost(length)
            val record = substring(space + 1, recordEnd).trimEnd('\n')
            val equals = record.indexOf('=')
            if (equals > 0) {
                result[record.substring(0, equals)] = record.substring(equals + 1)
            }
            index += recordLength
        }
        return result
    }

    private fun File.applyMode(mode: Int, directory: Boolean) {
        setReadable(true, false)
        setWritable(true, true)
        val executable = directory || (mode and 0b001001001) != 0
        setExecutable(executable, false)
    }
}
