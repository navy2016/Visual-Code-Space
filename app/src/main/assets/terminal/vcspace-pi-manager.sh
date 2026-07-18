#!/bin/sh
# Visual Code Space Pi manager. Runs inside the Alpine/proot terminal.

PACKAGE="${PI_PACKAGE:-@earendil-works/pi-coding-agent}"
MARKER="/home/.vcspace/pi-installed"
NPM_VERSION="${NPM_VERSION:-11.6.4}"
NPM_TARBALL="${NPM_TARBALL:-https://registry.npmmirror.com/npm/-/npm-${NPM_VERSION}.tgz}"
NPM_CLI="/usr/lib/node_modules/npm/bin/npm-cli.js"
PI_CLI="/usr/lib/node_modules/@earendil-works/pi-coding-agent/dist/cli.js"
export LD_LIBRARY_PATH="${LD_LIBRARY_PATH:-/usr/lib:/lib}"
export NPM_CONFIG_REGISTRY="${NPM_CONFIG_REGISTRY:-https://registry.npmmirror.com}"
export NPM_CONFIG_PREFIX="${NPM_CONFIG_PREFIX:-/usr}"

log() {
  echo "[VCSpace] $*"
}

find_npm_cli() {
  if [ -f "$NPM_CLI" ]; then
    echo "$NPM_CLI"
    return 0
  fi

  if [ -x /usr/bin/find ]; then
    /usr/bin/find /usr/lib /usr/local/lib -path '*/npm-cli.js' -type f 2>/dev/null | head -n 1
  else
    find /usr/lib /usr/local/lib -path '*/npm-cli.js' -type f 2>/dev/null | head -n 1
  fi
}

write_npm_launcher() {
  if [ -f "$NPM_CLI" ]; then
    rm -f /usr/bin/npm /usr/bin/npx
    printf '%s\n' '#!/bin/sh' 'export LD_LIBRARY_PATH="${LD_LIBRARY_PATH:-/usr/lib:/lib}"' 'exec /usr/bin/node /usr/lib/node_modules/npm/bin/npm-cli.js "$@"' > /usr/bin/npm
    printf '%s\n' '#!/bin/sh' 'export LD_LIBRARY_PATH="${LD_LIBRARY_PATH:-/usr/lib:/lib}"' 'exec /usr/bin/node /usr/lib/node_modules/npm/bin/npx-cli.js "$@"' > /usr/bin/npx
    chmod +x /usr/bin/npm /usr/bin/npx
  fi
}

install_base_packages() {
  apk add --no-cache nodejs git ca-certificates wget tar gzip findutils
}

install_npm_from_registry() {
  log "Installing standalone npm bundle from registry mirror..."
  npm_tmp="$(mktemp -d /tmp/vcspace-npm-registry.XXXXXX)" || return 1
  npm_tgz="$npm_tmp/npm.tgz"
  npm_unpack="$npm_tmp/unpack"
  mkdir -p "$npm_unpack"

  if ! wget -q -O "$npm_tgz" "$NPM_TARBALL"; then
    log "Failed to download standalone npm bundle: $NPM_TARBALL"
    rm -rf "$npm_tmp"
    return 1
  fi

  rm -rf /usr/lib/node_modules/npm
  mkdir -p /usr/lib/node_modules/npm

  if [ -x /usr/bin/tar ]; then
    if ! /usr/bin/tar -xzf "$npm_tgz" -C /usr/lib/node_modules/npm --strip-components=1; then
      log "Failed to unpack standalone npm bundle with GNU tar."
      rm -rf "$npm_tmp" /usr/lib/node_modules/npm
      return 1
    fi
  else
    if ! tar -xzf "$npm_tgz" -C "$npm_unpack"; then
      log "Failed to unpack standalone npm bundle."
      rm -rf "$npm_tmp" /usr/lib/node_modules/npm
      return 1
    fi
    cp -R "$npm_unpack/package/." /usr/lib/node_modules/npm/ || {
      log "Failed to copy standalone npm bundle."
      rm -rf "$npm_tmp" /usr/lib/node_modules/npm
      return 1
    }
  fi

  rm -rf "$npm_tmp"
  if [ ! -f "$NPM_CLI" ]; then
    log "Standalone npm bundle did not create $NPM_CLI"
    ls -la /usr/lib/node_modules/npm 2>/dev/null || true
    ls -la /usr/lib/node_modules/npm/bin 2>/dev/null || true
    return 1
  fi

  write_npm_launcher
}

