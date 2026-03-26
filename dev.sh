#!/bin/bash

PACKAGE="com.royal.insightlens"

case "$1" in
  dev)
    echo "🚀 Installing app..."
    ./gradlew installDebug

    echo "📲 Launching app..."
    adb shell monkey -p $PACKAGE -c android.intent.category.LAUNCHER 1
    ;;

  logs)
    echo "📱 Streaming logs..."
    PID=$(adb shell pidof $PACKAGE)

    if [ -z "$PID" ]; then
      echo "⚠️ App not running. Open it first."
      exit 1
    fi

    adb logcat --pid=$PID
    ;;
esac
