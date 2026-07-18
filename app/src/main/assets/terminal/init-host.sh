prootArgs="-r $ALPINE -0 -b /dev/ -b /sys/ -b /proc/ -b /sdcard -b /storage -b $PREFIX -b $HOME:/home -w /home --kill-on-exit --link2symlink"

exec $PROOT $prootArgs /bin/sh $PREFIX/bin/init "$@"
