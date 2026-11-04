#!/bin/bash
APK=work/y7s-a11/SystemUI.apk
for c in \
  com.android.systemui.media.MediaControlPanel \
  com.android.systemui.media.NotificationPlayer \
  com.android.systemui.media.PlayerViewHolder \
  com.android.systemui.media.MediaCarouselController ; do
  ./work/y7s-a11/jdx.sh "$APK" "$c" "work/y7s-a11/tmp/$(basename ${c##*.}).java" 2>&1 | tail -1
  echo "done $c"
done
