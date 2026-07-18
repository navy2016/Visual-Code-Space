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

package com.teixeira.vcspace.pi

object PiCommands {
    private const val PACKAGE = "@earendil-works/pi-coding-agent"
    private const val MARKER = "/home/.vcspace/pi-installed"

    val OPEN_PI: String = """
        if command -v pi >/dev/null 2>&1; then
          echo '[VCSpace] Starting Pi with Visual Code Space bridge...'
          pi
        else
          echo '[VCSpace] Pi is not installed.'
          echo '[VCSpace] Run Install Pi from the command palette, or run:'
          echo '          npm install -g --ignore-scripts @earendil-works/pi-coding-agent'
        fi
        exec /bin/bash
    """.trimIndent()

    val INSTALL_PI: String = """
        echo '[VCSpace] Installing Node.js, npm, Git and Pi...'
        if apk add --update nodejs npm git && npm install -g --ignore-scripts $PACKAGE; then
          mkdir -p /home/.vcspace
          date -u +%FT%TZ > $MARKER
          echo '[VCSpace] Pi installed. Run `pi` or use Open Pi in Terminal.'
        else
          echo '[VCSpace] Pi installation failed. Check the logs above or try Repair Pi.'
        fi
        exec /bin/bash
    """.trimIndent()

    val UPDATE_PI: String = """
        echo '[VCSpace] Updating Pi...'
        if apk add --update nodejs npm git && npm install -g --ignore-scripts $PACKAGE@latest; then
          mkdir -p /home/.vcspace
          date -u +%FT%TZ > $MARKER
          echo '[VCSpace] Pi updated.'
        else
          echo '[VCSpace] Pi update failed. Check the logs above or try Repair Pi.'
        fi
        exec /bin/bash
    """.trimIndent()

    val REPAIR_PI: String = """
        echo '[VCSpace] Repairing Pi installation...'
        apk fix || true
        if apk add --update nodejs npm git && (npm cache verify || true) && npm install -g --ignore-scripts --force $PACKAGE@latest; then
          mkdir -p /home/.vcspace
          date -u +%FT%TZ > $MARKER
          echo '[VCSpace] Pi repair finished.'
        else
          echo '[VCSpace] Pi repair failed. Check the logs above.'
        fi
        exec /bin/bash
    """.trimIndent()
}
