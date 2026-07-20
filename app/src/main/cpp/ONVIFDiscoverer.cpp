#include <jni.h>

#include <memory>
#include <future>

#include "Helpers/Actor.h"

#include "JVMBridge.h"

#include "gsoap/plugin/wsddapi.h"
#include "ONVIF/SOAP.h"


namespace {

enum {
    SOAP_LISTEN_BACKLOG = 10,
    SOAP_LISTEN_TIME = 10,
};

const char* ONVIF_DISCOVERY_URL = "soap.udp://239.255.255.250:3702";

}

class ONVIFDiscoverer: public JVMBridge {
public:
    enum class State {
        Preparing = 0,
        Scanning = 1,
        Done = 2,
        Error = 3,
    };

    ONVIFDiscoverer(JNIEnv*, jobject oppositeBank) noexcept;
    ~ONVIFDiscoverer() noexcept;

private:
    void discover() noexcept;

    void onStateChanged(State) const noexcept;
    void onDiscovered(const char* urn, const char* endpoint, const char* scopes) const noexcept;

private:
    const jmethodID _onStateChangedJni;
    const jmethodID _onDiscoveredJni;

    JNIEnv* _asyncJniEnv = nullptr;

    SOAP _discoverSoap;
    std::future<void> _discovery;

    friend void wsdd_event_ProbeMatches(
        struct soap*,
        unsigned int,
        const char*,
        unsigned,
        const char*,
        const char*,
        struct wsdd__ProbeMatchesType*);
};

void wsdd_event_ProbeMatches(
    struct soap *soap,
    unsigned int InstanceId,
    const char *SequenceId,
    unsigned MessageNumber,
    const char *MessageID,
    const char *RelatesTo,
    struct wsdd__ProbeMatchesType *matches)
{
    ONVIFDiscoverer* discoverer = static_cast<ONVIFDiscoverer*>(soap->user);
    for(int i = 0; i < matches->__sizeProbeMatch; ++i) {
        const wsdd__ProbeMatchType& match = matches->ProbeMatch[i];
        if(match.XAddrs) {
            discoverer->onDiscovered(
                match.wsa5__EndpointReference.Address,
                match.XAddrs,
                match.Scopes && match.Scopes->__item ? match.Scopes->__item: nullptr);
        }
    }
}

void wsdd_event_ResolveMatches(
    struct soap *soap,
    unsigned int InstanceId,
    const char *SequenceId,
    unsigned int MessageNumber,
    const char *MessageID,
    const char *RelatesTo,
    struct wsdd__ResolveMatchType *match)
{
}

void wsdd_event_Hello(
    struct soap *soap,
    unsigned int InstanceId,
    const char *SequenceId,
    unsigned int MessageNumber,
    const char *MessageID,
    const char *RelatesTo,
    const char *EndpointReference,
    const char *Types,
    const char *Scopes,
    const char *MatchBy,
    const char *XAddrs,
    unsigned int MetadataVersion)
{
}

soap_wsdd_mode wsdd_event_Resolve(
    struct soap *soap,
    const char *MessageID,
    const char *ReplyTo,
    const char *EndpointReference,
    struct wsdd__ResolveMatchType *match)
{
    return SOAP_WSDD_ADHOC;
}

soap_wsdd_mode wsdd_event_Probe(
    struct soap *soap,
    const char *MessageID,
    const char *ReplyTo,
    const char *Types,
    const char *Scopes,
    const char *MatchBy,
    struct wsdd__ProbeMatchesType *matches)
{
    return SOAP_WSDD_ADHOC;
}

void wsdd_event_Bye(
    struct soap *soap,
    unsigned int InstanceId,
    const char *SequenceId,
    unsigned int MessageNumber,
    const char *MessageID,
    const char *RelatesTo,
    const char *EndpointReference,
    const char *Types,
    const char *Scopes,
    const char *MatchBy,
    const char *XAddrs,
    unsigned int *MetadataVersion)
{
}

ONVIFDiscoverer::ONVIFDiscoverer(
    JNIEnv* env,
    jobject oppositeBank) noexcept :
    JVMBridge(env, oppositeBank),
    _onStateChangedJni(getMethodID("onStateChangedJni", "(I)V")),
    _onDiscoveredJni(
        getMethodID(
            "onDiscoveredJni",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V")),
    _discoverSoap(SOAP_IO_UDP)
{
    _discoverSoap->user = this;

    discover();
}

ONVIFDiscoverer::~ONVIFDiscoverer() noexcept {
    // to stop possible still running discovery
    soap_close_connection(_discoverSoap);
}

void ONVIFDiscoverer::onStateChanged(State state) const noexcept {
    callVoidMethod(
         _asyncJniEnv,
        _onStateChangedJni,
        jint(static_cast<int>(state)));
}

void ONVIFDiscoverer::onDiscovered(
    const char* urn,
    const char* endpoint,
    const char* scope) const noexcept
{
    callVoidMethod(
        _asyncJniEnv,
        _onDiscoveredJni,
        _asyncJniEnv->NewStringUTF(urn ? urn : ""),
        _asyncJniEnv->NewStringUTF(endpoint),
        _asyncJniEnv->NewStringUTF(scope ? scope : ""));
}

void ONVIFDiscoverer::discover() noexcept {
    auto discover = [this] () {
        onStateChanged(State::Scanning);

        SOAP_SOCKET listenSocket = soap_bind(_discoverSoap, nullptr, 0, SOAP_LISTEN_BACKLOG);
        if (!soap_valid_socket(listenSocket)) {
            soap_print_fault(_discoverSoap, stderr);
        }

        const char* type = nullptr;
        const char* scope = nullptr; // "onvif://www.onvif.org/";

        const char* probeMessageId = soap_wsa_rand_uuid(_discoverSoap);
        const soap_status probeResult = soap_wsdd_Probe(
            _discoverSoap,
            SOAP_WSDD_ADHOC,
            SOAP_WSDD_TO_TS,
            ONVIF_DISCOVERY_URL,
            probeMessageId,
            nullptr,
            type,
            scope,
            nullptr);
        if(probeResult != SOAP_OK) {
            onStateChanged(State::Error);
            return;
        }

        if(soap_wsdd_listen(_discoverSoap, SOAP_LISTEN_TIME) != SOAP_OK) {
            onStateChanged(State::Error);
            return;
        }

        onStateChanged(State::Done);
    };

    _discovery = std::async(std::launch::async, [this, discover] () {
        javaVm()->AttachCurrentThread(&_asyncJniEnv, nullptr);
        discover();
        javaVm()->DetachCurrentThread();
    });
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_webrtsp_monitor_onvif_ONVIFDiscoverer_jniOpen(
    JNIEnv* env,
    jobject thiz)
{
    ONVIFDiscoverer* discoverer = new ONVIFDiscoverer(env, thiz);

    return reinterpret_cast<jlong>(discoverer);
}

extern "C"
JNIEXPORT void JNICALL
Java_org_webrtsp_monitor_onvif_ONVIFDiscoverer_jniClose(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle)
{
    delete reinterpret_cast<ONVIFDiscoverer*>(handle);
}
