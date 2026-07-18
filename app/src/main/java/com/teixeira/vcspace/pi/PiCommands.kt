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
    const val OPEN_PI = "if command -v pi >/dev/null 2>&1; then pi; exec /bin/bash; else echo 'Pi is not installed. Run Install Pi from the command palette first.'; exec /bin/bash; fi"

    const val INSTALL_PI = "apk add nodejs npm git && npm install -g --ignore-scripts @earendil-works/pi-coding-agent; exec /bin/bash"
}
