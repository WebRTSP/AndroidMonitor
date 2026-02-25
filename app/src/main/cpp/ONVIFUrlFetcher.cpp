#include <jni.h>

#include <future>

#include "gsoap/plugin/wsseapi.h"
#include "ONVIF/SOAP.h"

#include "CxxPtr/GlibPtr.h"

#include "JVMBridge.h"


class ONVIFUrlFetcher: public JVMBridge {
public:
    enum class State {
        Fetching = 1,
        Done = 2,
        Error = 3,
    };

    ONVIFUrlFetcher(
        const char* endpoint,
        const char* userName,
        const char* password,
        JNIEnv*,
        jobject oppositeBank) noexcept;
    ~ONVIFUrlFetcher() noexcept;

private:
    void addAuth() noexcept;

    void fetch() noexcept;

    void onStateChanged(State) const noexcept;
    void onUrlFetched(const std::string& url) const noexcept;

private:
    const std::string _endpoint;
    const std::string _userName;
    const std::string _password;

    const jmethodID _onStateChangedJni;
    const jmethodID _onUrlFetchedJni;

    JNIEnv* _asyncJniEnv = nullptr;

    SOAP _soap;
    std::future<void> _fetch;
};

ONVIFUrlFetcher::ONVIFUrlFetcher(
    const char* endpoint,
    const char* userName,
    const char* password,
    JNIEnv* env,
    jobject oppositeBank) noexcept:
    _endpoint(endpoint),
    _userName(userName ? userName : ""),
    _password(password ? password : ""),
    JVMBridge(env, oppositeBank),
    _onStateChangedJni(getMethodID("onStateChangedJni", "(I)V")),
    _onUrlFetchedJni(getMethodID( "onUrlFetchedJni", "(Ljava/lang/String;)V"))
{
    fetch();
}

ONVIFUrlFetcher::~ONVIFUrlFetcher() noexcept {
    soap_close_connection(_soap);
}

void ONVIFUrlFetcher::addAuth() noexcept {
    if(_userName.empty() && _password.empty())
        return;

    soap_wsse_add_UsernameTokenDigest(
        _soap,
        nullptr,
        _userName.c_str(),
        _password.c_str());
}

void ONVIFUrlFetcher::onStateChanged(State state) const noexcept {
    callVoidMethod(
        _asyncJniEnv,
        _onStateChangedJni,
        jint(static_cast<int>(state)));
}

void ONVIFUrlFetcher::onUrlFetched(const std::string& url) const noexcept {
    callVoidMethod(
        _asyncJniEnv,
        _onUrlFetchedJni,
        _asyncJniEnv->NewStringUTF(url.c_str()));
}

