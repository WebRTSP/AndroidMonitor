#include <jni.h>

#include "CxxPtr/GlibPtr.h"
#include "Helpers/Actor.h"
#include "WebRTSP/Signalling/WsClient.h"

#include "JVMBridge.h"
#include "ClientSession.h"


namespace {

enum
{
    MIN_RECONNECT_DELAY = 3, // seconds
    MAX_RECONNECT_DELAY = 10, // seconds
};

}

class ReStreamer: public JVMBridge {
public:
    enum class State {
        Disconnected = 0,
        Connecting = 1,
        Connected = 2,
        Error = 3,
    };

    ReStreamer(
        const char* serverUrl,
        const char* clientId,
        const char* agentId, // optional on first connect
        const char* accessToken, // optional on first connect
        JNIEnv*,
        jobject oppositeBank) noexcept;
    ~ReStreamer() noexcept;

private:
    struct ActorContext;

private:
    const std::string _serverUrl;
    const std::string _clientId;
    const std::string _agentId;
    const std::string _accessToken;

    const jmethodID _onStateChangedJni;
    const jmethodID _onRegisteredJni;

    std::shared_ptr<ActorContext> _actorContext;
    std::shared_ptr<Actor> _actor;
};

struct ReStreamer::ActorContext : public Actor::Context
{
    ActorContext(JavaVM *const javaVm) noexcept : javaVm(javaVm) {}

    void activate(GMainContext* mainContext, GMainLoop* mainLoop) noexcept override {
        this->mainContext = mainContext;
        this->mainLoop = mainLoop;

        javaVm->AttachCurrentThread(&actorJniEnv, nullptr);
    }

    void deactivate() noexcept override {
        javaVm->DetachCurrentThread();
    }

    std::unique_ptr<WebRTCPeer> createPeer(const std::string& uri);

    JavaVM *const javaVm;
    GMainContext* mainContext = nullptr;
    GMainLoop* mainLoop = nullptr;
    JNIEnv* actorJniEnv = nullptr;

    std::shared_ptr<WebRTCConfig> webRTCConfig = std::make_shared<WebRTCConfig>();
    std::unique_ptr<WsClient> wsClient;
    GSourcePtr reconnectTimeout;
};

std::unique_ptr<WebRTCPeer>
ReStreamer::ActorContext::createPeer(const std::string& uri)
{
    return nullptr;
}

ReStreamer::ReStreamer(
    const char* serverUrl,
    const char* clientId,
    const char* agentId, // optional on first connect
    const char* accessToken, // optional on first connect
    JNIEnv* env,
    jobject oppositeBank) noexcept:
    _serverUrl(serverUrl),
    _clientId(clientId),
    _agentId(agentId ? agentId : ""),
    _accessToken(accessToken ? accessToken : ""),
    JVMBridge(env, oppositeBank),
    _onStateChangedJni(getMethodID("onStateChangedJni", "(I)V")),
    _onRegisteredJni(getMethodID( "onRegisteredJni", "(Ljava/lang/String;Ljava/lang/String;)V")),
    _actorContext(std::make_shared<ActorContext>(javaVm())),
    _actor(std::make_shared<Actor>(_actorContext))
{
    _actor->sendAction([this] () {
        WsClientConfig config {};
        if(!FillConfigFromUrl(_serverUrl, &config))
            return;

        _actorContext->wsClient = std::make_unique<WsClient>(
            config,
            _actorContext->mainLoop,
            [actorContext = _actorContext.get()] (
                const rtsp::Session::SendRequest& sendRequest,
                const rtsp::Session::SendResponse& sendResponse) -> std::unique_ptr<rtsp::Session>
            {
                return std::make_unique<ClientSession>(
                    actorContext->webRTCConfig,
                    [actorContext] (const std::string& uri) -> std::unique_ptr<WebRTCPeer> {
                        return actorContext->createPeer(uri);
                    },
                    sendRequest,
                    sendResponse);
            },
            [this] (WsClient& client) {
                if(_actorContext->reconnectTimeout)
                    return;

                const guint reconnectDelay = g_random_int_range(MIN_RECONNECT_DELAY, MAX_RECONNECT_DELAY);
                GSourcePtr timeoutSourcePtr(g_timeout_source_new_seconds(reconnectDelay));
                GSource* timeoutSource = timeoutSourcePtr.get();
                g_source_set_callback(
                    timeoutSource,
                    [] (gpointer userData) -> gboolean {
                        static_cast<WsClient*>(userData)->connect();
                        return false;
                    },
                    &client,
                    nullptr);
                g_source_attach(timeoutSource, _actorContext->mainContext);
                _actorContext->reconnectTimeout = std::move(timeoutSourcePtr);
            }
        );

        if(_actorContext->wsClient->init())
            _actorContext->wsClient->connect();
    });
}

ReStreamer::~ReStreamer() noexcept
{
    _actor->sendAction([this] () {
        if(_actorContext->reconnectTimeout) {
            g_source_destroy(_actorContext->reconnectTimeout.get());
            _actorContext->reconnectTimeout.reset();
        }

        if(_actorContext->wsClient) {
            _actorContext->wsClient.reset();
        }
    });
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_webrtsp_monitor_restreamer_ReStreamer_jniOpen(
    JNIEnv* env,
    jobject thiz,
    jstring jServerUrl,
    jstring jClientId,
    jstring jAgentId,
    jstring jAccessToken)
{
    if(!jServerUrl)
        return {};

    const char* serverUrl = env->GetStringUTFChars(jServerUrl, nullptr);
    const char* clientId = jClientId ? env->GetStringUTFChars(jClientId, nullptr) : nullptr;
    const char* agentId = jAgentId ? env->GetStringUTFChars(jAgentId, nullptr) : nullptr;
    const char* accessToken = jAccessToken ? env->GetStringUTFChars(jAccessToken, nullptr) : nullptr;

    ReStreamer *const client = new ReStreamer(
        serverUrl,
        clientId,
        agentId,
        accessToken,
        env,
        thiz);

    if(jServerUrl)
        env->ReleaseStringUTFChars(jServerUrl, serverUrl);
    if(jClientId)
        env->ReleaseStringUTFChars(jClientId, clientId);
    if(jAgentId)
        env->ReleaseStringUTFChars(jAgentId, agentId);
    if(jAccessToken)
        env->ReleaseStringUTFChars(jAccessToken, accessToken);

    return reinterpret_cast<jlong>(client);
}

extern "C"
JNIEXPORT void JNICALL
Java_org_webrtsp_monitor_restreamer_ReStreamer_jniClose(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle)
{
    delete reinterpret_cast<ReStreamer*>(handle);
}

extern "C"
JNIEXPORT void JNICALL
Java_org_webrtsp_monitor_restreamer_ReStreamer_jniUpdateSources(
    JNIEnv *env,
    jobject /*thiz*/,
    jlong native_handle,
    jobjectArray sources)
{
    ReStreamer* client = reinterpret_cast<ReStreamer*>(client);
}
