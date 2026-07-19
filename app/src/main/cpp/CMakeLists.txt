cmake_minimum_required(VERSION 3.22.1)
project("motion-cplus-engine")

add_library(
    native-renderer
    SHARED
    native_bridge.cpp
    native_compositor.cpp
    native_compositor.h
    yuv_convert.cpp
    yuv_convert.h
    video_export_engine.cpp
    video_export_engine.h
)

target_compile_features(native-renderer PRIVATE cxx_std_17)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -Wall -Wextra -Wpedantic -fPIC")

if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(native-renderer PRIVATE -mcpu=cortex-a76)
endif()

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
    native-renderer
    ${log-lib}
    ${android-lib}
    android
)
