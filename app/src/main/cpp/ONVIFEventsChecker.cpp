#include <jni.h>

#include <future>

#include "gsoap/plugin/wsseapi.h"
#include "ONVIF/SOAP.h"

#include "CxxPtr/GlibPtr.h"

#include "JVMBridge.h"

namespace {
const char *const PullSubscriptionDuration = "PT1M"; // 1 minute, relative
const char *const PullMessagesTimeout = "PT5S"; // 5 seconds
const int PullMessagesLimit = 50;
constexpr std::chrono::seconds PullSubscriptionRefreshInterval = std::chrono::seconds(15);
}

class ONVIFEventsChecker: public JVMBridge {
public:
    enum class State {
        Idle = 0,
        Preparing = 1,
        Checking = 2,
        Error = 3,
    };

    ONVIFEventsChecker(
        const char* endpoint,
        const char* userName,
        const char* password,
        JNIEnv*,
        jobject oppositeBank) noexcept;
    ~ONVIFEventsChecker() noexcept;

    void checkEvents() noexcept;

private:
    void addAuth() noexcept;

    void onStateChanged(JNIEnv*, State) const noexcept;
    void onMotionDetected() const noexcept;

private:
    const std::string _endpoint;
    const std::string _userName;
    const std::string _password;

    const jmethodID _onStateChangedJni;
    const jmethodID _onMotionDetectedJni;

    JNIEnv* _asyncJniEnv = nullptr;

    SOAP _soap;
    std::future<void> _fetch;

    std::string _eventSubscriptionEndpoint; // not thread safe, to use only in motionEventRequestTask
    std::chrono::steady_clock::time_point _eventSubscriptionTime; // ^^^ the same ^^^
};

