#
# This file is part of Visual Code Space.
#
# Visual Code Space is free software: you can redistribute it and/or modify it under the terms of
# the GNU General Public License as published by the Free Software Foundation, either version 3 of
# the License, or (at your option) any later version.
#
# Visual Code Space is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
# without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License along with Visual Code Space.
# If not, see <https://www.gnu.org/licenses/>.
#

#https://github.com/Xed-Editor/Xed-Editor/blob/main/core/main/src/main/assets/terminal/init.sh
# Keep terminal startup resilient: failed package bootstrap should not close the shell.

export PATH="$PREFIX/bin:/usr/local/bin:/bin:/sbin:/usr/bin:/usr/sbin:/usr/share/bin:/usr/share/sbin:/usr/local/sbin"
export HOME=/home
HOST_FILES_DIR="${PREFIX%/usr}"
if [ "$HOST_FILES_DIR" = "$PREFIX" ]; then
    HOST_FILES_DIR="$(dirname "$PREFIX")"
fi
export VCSPACE_HOME="${VCSPACE_HOME:-$HOST_FILES_DIR/home}"
export PI_CODING_AGENT_DIR="${PI_CODING_AGENT_DIR:-$VCSPACE_HOME/.pi/agent}"
export PI_CODING_AGENT_SESSION_DIR="${PI_CODING_AGENT_SESSION_DIR:-$PI_CODING_AGENT_DIR/sessions}"
export XDG_CACHE_HOME="${XDG_CACHE_HOME:-$VCSPACE_HOME/.cache}"
mkdir -p "$PI_CODING_AGENT_DIR/extensions" "$PI_CODING_AGENT_DIR/bin" "$PI_CODING_AGENT_SESSION_DIR" "$XDG_CACHE_HOME"
export PROMPT_DIRTRIM=2
export PS1="\[\e[38;5;46m\]\u\[\033[39m\]@vcspace \[\033[39m\]\w \[\033[0m\]\\$ "
START_SHELL="/bin/bash"
APK_REPOSITORY_BASE="${APK_REPOSITORY_BASE:-https://mirrors.aliyun.com/alpine/v3.22}"

configure_apk_repositories() {
    if [ -n "$APK_REPOSITORY_BASE" ] && [ -d /etc/apk ]; then
        printf "%s/main\n%s/community\n" "$APK_REPOSITORY_BASE" "$APK_REPOSITORY_BASE" > /etc/apk/repositories
    fi
}

configure_apk_repositories

required_packages="bash"
missing_packages=""
for pkg in $required_packages; do
    if ! apk info -e $pkg >/dev/null 2>&1; then
        missing_packages="$missing_packages $pkg"
    fi
done
if [ -n "$missing_packages" ]; then
    echo -e "\e[34;1m[*] \e[37mInstalling minimal terminal packages\e[0m"
    echo -e "\e[34m[*] \e[37mUsing Alpine mirror: \e[32m$APK_REPOSITORY_BASE\e[0m"
    if apk add --no-cache $missing_packages; then
        echo -e "\e[32;1m[+] \e[37mSuccessfully Installed\e[0m"
    else
        echo -e "\e[31;1m[-] \e[37mPackage bootstrap failed. Falling back to /bin/sh.\e[0m"
        START_SHELL="/bin/sh"
    fi
    echo -e "\e[34m[*] \e[37mUse \e[32mapk add\e[37m to install optional packages, e.g. nano sudo file build-base\e[0m"
fi

if [ ! -x "$START_SHELL" ]; then
    START_SHELL="/bin/sh"
fi

if [ -x "$PREFIX/bin/vcspace-pi-manager" ]; then
    "$PREFIX/bin/vcspace-pi-manager" repair-launchers >/dev/null 2>&1 || true
fi
if [ -x "$PREFIX/bin/pi" ]; then
    mkdir -p /usr/local/bin
    ln -sf "$PREFIX/bin/pi" /usr/local/bin/pi 2>/dev/null || true
fi
if [ -x "$PREFIX/bin/npm" ]; then
    mkdir -p /usr/local/bin
    ln -sf "$PREFIX/bin/npm" /usr/local/bin/npm 2>/dev/null || true
fi
if [ -x "$PREFIX/bin/npx" ]; then
    mkdir -p /usr/local/bin
    ln -sf "$PREFIX/bin/npx" /usr/local/bin/npx 2>/dev/null || true
fi

#fix linker warning
if [ ! -f /linkerconfig/ld.config.txt ]; then
    mkdir -p /linkerconfig
    touch /linkerconfig/ld.config.txt
fi

if [ "$#" -eq 0 ]; then
    $START_SHELL
else
    if [ "$#" -eq 1 ]; then
        $START_SHELL -lc "$1"
    else
        "$@"
    fi
fi
