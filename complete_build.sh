#!/bin/bash

###
# This script compiles the needed go libraries into Android AAR files.
# Requirements: go, gobind, gomobile
#
# ANDROID_HOME and ANDROID_NDK_HOME have to be set to the correct paths
# to use this script
# The native libraries only need to built once.
# Afterwards, you can build the app with ./gradlew assembleDebug as usual
###

cd age-encryption
gomobile bind -o ../app/libs/encrypted_writer.aar tnibler.com/cryptocam-age-encryption
cd ..
./gradlew assembleDebug
