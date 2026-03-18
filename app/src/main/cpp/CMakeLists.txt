cmake_minimum_required(VERSION 3.28)

project(GStreamerPlayer)

if(NOT DEFINED GSTREAMER_ANDROID_ROOT)
    message(FATAL_ERROR "GSTREAMER_ANDROID_ROOT is not defined")
endif()

if(${CMAKE_ANDROID_ARCH_ABI} STREQUAL "x86" OR ${CMAKE_ANDROID_ARCH_ABI} STREQUAL "x86_64")
    set(GSTREAMER_ANDROID_ARCH ${CMAKE_ANDROID_ARCH_ABI})
elseif(${CMAKE_ANDROID_ARCH_ABI} STREQUAL "arm64-v8a")
    set(GSTREAMER_ANDROID_ARCH arm64)
elseif(${CMAKE_ANDROID_ARCH_ABI} STREQUAL "armeabi-v7a")
    set(GSTREAMER_ANDROID_ARCH armv7)
endif()

set(GSTREAMER_ANDROID_ROOT ${GSTREAMER_ANDROID_ROOT}/${GSTREAMER_ANDROID_ARCH})

set(GSTREAMER_PKG_PATHS
    "${GSTREAMER_ANDROID_ROOT}/lib/pkgconfig"
    "${GSTREAMER_ANDROID_ROOT}/lib/gstreamer-1.0/pkgconfig"
)
cmake_path(CONVERT "${GSTREAMER_PKG_PATHS}" TO_NATIVE_PATH_LIST GSTREAMER_PKG_PATHS)
set(ENV{PKG_CONFIG_PATH} "${GSTREAMER_PKG_PATHS}")

find_package(PkgConfig REQUIRED)

pkg_check_modules(GLIB_LIBS REQUIRED
    glib-2.0
)
pkg_check_modules(GST_LIBS REQUIRED
    gstreamer-1.0
)
pkg_check_modules(GST_PLUGINS REQUIRED
    gstcoreelements
    gstvideotestsrc
    gstopengl
    gstplayback
    gstrtsp
    gstudp
    gstrtp
    gstopenh264
    gstvideoparsersbad
    gstrtpmanager
)

set(SOURCES
    GStreamerPlayer.cpp
    Actor.h
    Actor.cpp
    EventSource.h
    EventSource.cpp
)

add_library(${PROJECT_NAME} SHARED ${SOURCES})
target_include_directories(${PROJECT_NAME} PRIVATE
    ${GLIB_LIBS_INCLUDE_DIRS}
    ${GST_LIBS_INCLUDE_DIRS}
    ${GST_PLUGINS_INCLUDE_DIRS}
)
target_link_directories(${PROJECT_NAME} PRIVATE
    ${GLIB_LIBS_LIBRARY_DIRS}
    ${GST_LIBS_LIBRARY_DIRS}
    ${GST_PLUGINS_LIBRARY_DIRS}
)
target_link_libraries(${PROJECT_NAME} PRIVATE
    ${GLIB_LIBS_STATIC_LIBRARIES}
    ${GST_LIBS_STATIC_LIBRARIES}
    ${GST_PLUGINS_STATIC_LIBRARIES}
    android
    log
)

#get_cmake_property(_variableNames VARIABLES)
#foreach (_variableName ${_variableNames})
#    message(STATUS "${_variableName}=${${_variableName}}")
#endforeach()