ensure_npm_ready() {
  npm_cli="$(find_npm_cli)"
  if [ -n "$npm_cli" ] && /usr/bin/node "$npm_cli" --version >/dev/null 2>&1; then
    write_npm_launcher
    return 0
  fi

  log "npm CLI is missing or broken; installing standalone npm..."
  if install_npm_from_registry; then
    npm_cli="$(find_npm_cli)"
    if [ -n "$npm_cli" ] && /usr/bin/node "$npm_cli" --version >/dev/null 2>&1; then
      write_npm_launcher
      return 0
    fi
  fi

  log "npm CLI repair failed. Expected npm CLI: $NPM_CLI"
  return 1
}

run_npm() {
  npm_cli="$(find_npm_cli)"
  if [ -n "$npm_cli" ]; then
    /usr/bin/node "$npm_cli" "$@"
  else
    log "npm is unavailable. Expected npm CLI: $NPM_CLI"
    return 1
  fi
}

install_pi_launcher() {
  if [ -f "$PI_CLI" ]; then
    rm -f /usr/bin/pi
    printf '%s\n' '#!/bin/sh' 'export LD_LIBRARY_PATH="${LD_LIBRARY_PATH:-/usr/lib:/lib}"' 'exec /usr/bin/node /usr/lib/node_modules/@earendil-works/pi-coding-agent/dist/cli.js "$@"' > /usr/bin/pi
    chmod +x /usr/bin/pi
    return 0
  fi

  log "Pi CLI was not found after npm install. Expected Pi CLI: $PI_CLI"
  return 1
}

mark_installed() {
  mkdir -p /home/.vcspace
  date -u +%FT%TZ > "$MARKER"
}

install_pi() {
  log "Installing Node.js, Git, npm and Pi..."
  log "npm registry: $NPM_CONFIG_REGISTRY"
  if install_base_packages && ensure_npm_ready && run_npm install -g --ignore-scripts "$PACKAGE" && install_pi_launcher; then
    mark_installed
    log "Pi installed. Run 'pi' or use Open Pi in Terminal."
    return 0
  fi
  log "Pi installation failed. Check the logs above or try Repair Pi."
  return 1
}

update_pi() {
  log "Updating Pi..."
  log "npm registry: $NPM_CONFIG_REGISTRY"
  if install_base_packages && ensure_npm_ready && run_npm install -g --ignore-scripts "$PACKAGE@latest" && install_pi_launcher; then
    mark_installed
    log "Pi updated."
    return 0
  fi
  log "Pi update failed. Check the logs above or try Repair Pi."
  return 1
}

repair_pi() {
  log "Repairing Pi installation..."
  log "npm registry: $NPM_CONFIG_REGISTRY"
  if install_base_packages && ensure_npm_ready && (run_npm cache verify || true) && run_npm install -g --ignore-scripts --force "$PACKAGE@latest" && install_pi_launcher; then
    mark_installed
    log "Pi repair finished."
    return 0
  fi
  log "Pi repair failed. Check the logs above."
  return 1
}

open_pi() {
  if [ -f "$PI_CLI" ]; then
    log "Starting Pi with Visual Code Space bridge..."
    exec /usr/bin/node "$PI_CLI"
  elif command -v pi >/dev/null 2>&1; then
    log "Starting Pi with Visual Code Space bridge..."
    exec pi
  fi

  log "Pi is not installed. Run Install Pi from the command palette first."
  return 1
}

smoke() {
  log "Running Pi manager smoke test..."
  install_base_packages || return 1
  ensure_npm_ready || return 1
  /usr/bin/node --version
  run_npm --version || return 1
  log "Pi manager smoke test passed."
}

case "${1:-open}" in
  install) install_pi ;;
  update) update_pi ;;
  repair) repair_pi ;;
  open) open_pi ;;
  smoke) smoke ;;
  *)
    echo "Usage: vcspace-pi-manager.sh {install|update|repair|open|smoke}"
    exit 2
    ;;
esac
