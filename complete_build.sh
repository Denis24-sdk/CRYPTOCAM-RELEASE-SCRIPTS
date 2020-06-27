#!/usr/bin/env bash
ANDROIDX_BUILD=androidx-build

git submodule init 
git submodule update

cd $ANDROIDX_BUILD
git submodule init

git submodule update --recommend-shallow --depth 1 frameworks/support

git submodule update --recommend-shallow --depth 1 prebuilts/androidx/internal
git submodule update --recommend-shallow --depth 1 prebuilts/androidx/external

git submodule update --recommend-shallow --depth 1 prebuilts/fullsdk-linux/build-tools/29.0.3
git submodule update --recommend-shallow --depth 1 prebuilts/fullsdk-linux/ndk
git submodule update --recommend-shallow --depth 1 prebuilts/fullsdk-linux/platform-tools
git submodule update --recommend-shallow --depth 1 prebuilts/fullsdk-linux/platforms/android-30
git submodule update --recommend-shallow --depth 1 prebuilts/fullsdk-linux/tools

git submodule update --recommend-shallow --depth 1 prebuilts/jdk/jdk11
git submodule update --recommend-shallow --depth 1 prebuilts/jdk/jdk8

git submodule update --recommend-shallow --depth 1 tools/external/gradle


cd ..
if [ -f "ffmpeg-android-maker/output/lib/armeabi-v7a/libavformat.so" -a -f "ffmpeg-android-maker/output/lib/armeabi-v7a/libavutil.so" -a -f "ffmpeg-android-maker/output/lib/armeabi-v7a/libavcodec.so" -a -f "ffmpeg-android-maker/output/lib/armeabi-v7a/libswscale.so" ]; then
	echo "ffmpeg already built"
else
	echo "rebuilding ffmpeg"
	./ffmpeg-android-maker/ffmpeg-android-maker.sh
fi

cd $ANDROIDX_BUILD/frameworks/support
./gradlew createArchive

cd ../..

cp out/androidx/camera/camera-core/build/outputs/aar/camera-core-release.aar out/androidx/camera/camera-camera2/build/outputs/aar/camera-camera2-release.aar ../app/libs/

cd ..

./gradlew assembleDebug
