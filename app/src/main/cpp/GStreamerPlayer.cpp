#include <cassert>

#include <jni.h>
#include <android/native_window_jni.h>

#include <gst/gst.h>
#include <gst/video/videooverlay.h>

#include "CxxPtr/GstPtr.h"
#include "Helpers/Actor.h"

#include "JVMHelpers.h"


class GStreamerPlayer
{
public:
    enum class State {
        Playing = 2,
        Eos = 3,
        Error = 4,
    };

    GStreamerPlayer(JNIEnv*, jobject oppositeBank, const std::string& url) noexcept;
    virtual ~GStreamerPlayer() noexcept;

    void attachSurface(jobject surface) noexcept;
    void detachSurface() noexcept;

    void stop() noexcept;

private:
    void onStateChanged(State state) noexcept;

    void prepare(const std::string& url) noexcept;

    void onBusMessage(GstBus* bus, GstMessage* message) noexcept;

    void onEos(bool error) noexcept;

private:
    JavaVM *const _javaVm;
    JNIEnv *const _jniEnv;
    const jobject _oppositeBank;
    const jmethodID _onStateChangedJni;
    JNIEnv* _actorJniEnv = nullptr;

    std::shared_ptr<Actor> _actor;
    GstElement* _pipeline = nullptr;
};

GStreamerPlayer::GStreamerPlayer(
    JNIEnv* env,
    jobject oppositeBank,
    const std::string& url) noexcept :
    _javaVm(GetJavaVM(env)),
    _jniEnv(env),
    _oppositeBank(env->NewGlobalRef(oppositeBank)),
    _onStateChangedJni(GetMethodID(env, oppositeBank, "onStateChangedJni", "(I)V"))
{
    static thread_local std::weak_ptr<Actor> sharedActor;

    if(sharedActor.expired()) {
        _actor = std::make_shared<Actor>();
        sharedActor = _actor;
    } else {
        _actor = sharedActor.lock();
    }

    prepare(url);
}

GStreamerPlayer::~GStreamerPlayer() noexcept
{
    stop();

    _jniEnv->DeleteGlobalRef(_oppositeBank);
}

void GStreamerPlayer::onBusMessage(GstBus* bus, GstMessage* message) noexcept
{
    switch(GST_MESSAGE_TYPE(message)) {
        case GST_MESSAGE_EOS:
            onEos(false);
            break;
        case GST_MESSAGE_ERROR: {
            gchar* debug;
            GError* error;

            gst_message_parse_error(message, &error, &debug);

            g_free(debug);
            g_error_free(error);

            onEos(true);
            break;
        }
        case GST_MESSAGE_LATENCY:
            break;
        case GST_MESSAGE_ASYNC_DONE:
            onStateChanged(State::Playing);
            break;
        default:
            break;
    }
}

// maybe be called from GStreamer thread
void GStreamerPlayer::onStateChanged(State state) noexcept
{
    _actorJniEnv->CallVoidMethod(
        _oppositeBank,
        _onStateChangedJni,
        jboolean(static_cast<int>(state)));
}

void GStreamerPlayer::onEos(bool error) noexcept
{
    onStateChanged(error ? State::Error : State::Eos);
}

void GStreamerPlayer::prepare(const std::string& url) noexcept
{
    assert(!_pipeline);

    _actor->sendAction([this, url] () {
        if(!_actorJniEnv)
            _javaVm->AttachCurrentThread(&_actorJniEnv, nullptr);

        GstElement* pipeline = gst_parse_launch(
            "uridecodebin name=decodebin ! queue name=queue ! fakesink name=fakeSink",
            nullptr);

        if(pipeline) {
            GstElement* decodebin = gst_bin_get_by_name(GST_BIN(pipeline), "decodebin");
            g_object_set(decodebin, "uri", url.c_str(), nullptr);
            gst_object_unref(decodebin);

            GstBus* bus = gst_pipeline_get_bus(GST_PIPELINE(pipeline));
            gst_bus_add_watch(
                bus,
                [] (GstBus* bus, GstMessage* message, gpointer userData) -> gboolean {
                    GStreamerPlayer* self = static_cast<GStreamerPlayer*>(userData);
                    self->onBusMessage(bus, message);
                    return TRUE;
                },
                this);
            gst_object_unref(bus);

            gst_element_set_state(pipeline, GST_STATE_PLAYING);

            _pipeline = pipeline;
        }
    });
}

