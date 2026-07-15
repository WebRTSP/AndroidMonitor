#include <jni.h>

#include <string_view>
#include <deque>
#include <future>

#include "ONVIF/SOAP.h"
#include "CxxPtr/GlibPtr.h"
#include "Helpers/Actor.h"
#include "Signalling/WsClient.h"
#include "RtStreaming/GstRtStreaming/GstReStreamer2.h"

#include "Log.h"
#include "JVMBridge.h"
#include "main.h"
#include "AgentSession.h"
#include "ONVIFHelpers.h"


namespace {

enum
{
    MIN_RECONNECT_DELAY = 3, // seconds
    MAX_RECONNECT_DELAY = 10, // seconds
    MIN_FETCH_TRY_INTERVAL = 3, // seconds
    MAX_FETCH_TRY_INTERVAL = 5, // seconds
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

    struct Source {
        const std::string id;
        const std::string url;
        const std::string userName;
        const std::string password;
        const bool maybeOnvif;
        const std::string accessToken;
        std::chrono::steady_clock::time_point lastFetchTry;
    };

    typedef std::deque<Source> Sources;

    ReStreamer(
        const std::string_view& trustedCAs,
        const char* serverUrl,
        const char* clientId,
        const char* agentId, // optional on first connect
        const char* accessToken, // optional on first connect
        JNIEnv*,
        jobject oppositeBank) noexcept;
    ~ReStreamer() noexcept;

    void run() noexcept;

    void updateSources(Sources&& sources) noexcept;

    void onStateChanged(JNIEnv*, State) noexcept;
    void onConnected(
        JNIEnv*,
        const std::string& agentId,
        const std::string& accessToken) noexcept;

private:
    struct ActorContext;

private:
    const jmethodID _onStateChangedJni;
    const jmethodID _onCredentialsJni;

