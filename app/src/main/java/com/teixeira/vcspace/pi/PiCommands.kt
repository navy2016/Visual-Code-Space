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
    private const val NPM_CLI = "/usr/lib/node_modules/npm/bin/npm-cli.js"
    private const val PI_CLI = "/usr/lib/node_modules/@earendil-works/pi-coding-agent/dist/cli.js"

    private val npmHelpers: String = """
        export NPM_CONFIG_REGISTRY="${'$'}{NPM_CONFIG_REGISTRY:-https://registry.npmmirror.com}"

        find_npm_cli() {
          if [ -f "$NPM_CLI" ]; then
            echo "$NPM_CLI"
            return 0
          fi
          find /usr/lib /usr/local/lib -path '*/npm-cli.js' -type f 2>/dev/null | head -n 1
        }

        repair_npm_cli() {
          if [ -n "${'$'}(find_npm_cli)" ]; then
            return 0
          fi

          echo '[VCSpace] npm CLI files are missing; repairing npm package...'
          apk fix npm >/dev/null 2>&1 || true
          if [ -n "${'$'}(find_npm_cli)" ]; then
            return 0
          fi

          npm_tmp="${'$'}(mktemp -d /tmp/vcspace-npm.XXXXXX)" || return 1
          if apk fetch -o "${'$'}npm_tmp" npm >/dev/null 2>&1; then
            npm_apk="${'$'}(find "${'$'}npm_tmp" -name 'npm-*.apk' -type f | head -n 1)"
            if [ -n "${'$'}npm_apk" ]; then
              rm -f /usr/bin/npm /usr/bin/npx /usr/bin/node-gyp
              tar -xzf "${'$'}npm_apk" -C /
              rm -f /.PKGINFO /.SIGN.*
            fi
          fi
          rm -rf "${'$'}npm_tmp"

          if [ -z "${'$'}(find_npm_cli)" ]; then
            echo '[VCSpace] npm CLI repair failed.'
            echo '[VCSpace] Expected npm CLI: $NPM_CLI'
            return 1
          fi
        }

        run_npm() {
          npm_cli="${'$'}(find_npm_cli)"
          if [ -n "${'$'}npm_cli" ]; then
            node "${'$'}npm_cli" "${'$'}@"
          elif command -v npm >/dev/null 2>&1 && npm --version >/dev/null 2>&1; then
            npm "${'$'}@"
          else
            echo '[VCSpace] npm is unavailable after apk install.'
            echo '[VCSpace] Expected npm CLI: $NPM_CLI'
            return 1
          fi
        }

        ensure_npm_ready() {
          repair_npm_cli && run_npm --version >/dev/null
        }

        install_pi_launcher() {
          if [ -f "$PI_CLI" ]; then
            rm -f /usr/bin/pi
            printf '%s\n' \
              '#!/bin/sh' \
              'exec node /usr/lib/node_modules/@earendil-works/pi-coding-agent/dist/cli.js "${'$'}@"' \
              > /usr/bin/pi
            chmod +x /usr/bin/pi
          else
            echo '[VCSpace] Pi CLI was not found after npm install.'
            echo '[VCSpace] Expected Pi CLI: $PI_CLI'
            return 1
          fi
        }
    """.trimIndent()

    val OPEN_PI: String = """
        if [ -f "$PI_CLI" ]; then
          echo '[VCSpace] Starting Pi with Visual Code Space bridge...'
          node "$PI_CLI"
        elif command -v pi >/dev/null 2>&1; then
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
        $npmHelpers

        echo '[VCSpace] Installing Node.js, npm, Git and Pi...'
        echo "[VCSpace] npm registry: ${'$'}NPM_CONFIG_REGISTRY"
        if apk add --no-cache nodejs npm git && ensure_npm_ready && run_npm install -g --ignore-scripts $PACKAGE && install_pi_launcher; then
          mkdir -p /home/.vcspace
          date -u +%FT%TZ > $MARKER
          echo '[VCSpace] Pi installed. Run `pi` or use Open Pi in Terminal.'
        else
          echo '[VCSpace] Pi installation failed. Check the logs above or try Repair Pi.'
        fi
        exec /bin/bash
    """.trimIndent()

    val UPDATE_PI: String = """
        $npmHelpers

        echo '[VCSpace] Updating Pi...'
        echo "[VCSpace] npm registry: ${'$'}NPM_CONFIG_REGISTRY"
        if apk add --no-cache nodejs npm git && ensure_npm_ready && run_npm install -g --ignore-scripts $PACKAGE@latest && install_pi_launcher; then
          mkdir -p /home/.vcspace
          date -u +%FT%TZ > $MARKER
          echo '[VCSpace] Pi updated.'
        else
          echo '[VCSpace] Pi update failed. Check the logs above or try Repair Pi.'
        fi
        exec /bin/bash
    """.trimIndent()

    val REPAIR_PI: String = """
        $npmHelpers

        echo '[VCSpace] Repairing Pi installation...'
        echo "[VCSpace] npm registry: ${'$'}NPM_CONFIG_REGISTRY"
        apk fix || true
        if apk add --no-cache nodejs npm git && ensure_npm_ready && (run_npm cache verify || true) && run_npm install -g --ignore-scripts --force $PACKAGE@latest && install_pi_launcher; then
          mkdir -p /home/.vcspace
          date -u +%FT%TZ > $MARKER
          echo '[VCSpace] Pi repair finished.'
        else
          echo '[VCSpace] Pi repair failed. Check the logs above.'
        fi
        exec /bin/bash
    """.trimIndent()
}