void ONVIFUrlFetcher::fetch() noexcept {
    auto fetch = [this] () {
        onStateChanged(State::Fetching);

        if(!_userName.empty() || !_password.empty()) {
            soap_wsse_add_UsernameTokenDigest(
                _soap,
                nullptr,
                _userName.c_str(),
                _password.c_str());
        }

        soap_status status;

        _tds__GetCapabilities getCapabilities;
        tt__CapabilityCategory category = tt__CapabilityCategory::Media;
        getCapabilities.Category.push_back(category);
        _tds__GetCapabilitiesResponse getCapabilitiesResponse;
        addAuth();
        status = soap_call___tds__GetCapabilities(
            _soap,
            _endpoint.c_str(),
            nullptr,
            &getCapabilities,
            getCapabilitiesResponse);
        if(status != SOAP_OK) {
            onStateChanged(State::Error);
            return;
        }

        const std::string& mediaEndpoint = getCapabilitiesResponse.Capabilities->Media->XAddr;

        _trt__GetProfiles getProfiles;
        _trt__GetProfilesResponse getProfilesResponse;
        addAuth();
        status = soap_call___trt__GetProfiles(
            _soap,
            mediaEndpoint.c_str(),
            nullptr,
            &getProfiles,
            getProfilesResponse);
        if(status != SOAP_OK) {
            onStateChanged(State::Error);
            return;
        }

        if(getProfilesResponse.Profiles.empty()) {
            onStateChanged(State::Error);
            return;
        }

        const tt__Profile *const mediaProfile = getProfilesResponse.Profiles[0];
        _trt__GetStreamUri getStreamUri;
        _trt__GetStreamUriResponse getStreamUriResponse;
        getStreamUri.ProfileToken = mediaProfile->token;

        tt__StreamSetup streamSetup;

        tt__Transport transport;
        transport.Protocol = tt__TransportProtocol::RTSP;

        streamSetup.Transport = &transport;

        getStreamUri.StreamSetup = &streamSetup;

        addAuth();
        status = soap_call___trt__GetStreamUri(
            _soap,
            mediaEndpoint.c_str(),
            nullptr,
            &getStreamUri,
            getStreamUriResponse);
        if(status != SOAP_OK) {
            onStateChanged(State::Error);
            return;
        }

        const tt__MediaUri *const mediaUri = getStreamUriResponse.MediaUri;
        if(!mediaUri || mediaUri->Uri.empty()) {
            onStateChanged(State::Error);
            return;
        }

        GCharPtr uriStringPtr;
        if(!_userName.empty() || !_password.empty()) {
            GUriPtr uriPtr(g_uri_parse(mediaUri->Uri.c_str(), G_URI_FLAGS_ENCODED, nullptr));
            GUri* uri = uriPtr.get();
            if(!g_uri_get_user(uri) && !g_uri_get_password(uri)) {
                GCharPtr userPtr(
                    !_userName.empty() ?
                        g_uri_escape_string(
                            _userName.c_str(),
                            G_URI_RESERVED_CHARS_SUBCOMPONENT_DELIMITERS,
                            false) :
                            nullptr);
                GCharPtr passwordPtr(
                    !_password.empty() ?
                        g_uri_escape_string(
                            _password.c_str(),
                            G_URI_RESERVED_CHARS_SUBCOMPONENT_DELIMITERS,
                            false) :
                            nullptr);
                uriStringPtr.reset(
                    g_uri_join_with_user(
                        G_URI_FLAGS_ENCODED,
                        g_uri_get_scheme(uri),
                        userPtr.get(),
                        passwordPtr.get(),
                        g_uri_get_auth_params(uri),
                        g_uri_get_host(uri),
                        g_uri_get_port(uri),
                        g_uri_get_path(uri),
                        g_uri_get_query(uri),
                        g_uri_get_fragment(uri)));
            }
        }

        const std::string& mediaUriUri = uriStringPtr ?
            std::string(uriStringPtr.get()) :
            mediaUri->Uri;

        onUrlFetched(mediaUriUri);

        onStateChanged(State::Done);
    };

    _fetch = std::async(std::launch::async, [this, fetch] () {
        javaVm()->AttachCurrentThread(&_asyncJniEnv, nullptr);
        fetch();
        javaVm()->DetachCurrentThread();
    });
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_webrtsp_monitor_onvif_ONVIFUrlFetcher_jniOpen(
    JNIEnv *env,
    jobject thiz,
    jstring jEndpoint,
    jstring jUserName,
    jstring jPassword
) {
    if(!jEndpoint)
        return {};

    const char* endpoint = env->GetStringUTFChars(jEndpoint, nullptr);
    const char* userName = jUserName ? env->GetStringUTFChars(jUserName, nullptr) : nullptr;
    const char* password = jPassword ? env->GetStringUTFChars(jPassword, nullptr) : nullptr;

    ONVIFUrlFetcher* fetcher = new ONVIFUrlFetcher(
        endpoint,
        userName,
        password,
        env,
        thiz);

    if(jPassword)
        env->ReleaseStringUTFChars(jPassword, password);
    if(jUserName)
        env->ReleaseStringUTFChars(jUserName, userName);
    if(jEndpoint)
        env->ReleaseStringUTFChars(jEndpoint, endpoint);

    return reinterpret_cast<jlong>(fetcher);
}

extern "C"
JNIEXPORT void JNICALL
Java_org_webrtsp_monitor_onvif_ONVIFUrlFetcher_jniClose(
    JNIEnv *env,
    jobject /*thiz*/,
    jlong handle)
{
    delete reinterpret_cast<ONVIFUrlFetcher*>(handle);
}