ONVIFEventsChecker::ONVIFEventsChecker(
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
    _onMotionDetectedJni(getMethodID( "onMotionDetectedJni", "()V"))
{
}

ONVIFEventsChecker::~ONVIFEventsChecker() noexcept {
    soap_close_connection(_soap);
}

void ONVIFEventsChecker::addAuth() noexcept {
    if(_userName.empty() && _password.empty())
        return;

    soap_wsse_add_UsernameTokenDigest(
        _soap,
        nullptr,
        _userName.c_str(),
        _password.c_str());
}

void ONVIFEventsChecker::onStateChanged(JNIEnv* jniEnv, State state) const noexcept {
    callVoidMethod(
        jniEnv,
        _onStateChangedJni,
        jint(static_cast<int>(state)));
}

void ONVIFEventsChecker::onMotionDetected() const noexcept {
    callVoidMethod(
        _asyncJniEnv,
        _onMotionDetectedJni);
}

void ONVIFEventsChecker::checkEvents() noexcept {
    if(_fetch.valid())
        _fetch.wait();

    onStateChanged(jniEnv(), State::Preparing);

    auto check = [this] () {
        onStateChanged(_asyncJniEnv, State::Checking);

        soap_status status;

        _tds__GetCapabilities getCapabilities;
        tt__CapabilityCategory category = tt__CapabilityCategory::Events;
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
            onStateChanged(_asyncJniEnv, State::Error);
            return;
        }

        const std::string& eventsEndpoint = getCapabilitiesResponse.Capabilities->Events->XAddr;

        bool renewRequired = true;
        if(_eventSubscriptionEndpoint.empty()) {
            _tev__CreatePullPointSubscription ceatePullPointSubscription;
            std::string InitialTerminationTime = PullSubscriptionDuration;
            ceatePullPointSubscription.InitialTerminationTime = &InitialTerminationTime;
            _tev__CreatePullPointSubscriptionResponse createPullPointSubscriptionResponse;
            addAuth();
            status = soap_call___tev__CreatePullPointSubscription(
                _soap,
                eventsEndpoint.c_str(),
                nullptr,
                &ceatePullPointSubscription,
                createPullPointSubscriptionResponse);
            if(status != SOAP_OK) {
                onStateChanged(_asyncJniEnv, State::Error);
                return;
            }

            _eventSubscriptionEndpoint = createPullPointSubscriptionResponse.SubscriptionReference.Address;
            _eventSubscriptionTime = std::chrono::steady_clock::now();
            renewRequired = false;
        } else {
            const auto timeElapsed = std::chrono::steady_clock::now() - _eventSubscriptionTime;
            renewRequired = timeElapsed > PullSubscriptionRefreshInterval;
        }

        if(renewRequired) {
            _wsnt__Renew renew;
            std::string TerminationTime = PullSubscriptionDuration;
            renew.TerminationTime = &TerminationTime;
            _wsnt__RenewResponse renewResponse;
            addAuth();
            status = soap_call___tev__Renew(
                _soap,
                _eventSubscriptionEndpoint.c_str(),
                nullptr,
                &renew,
                renewResponse);
            if(status != SOAP_OK) {
                _eventSubscriptionEndpoint.clear();
                onStateChanged(_asyncJniEnv, State::Error);
                return;
            }

            _eventSubscriptionTime = std::chrono::steady_clock::now();
        }

        _tev__PullMessages pullMessages;
        pullMessages.Timeout = PullMessagesTimeout;
        pullMessages.MessageLimit = PullMessagesLimit;
        _tev__PullMessagesResponse pullMessagesResponse;
        addAuth();
        status = soap_call___tev__PullMessages(
            _soap,
            _eventSubscriptionEndpoint.c_str(),
            nullptr,
            &pullMessages,
            pullMessagesResponse);
        if(status != SOAP_OK) {
            const char* faultString = soap_fault_string(_soap);
            const char* faultDetail = soap_fault_detail(_soap);
            auto error = _soap->error;
            onStateChanged(_asyncJniEnv, State::Error);
            return;
        }

        bool stopMessageProcessing = false;
        for(const wsnt__NotificationMessageHolderType* messageHolder: pullMessagesResponse.wsnt__NotificationMessage) {
            const soap_dom_element& message = messageHolder->Message.__any;
            soap_dom_element* data = message.elt_get("tt:Data");
            if(!data) {
                onStateChanged(_asyncJniEnv, State::Error);
                return;
            }

            soap_dom_element* simpleItem = data->elt_get("tt:SimpleItem");
            for(;simpleItem; simpleItem = simpleItem->get_next()) {
                const soap_dom_attribute* name = simpleItem->att_get("Name");
                if(!name || !name->get_text()) continue;
                if(name->get_text() != std::string("IsMotion")) continue;

                const soap_dom_attribute* value = simpleItem->att_get("Value");
                if(!value || !value->get_text()) continue;

                if(value->is_true()) {
                    onMotionDetected();
                    stopMessageProcessing = true;
                    break;
                }
            }

            if(stopMessageProcessing)
                break;
        }

        onStateChanged(_asyncJniEnv, State::Idle);
    };

    _fetch = std::async(std::launch::async, [this, check] () {
        javaVm()->AttachCurrentThread(&_asyncJniEnv, nullptr);
        check();
        javaVm()->DetachCurrentThread();
    });
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_webrtsp_monitor_onvif_ONVIFEventsChecker_jniOpen(
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

    ONVIFEventsChecker* checker = new ONVIFEventsChecker(
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

    return reinterpret_cast<jlong>(checker);
}

extern "C"
JNIEXPORT void JNICALL
Java_org_webrtsp_monitor_onvif_ONVIFEventsChecker_jniClose(
    JNIEnv *env,
    jobject /*thiz*/,
    jlong handle)
{
    delete reinterpret_cast<ONVIFEventsChecker*>(handle);
}

extern "C"
JNIEXPORT void JNICALL
Java_org_webrtsp_monitor_onvif_ONVIFEventsChecker_jniCheckEvents(
    JNIEnv *env,
    jobject /*thiz*/,
    jlong handle)
{
    ONVIFEventsChecker* checker = reinterpret_cast<ONVIFEventsChecker*>(handle);
    checker->checkEvents();
}
