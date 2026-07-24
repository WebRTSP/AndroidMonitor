#include <jni.h>

#include <string_view>

#include "CxxPtr/GlibPtr.h"
#include "Helpers/Actor.h"
#include "WebRTSP/Signalling/WsClient.h"

#include "JVMBridge.h"
#include "AgentSession.h"


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
        const std::string_view& trustedCAs,
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
    const jmethodID _onStateChangedJni;
    const jmethodID _onRegisteredJni;

    std::shared_ptr<ActorContext> _actorContext;
    std::shared_ptr<Actor> _actor;
};

struct ReStreamer::ActorContext : public Actor::Context
{
    ActorContext(
        JavaVM *const javaVm,
        std::string&& serverUrl,
        std::string&& clientId,
        std::string&& agentId,
        std::string&& accessToken
    ) noexcept :
        javaVm(javaVm),
        serverUrl(std::move(serverUrl)),
        sessionContext {
            .clientId = std::move(clientId),
            .agentId = std::move(agentId),
            .accessToken = std::move(accessToken)
        }
    {}

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
    const std::string serverUrl;

    AgentSession::Context sessionContext;

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
    const std::string_view& trustedCAs,
    const char* serverUrl,
    const char* clientId,
    const char* agentId, // optional on first connect
    const char* accessToken, // optional on first connect
    JNIEnv* env,
    jobject oppositeBank) noexcept:
    JVMBridge(env, oppositeBank),
    _onStateChangedJni(getMethodID("onStateChangedJni", "(I)V")),
    _onRegisteredJni(getMethodID( "onRegisteredJni", "(Ljava/lang/String;Ljava/lang/String;)V")),
    _actorContext(
        std::make_shared<ActorContext>(
            javaVm(),
            serverUrl,
            clientId,
            agentId ? agentId : "",
            accessToken ? accessToken : "")),
    _actor(std::make_shared<Actor>(_actorContext))
{
    _actor->sendAction([
        trustedCAs = std::string(trustedCAs),
        actorContext = _actorContext.get()
    ] () mutable {
        WsClientConfig config {};
        if(!FillConfigFromUrl(actorContext->serverUrl, &config))
            return;

        actorContext->wsClient = std::make_unique<WsClient>(
            std::move(trustedCAs),
            config,
            actorContext->mainLoop,
            [actorContext] (
                const rtsp::Session::SendRequest& sendRequest,
                const rtsp::Session::SendResponse& sendResponse) -> std::unique_ptr<rtsp::Session>
            {
                return std::make_unique<AgentSession>(
                    &actorContext->sessionContext,
                    actorContext->webRTCConfig,
                    [actorContext] (const std::string& uri) -> std::unique_ptr<WebRTCPeer> {
                        return actorContext->createPeer(uri);
                    },
                    sendRequest,
                    sendResponse);
            },
            [actorContext] (WsClient& /*client*/) {
                if(actorContext->reconnectTimeout)
                    return;

                const guint reconnectDelay = g_random_int_range(MIN_RECONNECT_DELAY, MAX_RECONNECT_DELAY);
                GSourcePtr timeoutSourcePtr(g_timeout_source_new_seconds(reconnectDelay));
                GSource* timeoutSource = timeoutSourcePtr.get();
                g_source_set_callback(
                    timeoutSource,
                    [] (gpointer userData) -> gboolean {
                        ActorContext* actorContext =  static_cast<ActorContext*>(userData);

                        g_source_destroy(actorContext->reconnectTimeout.get());
                        actorContext->reconnectTimeout.reset();

                        actorContext->wsClient->connect(
                            actorContext->sessionContext.clientId,
                            actorContext->sessionContext.agentId,
                            actorContext->sessionContext.accessToken);

                        return false;
                    },
                    actorContext,
                    nullptr);
                g_source_attach(timeoutSource, actorContext->mainContext);
                actorContext->reconnectTimeout = std::move(timeoutSourcePtr);
            }
        );

        if(actorContext->wsClient->init()) {
            actorContext->wsClient->connect(
                actorContext->sessionContext.clientId,
                actorContext->sessionContext.agentId,
                actorContext->sessionContext.accessToken);
        }
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
    jobject jTrustedCAs,
    jstring jServerUrl,
    jstring jClientId,
    jstring jAgentId,
    jstring jAccessToken)
{
    const char* trustedCAs = static_cast<char*>(env->GetDirectBufferAddress(jTrustedCAs));
    jlong trustedCAsSize = env->GetDirectBufferCapacity(jTrustedCAs);
    if(!trustedCAs || !jServerUrl || !trustedCAsSize)
        return {};

    const char* serverUrl = env->GetStringUTFChars(jServerUrl, nullptr);
    const char* clientId = jClientId ? env->GetStringUTFChars(jClientId, nullptr) : nullptr;
    const char* agentId = jAgentId ? env->GetStringUTFChars(jAgentId, nullptr) : nullptr;
    const char* accessToken = jAccessToken ? env->GetStringUTFChars(jAccessToken, nullptr) : nullptr;

    ReStreamer *const client = new ReStreamer(
        std::string_view(trustedCAs, trustedCAsSize),
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
