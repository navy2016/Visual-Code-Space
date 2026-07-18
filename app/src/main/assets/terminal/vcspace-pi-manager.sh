#!/bin/sh
# Visual Code Space Pi manager. Runs inside the Android proot Alpine terminal.
#
# Android/proot note:
# Node.js can fail to read files that only exist inside the fake Alpine root
# (for example /usr/lib/node_modules or /home). Store npm, Pi, and Pi agent
# state under the Android-side PREFIX bind mount and invoke Node with those real
# PREFIX paths.

PACKAGE="${PI_PACKAGE:-@earendil-works/pi-coding-agent}"
HOST_PREFIX="${PREFIX:-/usr/local/vcspace}"
HOST_FILES_DIR="${HOST_PREFIX%/usr}"
if [ "$HOST_FILES_DIR" = "$HOST_PREFIX" ]; then
  HOST_FILES_DIR="$(dirname "$HOST_PREFIX")"
fi
VCSPACE_HOME="${VCSPACE_HOME:-$HOST_FILES_DIR/home}"
HOST_TMP="${TMPDIR:-$HOST_PREFIX/tmp}"
AGENT_DIR="${PI_CODING_AGENT_DIR:-$VCSPACE_HOME/.pi/agent}"
MARKER="$VCSPACE_HOME/.vcspace/pi-installed"
LEGACY_MARKER="/home/.vcspace/pi-installed"
NPM_VERSION="${NPM_VERSION:-11.6.4}"
NPM_TARBALL="${NPM_TARBALL:-https://registry.npmmirror.com/npm/-/npm-${NPM_VERSION}.tgz}"
NPM_ROOT="${NPM_ROOT:-$HOST_PREFIX/lib/node_modules/npm}"
NPM_CLI="$NPM_ROOT/bin/npm-cli.js"
PI_ROOT="${PI_ROOT:-$HOST_PREFIX/lib/node_modules/@earendil-works/pi-coding-agent}"
PI_CLI="$PI_ROOT/dist/cli.js"

export LD_LIBRARY_PATH="${LD_LIBRARY_PATH:-/usr/lib:/lib}"
export NPM_CONFIG_REGISTRY="${NPM_CONFIG_REGISTRY:-https://registry.npmmirror.com}"
export NPM_CONFIG_PREFIX="${NPM_CONFIG_PREFIX:-$HOST_PREFIX}"
export NPM_CONFIG_CACHE="${NPM_CONFIG_CACHE:-$HOST_PREFIX/var/npm-cache}"
export PI_CODING_AGENT_DIR="$AGENT_DIR"
export PI_CODING_AGENT_SESSION_DIR="${PI_CODING_AGENT_SESSION_DIR:-$AGENT_DIR/sessions}"
export XDG_CACHE_HOME="${XDG_CACHE_HOME:-$VCSPACE_HOME/.cache}"
export TMPDIR="$HOST_TMP"
export PATH="$HOST_PREFIX/bin:/usr/local/bin:/bin:/sbin:/usr/bin:/usr/sbin:/usr/local/sbin"
APK_REPOSITORY_BASE="${APK_REPOSITORY_BASE:-https://mirrors.aliyun.com/alpine/v3.22}"

log() {
  echo "[VCSpace] $*"
}

prepare_host_dirs() {
  mkdir -p \
    "$HOST_PREFIX/bin" \
    "$HOST_PREFIX/lib/node_modules" \
    "$HOST_PREFIX/var/npm-cache" \
    "$HOST_PREFIX/var/cache" \
    "$VCSPACE_HOME/.cache" \
    "$HOST_TMP" \
    "$AGENT_DIR/extensions" \
    "$AGENT_DIR/bin" \
    "$PI_CODING_AGENT_SESSION_DIR" \
    /usr/local/bin

  if [ ! -f "$AGENT_DIR/auth.json" ]; then
    printf '{}\n' > "$AGENT_DIR/auth.json" 2>/dev/null || true
    chmod 600 "$AGENT_DIR/auth.json" 2>/dev/null || true
  fi
}

find_npm_cli() {
  if [ -f "$NPM_CLI" ]; then
    echo "$NPM_CLI"
    return 0
  fi

  if [ -x /usr/bin/find ]; then
    /usr/bin/find "$HOST_PREFIX/lib/node_modules" -path '*/npm-cli.js' -type f 2>/dev/null | head -n 1
  else
    find "$HOST_PREFIX/lib/node_modules" -path '*/npm-cli.js' -type f 2>/dev/null | head -n 1
  fi
}

write_node_launcher() {
  launcher="$1"
  target="$2"
  mkdir -p "$(dirname "$launcher")"
  rm -f "$launcher"
  printf '%s\n' \
    '#!/bin/sh' \
    'export LD_LIBRARY_PATH="${LD_LIBRARY_PATH:-/usr/lib:/lib}"' \
    "export PI_CODING_AGENT_DIR='$AGENT_DIR'" \
    "export PI_CODING_AGENT_SESSION_DIR='$PI_CODING_AGENT_SESSION_DIR'" \
    "export XDG_CACHE_HOME='$XDG_CACHE_HOME'" \
    "exec /usr/bin/node '$target' \"\$@\"" \
    > "$launcher"
  chmod +x "$launcher"
}