namespace {

GstElementPtr GetPipeline(const GstElementPtr& element)
{
    GstElementPtr pipeline(GST_ELEMENT(gst_object_ref(element.get())));

    while(GstElement* parent = GST_ELEMENT(gst_element_get_parent(pipeline.get()))) {
        pipeline.reset(GST_ELEMENT(parent));
    }

    return pipeline;
}

GstPadProbeReturn SetWindowHandleProbe(
    GstPad* pad,
    GstPadProbeInfo*,
    gpointer userData)
{
    const ANativeWindow *const surfaceWindow = static_cast<ANativeWindow*>(userData);

    GstElementPtr queue(gst_pad_get_parent_element(pad));
    GstElementPtr pipeline = GetPipeline(queue);

    // do it here to not get pipeline in broken state if parse fails
    GstElementPtr videoSinkBin(
        GST_ELEMENT(gst_object_ref_sink(
            gst_parse_bin_from_description(
                "name=videoSinkBin glupload ! glcolorconvert ! glimagesink name=videoSink",
                true,
                nullptr))));
    if(!videoSinkBin) {
        assert(false);
        return GST_PAD_PROBE_REMOVE;
    }

    GstElementPtr fakeSink(gst_bin_get_by_name(GST_BIN(pipeline.get()), "fakeSink"));
    if(!fakeSink) {
        assert(false);
        return GST_PAD_PROBE_REMOVE;
    }

    gst_element_unlink(queue.get(), fakeSink.get());
    gst_bin_remove(GST_BIN(pipeline.get()), fakeSink.get());
    gst_element_set_state(fakeSink.get(), GST_STATE_NULL);

    GstElementPtr videoSink(gst_bin_get_by_name(GST_BIN(videoSinkBin.get()), "videoSink"));
    gst_video_overlay_set_window_handle(GST_VIDEO_OVERLAY(videoSink.get()), (guintptr)surfaceWindow);

    gst_bin_add(GST_BIN(pipeline.get()), videoSinkBin.get());
    gst_element_sync_state_with_parent(videoSinkBin.get());
    gst_element_link(queue.get(), videoSinkBin.get());

    return GST_PAD_PROBE_REMOVE;
}

GstPadProbeReturn RemoveWindowHandleProbe(
    GstPad* pad,
    GstPadProbeInfo*,
    gpointer userData)
{
    GstElementPtr queue(gst_pad_get_parent_element(pad));
    GstElementPtr pipeline = GetPipeline(queue);

    GstElementPtr videoSinkBin(gst_bin_get_by_name(GST_BIN(pipeline.get()), "videoSinkBin"));
    if(!videoSinkBin) {
        assert(false);
        return GST_PAD_PROBE_REMOVE;
    }

    gst_element_unlink(queue.get(), videoSinkBin.get());
    gst_bin_remove(GST_BIN(pipeline.get()), videoSinkBin.get());
    gst_element_set_state(videoSinkBin.get(), GST_STATE_NULL);

    GstElementPtr fakeSink(
        GST_ELEMENT(gst_object_ref_sink(
            gst_element_factory_make("fakesink", "fakeSink"))));
    gst_bin_add(GST_BIN(pipeline.get()), fakeSink.get());
    gst_element_sync_state_with_parent(fakeSink.get());
    gst_element_link(queue.get(), fakeSink.get());

    return GST_PAD_PROBE_REMOVE;
}

}

void GStreamerPlayer::attachSurface(jobject surface) noexcept
{
    assert(_pipeline);

    ANativeWindow *const surfaceWindow = ANativeWindow_fromSurface(_jniEnv, surface);
    _actor->postAction([pipeline = _pipeline, surfaceWindow] () {
        if(GstElementPtr queue = GstElementPtr(gst_bin_get_by_name(GST_BIN(pipeline), "queue"))) {
            GstPadPtr queueSrcPad(gst_element_get_static_pad(queue.get(), "src"));
            ANativeWindow_acquire(surfaceWindow),
            gst_pad_add_probe(
                queueSrcPad.get(),
                GST_PAD_PROBE_TYPE_IDLE,
                SetWindowHandleProbe,
                surfaceWindow,
                [] (void* userData) {
                    ANativeWindow_release(static_cast<ANativeWindow*>(userData));
                });
        }
        ANativeWindow_release(surfaceWindow);
    });
}

void GStreamerPlayer::detachSurface() noexcept
{
    assert(_pipeline);

    // detach from surface as soon as possible to allow release it and not get buffer related errors
    if(GstElementPtr videoSink = GstElementPtr(gst_bin_get_by_name(GST_BIN(_pipeline), "videoSink"))) {
        gst_video_overlay_set_window_handle(GST_VIDEO_OVERLAY(videoSink.get()), (guintptr)nullptr);
    }

    _actor->postAction([pipeline = _pipeline] () {
        if(GstElementPtr queue = GstElementPtr(gst_bin_get_by_name(GST_BIN(pipeline), "queue"))) {
            GstPadPtr queueSrcPad(gst_element_get_static_pad(queue.get(), "src"));
            gst_pad_add_probe(
                queueSrcPad.get(),
                GST_PAD_PROBE_TYPE_IDLE,
                RemoveWindowHandleProbe,
                nullptr,
                nullptr);
        }
    });
}

void GStreamerPlayer::stop() noexcept
{
    if(!_pipeline)
        return;

    // FIXME! by some reason postAction is not enough on app close
    _actor->sendAction([pipeline = _pipeline, javaVm = _javaVm] () {
        gst_element_set_state(pipeline, GST_STATE_NULL);

        GstBus* bus = gst_pipeline_get_bus(GST_PIPELINE(pipeline));
        gst_bus_remove_watch(bus);
        gst_object_unref(bus);

        gst_object_unref(pipeline);

        javaVm->DetachCurrentThread();
    });

    _pipeline = nullptr;
    _actorJniEnv = nullptr;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_webrtsp_monitor_GStreamerPlayer_jniOpen(
    JNIEnv* env,
    jobject thiz,
    jstring jUrl)
{
    const char* url = env->GetStringUTFChars(jUrl, nullptr);

    GStreamerPlayer* player = new GStreamerPlayer(env, thiz, url);

    env->ReleaseStringUTFChars(jUrl, url);

    return reinterpret_cast<jlong>(player);
}

extern "C"
JNIEXPORT void JNICALL
Java_org_webrtsp_monitor_GStreamerPlayer_jniClose(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle)
{
    delete reinterpret_cast<GStreamerPlayer*>(handle);
}

extern "C"
JNIEXPORT void JNICALL
Java_org_webrtsp_monitor_GStreamerPlayer_jniAttachSurface(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle,
    jobject surface)
{
    GStreamerPlayer* player = reinterpret_cast<GStreamerPlayer*>(handle);

    player->attachSurface(surface);
}

extern "C"
JNIEXPORT void JNICALL
Java_org_webrtsp_monitor_GStreamerPlayer_jniDetachSurface(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle)
{
    GStreamerPlayer* player = reinterpret_cast<GStreamerPlayer*>(handle);

    player->detachSurface();
}
