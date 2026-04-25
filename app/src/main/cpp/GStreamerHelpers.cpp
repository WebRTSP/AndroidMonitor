#include "GStreamerHelpers.h"

#include <android/log.h>

#include <gst/gst.h>

namespace {
    extern "C" {
        GST_PLUGIN_STATIC_DECLARE(coreelements);
        GST_PLUGIN_STATIC_DECLARE(videotestsrc);
        GST_PLUGIN_STATIC_DECLARE(opengl);
        GST_PLUGIN_STATIC_DECLARE(playback);
        GST_PLUGIN_STATIC_DECLARE(rtsp);
        GST_PLUGIN_STATIC_DECLARE(udp);
        GST_PLUGIN_STATIC_DECLARE(rtp);
        GST_PLUGIN_STATIC_DECLARE(openh264);
        GST_PLUGIN_STATIC_DECLARE(videoparsersbad);
        GST_PLUGIN_STATIC_DECLARE(rtpmanager);
    }

    void RegisterGStreamerPlugins()
    {
        GST_PLUGIN_STATIC_REGISTER(coreelements);
        GST_PLUGIN_STATIC_REGISTER(videotestsrc);
        GST_PLUGIN_STATIC_REGISTER(opengl);
        GST_PLUGIN_STATIC_REGISTER(playback);
        GST_PLUGIN_STATIC_REGISTER(rtsp);
        GST_PLUGIN_STATIC_REGISTER(udp);
        GST_PLUGIN_STATIC_REGISTER(rtp);
        GST_PLUGIN_STATIC_REGISTER(openh264);
        GST_PLUGIN_STATIC_REGISTER(videoparsersbad);
        GST_PLUGIN_STATIC_REGISTER(rtpmanager);
    }

    void LogToLogcat(
            GstDebugCategory* category,
            GstDebugLevel level,
            const gchar* /*file*/,
            const gchar* /*function*/,
            gint /*line*/,
            GObject* /*object*/,
            GstDebugMessage* message,
            gpointer /*userData*/)
    {
        android_LogPriority logPriority;
        switch(level) {
            case GST_LEVEL_NONE:
                logPriority = ANDROID_LOG_UNKNOWN;
                break;
            case GST_LEVEL_ERROR:
                logPriority = ANDROID_LOG_ERROR;
                break;
            case GST_LEVEL_WARNING:
                logPriority = ANDROID_LOG_WARN;
                break;
            case GST_LEVEL_FIXME:
            case GST_LEVEL_INFO:
                logPriority = ANDROID_LOG_INFO;
                break;
            case GST_LEVEL_DEBUG:
                logPriority = ANDROID_LOG_DEBUG;
                break;
            case GST_LEVEL_LOG:
            case GST_LEVEL_TRACE:
            case GST_LEVEL_MEMDUMP:
                logPriority = ANDROID_LOG_VERBOSE;
                break;
            default:
                logPriority = ANDROID_LOG_UNKNOWN;
                break;
        }

        __android_log_print(
                logPriority,
                gst_debug_category_get_name(category),
                "%s\n",
                gst_debug_message_get(message));
    }
}

void InitGStreamer()
{
    if(gst_is_initialized())
        return;

    gst_debug_remove_log_function(gst_debug_log_default);
    gst_debug_add_log_function(LogToLogcat, nullptr, nullptr);
    gst_debug_set_default_threshold(GST_LEVEL_WARNING);
    gst_debug_set_active(TRUE);

    gst_init(nullptr, nullptr);

    RegisterGStreamerPlugins();
}