write_npm_launcher() {
  if [ -f "$NPM_CLI" ]; then
    write_node_launcher "$HOST_PREFIX/bin/npm" "$NPM_CLI"
    write_node_launcher "$HOST_PREFIX/bin/npx" "$NPM_ROOT/bin/npx-cli.js"
    write_node_launcher "/usr/local/bin/npm" "$NPM_CLI"
    write_node_launcher "/usr/local/bin/npx" "$NPM_ROOT/bin/npx-cli.js"
  fi
}

configure_apk_repositories() {
  if [ -n "$APK_REPOSITORY_BASE" ] && [ -d /etc/apk ]; then
    printf "%s/main\n%s/community\n" "$APK_REPOSITORY_BASE" "$APK_REPOSITORY_BASE" > /etc/apk/repositories
  fi
}

install_base_packages() {
  configure_apk_repositories
  apk add --no-cache nodejs git ca-certificates wget tar gzip findutils
}

install_npm_from_registry() {
  log "Installing standalone npm bundle from registry mirror..."
  prepare_host_dirs
  npm_tmp="$(mktemp -d "$HOST_TMP/vcspace-npm-registry.XXXXXX")" || return 1
  npm_tgz="$npm_tmp/npm.tgz"

  if ! wget -q -O "$npm_tgz" "$NPM_TARBALL"; then
    log "Failed to download standalone npm bundle: $NPM_TARBALL"
    rm -rf "$npm_tmp"
    return 1
  fi

  rm -rf "$NPM_ROOT"
  mkdir -p "$NPM_ROOT"

  tar_bin="$(command -v tar || true)"
  if [ -z "$tar_bin" ]; then
    log "tar is unavailable after apk install."
    rm -rf "$npm_tmp" "$NPM_ROOT"
    return 1
  fi

  if ! "$tar_bin" -xzf "$npm_tgz" -C "$NPM_ROOT" --strip-components=1; then
    log "Failed to unpack standalone npm bundle with tar: $tar_bin"
    rm -rf "$npm_tmp" "$NPM_ROOT"
    return 1
  fi

  rm -rf "$npm_tmp"
  if [ ! -f "$NPM_CLI" ]; then
    log "Standalone npm bundle did not create $NPM_CLI"
    ls -la "$NPM_ROOT" 2>/dev/null || true
    ls -la "$NPM_ROOT/bin" 2>/dev/null || true
    return 1
  fi

  write_npm_launcher
}

ensure_npm_ready() {
  prepare_host_dirs
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
  if [ -n "$npm_cli" ]; then
    log "Found npm CLI candidate: $npm_cli"
    /usr/bin/node "$npm_cli" --version || true
  fi
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

install_pi_package() {
  requested_package="$1"
  shift || true
  prepare_host_dirs
  run_npm install -g --ignore-scripts "$@" "$requested_package"
}

install_pi_launcher() {
  if [ -f "$PI_CLI" ]; then
    write_node_launcher "$HOST_PREFIX/bin/pi" "$PI_CLI"
    write_node_launcher "/usr/local/bin/pi" "$PI_CLI"
    return 0
  fi

  log "Pi CLI was not found after npm install. Expected Pi CLI: $PI_CLI"
  return 1
}

mark_installed() {
  mkdir -p "$(dirname "$MARKER")"
  date -u +%FT%TZ > "$MARKER"

  mkdir -p "$(dirname "$LEGACY_MARKER")" 2>/dev/null || true
  date -u +%FT%TZ > "$LEGACY_MARKER" 2>/dev/null || true
}

install_pi() {
  log "Installing Node.js, Git, npm and Pi..."
  log "npm registry: $NPM_CONFIG_REGISTRY"
  log "host prefix: $HOST_PREFIX"
  log "agent dir: $AGENT_DIR"
  if install_base_packages && ensure_npm_ready && install_pi_package "$PACKAGE" && install_pi_launcher; then
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
  log "host prefix: $HOST_PREFIX"
  log "agent dir: $AGENT_DIR"
  if install_base_packages && ensure_npm_ready && install_pi_package "$PACKAGE@latest" && install_pi_launcher; then
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
  log "host prefix: $HOST_PREFIX"
  log "agent dir: $AGENT_DIR"
  if install_base_packages && ensure_npm_ready && (run_npm cache verify || true) && install_pi_package "$PACKAGE@latest" --force && install_pi_launcher; then
    mark_installed
    log "Pi repair finished."
    return 0
  fi
  log "Pi repair failed. Check the logs above."
  return 1
}

open_pi() {
  prepare_host_dirs
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
  log "host prefix: $HOST_PREFIX"
  log "agent dir: $AGENT_DIR"
  install_base_packages || return 1
  ensure_npm_ready || return 1
  /usr/bin/node --version
  run_npm --version || return 1
  printf '{}\n' > "$AGENT_DIR/auth.json"
  printf 'export default function vcspaceSmoke() {}\n' > "$AGENT_DIR/extensions/vcspace-bridge.ts"
  /usr/bin/node -e 'const fs = require("fs"); for (const p of [process.env.PI_CODING_AGENT_DIR, `${process.env.PI_CODING_AGENT_DIR}/auth.json`, `${process.env.PI_CODING_AGENT_DIR}/extensions/vcspace-bridge.ts`, `${process.env.PI_CODING_AGENT_DIR}/bin`]) { if (!fs.existsSync(p)) { console.error(`missing ${p}`); process.exit(1); } }'
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
