cmake_minimum_required(VERSION 3.22.1)
project("motionstudio_renderer")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Native rendering engine.
add_library(${CMAKE_PROJECT_NAME} SHARED
        native_bridge.cpp
        native_compositor.cpp
        yuv_convert.cpp
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE .)

find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(${CMAKE_PROJECT_NAME}
        ${log-lib}
        ${android-lib}
        ${jnigraphics-lib}
)

target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE -O3 -ffast-math)
