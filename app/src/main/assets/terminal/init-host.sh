mkdir -p "$ALPINE/workspace"

if [ -n "$VCSPACE_TERMINAL_WORKDIR" ] && [ -d "$VCSPACE_TERMINAL_WORKDIR" ]; then
    exec "$PROOT" \
        -r "$ALPINE" \
        -0 \
        -b /dev/ \
        -b /sys/ \
        -b /proc/ \
        -b "$PREFIX" \
        -b "$HOME" \
        -b "$HOME:/home" \
        -b "$VCSPACE_TERMINAL_WORKDIR:/workspace" \
        -w /home \
        --kill-on-exit \
        --link2symlink \
        /bin/sh "$PREFIX/bin/init" "$@"
else
    exec "$PROOT" \
        -r "$ALPINE" \
        -0 \
        -b /dev/ \
        -b /sys/ \
        -b /proc/ \
        -b "$PREFIX" \
        -b "$HOME" \
        -b "$HOME:/home" \
        -w /home \
        --kill-on-exit \
        --link2symlink \
        /bin/sh "$PREFIX/bin/init" "$@"
fi
