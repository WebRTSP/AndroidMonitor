#include <jni.h>

#include <future>

#include "gsoap/plugin/wsseapi.h"
#include "ONVIF/SOAP.h"

#include "CxxPtr/GlibPtr.h"

#include "JVMBridge.h"
#include "ONVIFHelpers.h"


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
    _fetch = std::async(std::launch::async, [this] () {
        javaVm()->AttachCurrentThread(&_asyncJniEnv, nullptr);

        onStateChanged(State::Fetching);
        const std::string& url = ONVIFFetchUrl(_soap, _endpoint, _userName, _password);
        if(!url.empty()) {
            onUrlFetched(url);
            onStateChanged(State::Done);
        } else {
            onStateChanged(State::Error);
        }

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

    ONVIFUrlFetcher* fetcher = nullptr;
    if(endpoint && (!jUserName || userName) && (!jPassword || password)) {
        fetcher = new ONVIFUrlFetcher(
            endpoint,
            userName,
            password,
            env,
            thiz);
    }

    if(jPassword && password)
        env->ReleaseStringUTFChars(jPassword, password);
    if(jUserName && userName)
        env->ReleaseStringUTFChars(jUserName, userName);
    if(jEndpoint && endpoint)
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
