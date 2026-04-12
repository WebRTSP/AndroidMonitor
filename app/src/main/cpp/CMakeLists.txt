cmake_minimum_required(VERSION 3.28)

project(GStreamerPlayer)

set(HTTP_SUPPORT OFF CACHE BOOL "HTTP server is not used" FORCE)
set(WS_SERVER_SUPPORT  OFF CACHE BOOL "libwebsocket based server is not used" FORCE)
set(WS_CLIENT_SUPPORT OFF CACHE BOOL "libwebsocket based client is not used" FORCE)
set(ONVIF_SUPPORT OFF CACHE BOOL "ONVIF not used" FORCE)
add_subdirectory(WebRTSP)

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
)

add_library(${PROJECT_NAME} SHARED ${SOURCES})
target_include_directories(${PROJECT_NAME} PRIVATE
    ${GLIB_LIBS_INCLUDE_DIRS}
    ${GST_LIBS_INCLUDE_DIRS}
    ${GST_PLUGINS_INCLUDE_DIRS}
    ${CMAKE_CURRENT_SOURCE_DIR}/WebRTSP
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
    Helpers
    CxxPtr
)

#get_cmake_property(_variableNames VARIABLES)
#foreach (_variableName ${_variableNames})
#    message(STATUS "${_variableName}=${${_variableName}}")
#endforeach()