    std::shared_ptr<ActorContext> _actorContext;
    std::unique_ptr<Actor> _actor;
};

struct ReStreamer::ActorContext :
    public Actor::Context,
    public WsClient::SessionFactory,
    private GstStreamingSource::Callbacks
{
    ActorContext(
        ReStreamer* owner,
        const std::string_view& trustedCAs,
        std::string&& serverUrl,
        std::string&& clientId,
        std::string&& agentId,
        std::string&& accessToken
    ) noexcept :
        owner(owner),
        trustedCAs(trustedCAs),
        serverUrl(std::move(serverUrl)),
        sessionContext {
            .clientId = std::move(clientId),
            .agentId = std::move(agentId),
            .accessToken = std::move(accessToken)
        }
    {}

    void activate(Actor* actor, GMainContext* mainContext, GMainLoop* mainLoop) noexcept override {
        this->actor = actor;
        this->mainContext = mainContext;
        this->mainLoop = mainLoop;

        owner->javaVm()->AttachCurrentThread(&actorJniEnv, nullptr);
    }

    void deactivate() noexcept override {
        owner->javaVm()->DetachCurrentThread();
    }

    void onStateChanged(ReStreamer::State state) noexcept
        { owner->onStateChanged(actorJniEnv, state); };

    std::unique_ptr<rtsp::Session> createAgentSession(
        std::string&& clientId,
        std::string&& agentId,
        std::string&& accessToken,
        const rtsp::Session::SendRequest& sendRequest,
        const rtsp::Session::SendResponse& sendResponse) noexcept override;

    std::unique_ptr<WebRTCPeer> createPeer(const std::string& uri) noexcept;
    void onLastPeerDetached(GstStreamingSource*, const std::string& id) noexcept override;

    struct FetchContext
    {
        ReStreamer::Source source;
        SOAP soap;
    };
    void fetch() noexcept;
    void cancelFetch() noexcept;
    void fetched(const std::shared_ptr<FetchContext>& fetchContext, std::string&& fetched) noexcept;

    const std::shared_ptr<spdlog::logger> log = ReStreamerLog();

    ReStreamer *const owner;
    std::string trustedCAs;
    const std::string serverUrl;

    AgentSession::Context sessionContext;

    Actor* actor = nullptr;
    GMainContext* mainContext = nullptr;
    GMainLoop* mainLoop = nullptr;
    JNIEnv* actorJniEnv = nullptr;

    std::shared_ptr<WebRTCConfig> webRTCConfig = std::make_shared<WebRTCConfig>();
    std::unique_ptr<WsClient> wsClient;
    GSourcePtr reconnectTimeout;

    Sources sourcesToFetch;
    std::shared_ptr<FetchContext> fetchContext;
    std::future<void> fetchAsync;
    GSourcePtr reFetchTimeout;

    typedef std::map<std::string, std::unique_ptr<GstStreamingSource>> Streamers;
    Streamers streamers;
};

std::unique_ptr<WebRTCPeer>
ReStreamer::ActorContext::createPeer(const std::string& uri) noexcept
{
    auto streamerIt = streamers.find(uri);
    if(streamerIt == streamers.end()) {
        auto sourceIt = sessionContext.sources.find(uri);
        if(sourceIt != sessionContext.sources.end()) {
            streamerIt = streamers.emplace(
                sourceIt->first,
                std::make_unique<GstReStreamer2>(sourceIt->second.url, std::string())).first;
            streamerIt->second->setCallbacks(this, uri);
        }
    }

    return streamerIt != streamers.end() ? streamerIt->second->createPeer() : nullptr;
}

void ReStreamer::ActorContext::onLastPeerDetached(
    GstStreamingSource* reStreamer,
    const std::string& id) noexcept
{
    streamers.erase(id);
}

std::unique_ptr<rtsp::Session> ReStreamer::ActorContext::createAgentSession(
    std::string&& clientId,
    std::string&& agentId,
    std::string&& accessToken,
    const rtsp::Session::SendRequest& sendRequest,
    const rtsp::Session::SendResponse& sendResponse) noexcept
{
    assert(sessionContext.clientId == clientId);
    sessionContext.agentId = std::move(agentId);
    sessionContext.accessToken = std::move(accessToken);

    onStateChanged(State::Connected);
    owner->onConnected(actorJniEnv, sessionContext.agentId, sessionContext.accessToken);

    return std::make_unique<AgentSession>(
        &sessionContext,
        webRTCConfig,
        [this] (const std::string& uri) -> std::unique_ptr<WebRTCPeer> {
            return createPeer(uri);
        },
        sendRequest,
        sendResponse);
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
    _onCredentialsJni(getMethodID("onCredentialsJni", "(Ljava/lang/String;Ljava/lang/String;)V")),
    _actorContext(
        std::make_shared<ActorContext>(
            this,
            trustedCAs,
            serverUrl,
            clientId,
            agentId ? agentId : "",
            accessToken ? accessToken : "")),
    _actor(std::make_unique<Actor>(_actorContext))
{
}

ReStreamer::~ReStreamer() noexcept
{
    _actor->sendAction([this] () {
        _actorContext->cancelFetch();

        if(_actorContext->reconnectTimeout) {
            g_source_destroy(_actorContext->reconnectTimeout.get());
            _actorContext->reconnectTimeout.reset();
        }

        if(_actorContext->wsClient) {
            _actorContext->wsClient.reset();
        }
    });
}

void ReStreamer::run() noexcept
{
    _actor->postAction([
       actorContext = _actorContext.get()
    ] () mutable {
        assert(!actorContext->wsClient);
        if(actorContext->wsClient)
            return;

        WsClientConfig config {};
        if(!FillConfigFromUrl(actorContext->serverUrl, &config)) {
            actorContext->onStateChanged(State::Error);
            return;
        }

        actorContext->onStateChanged(State::Connecting);

        actorContext->wsClient = std::make_unique<WsClient>(
            std::move(actorContext->trustedCAs),
            config,
            actorContext,
            [actorContext] (WsClient& /*client*/, unsigned statusCode) {
                actorContext->onStateChanged(State::Disconnected);

                if(statusCode == rtsp::StatusCode::UNAUTHORIZED) {
                    actorContext->sessionContext.agentId.clear();
                    actorContext->sessionContext.accessToken.clear();
                }

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

                        actorContext->onStateChanged(State::Connecting);
                        actorContext->wsClient->connectAsAgent(
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

        if(actorContext->wsClient->init(actorContext->mainLoop, DefaultSSLContext())) {
            actorContext->wsClient->connectAsAgent(
                actorContext->sessionContext.clientId,
                actorContext->sessionContext.agentId,
                actorContext->sessionContext.accessToken);
        }
    });
}

void ReStreamer::ActorContext::fetch() noexcept
{
    assert(!fetchContext && !fetchAsync.valid());

    if(sourcesToFetch.empty())
        return;

    if(reFetchTimeout)
        return;

    if(std::chrono::steady_clock::now() - sourcesToFetch.front().lastFetchTry < std::chrono::seconds(MIN_FETCH_TRY_INTERVAL)) {
        const guint fetchDelay = g_random_int_range(MIN_FETCH_TRY_INTERVAL, MAX_FETCH_TRY_INTERVAL);
        log->debug("Scheduling fetch in {} seconds", fetchDelay);
        GSourcePtr timeoutSourcePtr(g_timeout_source_new_seconds(fetchDelay));
        GSource* timeoutSource = timeoutSourcePtr.get();
        g_source_set_callback(
            timeoutSource,
            [] (gpointer userData) -> gboolean {
                ActorContext* actorContext =  static_cast<ActorContext*>(userData);
                g_source_destroy(actorContext->reFetchTimeout.get());
                actorContext->reFetchTimeout.reset();
                actorContext->fetch();
                return false;
            },
            this,
            nullptr);
        g_source_attach(timeoutSource, mainContext);
        reFetchTimeout = std::move(timeoutSourcePtr);
        return;
    }

    const Source& source = sourcesToFetch.front();
    fetchContext = std::make_shared<FetchContext>(std::move(source));
    sourcesToFetch.pop_front();

    log->debug("Fetching URL for \"{}\"", fetchContext->source.url);

    fetchAsync = std::async(
        std::launch::async,
        [
            actor = actor,
            actorContext = this,
            fetchContext = fetchContext,
            source = std::move(source)
        ] () {
            std::string fetchedUrl = ONVIFFetchUrl(
                fetchContext->soap,
                fetchContext->source.url,
                fetchContext->source.userName,
                fetchContext->source.password);
            actor->postAction(
                [
                    actorContext,
                    fetchContext = std::move(fetchContext),
                    fetchedUrl = std::move(fetchedUrl)
                ] () mutable {
                    actorContext->fetched(fetchContext, std::move(fetchedUrl));
                });
        });

}

void ReStreamer::ActorContext::cancelFetch() noexcept
{
    if(reFetchTimeout) {
        g_source_destroy(reFetchTimeout.get());
        reFetchTimeout.reset();
    }

    if(!fetchContext)
        return;

    soap_close_connection(fetchContext->soap);
    fetchContext.reset();
    fetchAsync = std::future<void>();
}

void ReStreamer::ActorContext::fetched(
    const std::shared_ptr<FetchContext>& fetchContext,
    std::string&& fetched) noexcept
{
    if(this->fetchContext == fetchContext) {
        if(!fetched.empty()) {
            log->debug("Fetched \"{}\"", fetched);

            sessionContext.sources.try_emplace(
                std::move(this->fetchContext->source.id),
                std::move(fetched),
                std::move(this->fetchContext->source.accessToken));
        } else {
            log->debug("Fetch failed");
            sourcesToFetch.emplace_back(std::move(this->fetchContext->source));
        }
        this->fetchContext.reset();
        fetchAsync = std::future<void>();

        fetch();
    }
}

void ReStreamer::updateSources(Sources&& sources) noexcept
{
    _actor->postAction([
        actorContext = _actorContext.get(),
        sources = std::move(sources)
    ] () mutable {
        actorContext->cancelFetch();
        actorContext->sourcesToFetch.clear();

        AgentSession::Sources agentSources;
        for(auto& source: sources) {
            if(source.maybeOnvif) {
                actorContext->sourcesToFetch.emplace_back(source);
            } else {
                agentSources.try_emplace(
                    std::move(source.id),
                    std::move(source.url),
                    std::move(source.accessToken));
            }
        }
        actorContext->sessionContext.sources = std::move(agentSources);
        actorContext->fetch();
    });
}

void ReStreamer::onStateChanged(JNIEnv* jniEnv, State state) noexcept
{
    callVoidMethod(
        jniEnv,
        _onStateChangedJni,
        jint(static_cast<int>(state)));
}

void ReStreamer::onConnected(
    JNIEnv* jniEnv,
    const std::string& agentId,
    const std::string& accessToken) noexcept
{
    callVoidMethod(
        jniEnv,
        _onCredentialsJni,
        jniEnv->NewStringUTF(agentId.c_str()),
        jniEnv->NewStringUTF(accessToken.c_str()));
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

    ReStreamer* client = nullptr;
    if(serverUrl && clientId) {
        client = new ReStreamer(
            std::string_view(trustedCAs, trustedCAsSize),
            serverUrl,
            clientId,
            agentId,
            accessToken,
            env,
            thiz);
    }

    if(jServerUrl && serverUrl)
        env->ReleaseStringUTFChars(jServerUrl, serverUrl);
    if(jClientId && clientId)
        env->ReleaseStringUTFChars(jClientId, clientId);
    if(jAgentId && agentId)
        env->ReleaseStringUTFChars(jAgentId, agentId);
    if(jAccessToken && accessToken)
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
Java_org_webrtsp_monitor_restreamer_ReStreamer_jniRun(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle)
{
    reinterpret_cast<ReStreamer*>(handle)->run();
}

extern "C"
JNIEXPORT void JNICALL
Java_org_webrtsp_monitor_restreamer_ReStreamer_jniUpdateSources(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle,
    jobjectArray jSources)
{
    const jsize sourcesCount = env->GetArrayLength(jSources);
    if(sourcesCount == 0)
        return;

    jobject jFirstSource = env->GetObjectArrayElement(jSources, 0);
    jclass jSourceClass = env->GetObjectClass(jFirstSource);

    const jfieldID jIdFieldId = env->GetFieldID(jSourceClass, "id", "Ljava/lang/String;");
    const jfieldID jUrlFieldId = env->GetFieldID(jSourceClass, "url", "Ljava/lang/String;");
    const jfieldID jUserNameFieldId = env->GetFieldID(jSourceClass, "userName", "Ljava/lang/String;");
    const jfieldID jPasswordFieldId = env->GetFieldID(jSourceClass, "password", "Ljava/lang/String;");
    const jfieldID jMaybeOnvifFieldId = env->GetFieldID(jSourceClass, "maybeOnvif", "Z");
    const jfieldID jAccessTokenFieldId = env->GetFieldID(jSourceClass, "accessToken", "Ljava/lang/String;");

    env->DeleteLocalRef(jSourceClass);
    env->DeleteLocalRef(jFirstSource);

    ReStreamer::Sources sources;
    for(jsize i = 0; i < sourcesCount; ++i) {
        jobject jSource = env->GetObjectArrayElement(jSources, i);
        if(!jSource)
            continue;

        const jstring jId = static_cast<jstring>(env->GetObjectField(jSource, jIdFieldId));
        const jstring jUrl = static_cast<jstring>(env->GetObjectField(jSource, jUrlFieldId));
        const jstring jUserName = static_cast<jstring>(env->GetObjectField(jSource, jUserNameFieldId));
        const jstring jPassword = static_cast<jstring>(env->GetObjectField(jSource, jPasswordFieldId));
        const jboolean jMaybeOnvif = env->GetBooleanField(jSource, jMaybeOnvifFieldId);
        const jstring jAccessToken = static_cast<jstring>(env->GetObjectField(jSource, jAccessTokenFieldId));

        const char* id = jId ? env->GetStringUTFChars(jId, nullptr) : nullptr;
        const char* url = jUrl ? env->GetStringUTFChars(jUrl, nullptr) : nullptr;
        const char* userName = jUserName ? env->GetStringUTFChars(jUserName, nullptr) : nullptr;
        const char* password = jPassword ? env->GetStringUTFChars(jPassword, nullptr) : nullptr;
        const char* accessToken = jAccessToken ? env->GetStringUTFChars(jAccessToken, nullptr) : nullptr;

        if(
            id &&
            url &&
            (!jUserName || userName) &&
            (!jPassword || password) &&
            (!jAccessToken || accessToken))
        {
            sources.emplace_back(
                id,
                url,
                userName ? std::string(userName) : std::string(),
                password ? std::string(password) : std::string(),
                jMaybeOnvif,
                accessToken ? std::string(accessToken) : std::string());
        }

        if(jUrl && url)
            env->ReleaseStringUTFChars(jUrl, url);
        if(jUserName && userName)
            env->ReleaseStringUTFChars(jUserName, userName);
        if(jPassword && password)
            env->ReleaseStringUTFChars(jPassword, password);
        if(jAccessToken && accessToken)
            env->ReleaseStringUTFChars(jAccessToken, accessToken);

        if(jUrl)
            env->DeleteLocalRef(jUrl);
        if(jUserName)
            env->DeleteLocalRef(jUserName);
        if(jPassword)
            env->DeleteLocalRef(jPassword);
        if(jAccessToken)
            env->DeleteLocalRef(jAccessToken);

        env->DeleteLocalRef(jSource);
    }

    ReStreamer* client = reinterpret_cast<ReStreamer*>(handle);
    client->updateSources(std::move(sources));
}
