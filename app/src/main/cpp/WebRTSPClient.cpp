#include <jni.h>

#include "Helpers/Actor.h"
#include "WebRTSP/Signalling/WsClient.h"

#include "JVMBridge.h"

namespace {

enum
{
    MIN_RECONNECT_TIMEOUT = 3, // seconds
    MAX_RECONNECT_TIMEOUT = 10, // seconds
};

}

class WebRTSPClient: public JVMBridge {
public:
    enum class State {
        Disconnected = 0,
        Connecting = 1,
        Connected = 2,
        Error = 3,
    };

    WebRTSPClient(
        const char* serverUrl,
        const char* clientId,
        const char* agentId, // optional on first connect
        const char* accessToken, // optional on first connect
        JNIEnv*,
        jobject oppositeBank) noexcept;
    ~WebRTSPClient() noexcept;

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

struct WebRTSPClient::ActorContext: public Actor::Context {
    ActorContext(JavaVM *const javaVm) noexcept : javaVm(javaVm) {}

    void activate(GMainContext* mainContext, GMainLoop* mainLoop) noexcept override {
        this->mainContext = mainContext;
        this->mainLoop = mainLoop;

        javaVm->AttachCurrentThread(&actorJniEnv, nullptr);
    }

    void deactivate() noexcept override {
        javaVm->DetachCurrentThread();
    }

    JavaVM *const javaVm;
    GMainContext* mainContext = nullptr;
    GMainLoop* mainLoop = nullptr;
    JNIEnv* actorJniEnv = nullptr;

    std::unique_ptr<WsClient> wsClient;
};

WebRTSPClient::WebRTSPClient(
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
            [] (
                const rtsp::Session::SendRequest& sendRequest,
                const rtsp::Session::SendResponse& sendResponse) -> std::unique_ptr<rtsp::Session>
            {
                return nullptr;
            },
            [] (WsClient&) {
            }
        );
    });
}

WebRTSPClient::~WebRTSPClient() noexcept
{
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_webrtsp_monitor_restreamer_WebRTSPClient_jniOpen(
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

    WebRTSPClient *const client = new WebRTSPClient(
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
Java_org_webrtsp_monitor_restreamer_WebRTSPClient_jniClose(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle)
{
    delete reinterpret_cast<WebRTSPClient*>(handle);
}
