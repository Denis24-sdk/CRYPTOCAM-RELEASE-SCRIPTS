#!/usr/bin/env bash

git submodule init 
git submodule update

cd ..
if [ -f "ffmpeg-android-maker/output/lib/armeabi-v7a/libavformat.so" -a -f "ffmpeg-android-maker/output/lib/armeabi-v7a/libavutil.so" -a -f "ffmpeg-android-maker/output/lib/armeabi-v7a/libavcodec.so" -a -f "ffmpeg-android-maker/output/lib/armeabi-v7a/libswscale.so" ]; then
	echo "ffmpeg already built"
else
	echo "rebuilding ffmpeg"
	./ffmpeg-android-maker/ffmpeg-android-maker.sh
fi

gradle wrapper
./gradlew assembleDebug
