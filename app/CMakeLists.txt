cmake_minimum_required(VERSION 3.10)
set(ffmpeg_dir ${CMAKE_SOURCE_DIR}/../ffmpeg-android-maker/output)
set(ffmpeg_libs ${ffmpeg_dir}/lib/${ANDROID_ABI})
set(CMAKE_BUILD_TYPE Debug)
include_directories(${ffmpeg_dir}/include/${ANDROID_ABI})

set(
        # List variable name
        ffmpeg_libs_names
        # Values in the list
        avutil avformat avcodec swscale)

foreach (ffmpeg_lib_name ${ffmpeg_libs_names})
    add_library(
            ${ffmpeg_lib_name}
            SHARED
            IMPORTED)
    set_target_properties(
            ${ffmpeg_lib_name}
            PROPERTIES
            IMPORTED_LOCATION
            ${ffmpeg_libs}/lib${ffmpeg_lib_name}.so)
endforeach ()

add_library(
        # Name for a library to build
        video-muxer
        # Type of a library
        SHARED
        # All cpp files to compile
        src/main/cpp/main.cpp
        src/main/cpp/CustomAVIOContext.cpp
        src/main/cpp/VideoMuxer.cpp)

target_link_libraries(
        # Library to link
        video-muxer
        # List of libraries to link against:
        # Library for writing messages in LogCat
        log
        # Library for processing Bitmap objects
        # jnigraphics
        # FFmpeg libraries
        ${ffmpeg_libs_names})