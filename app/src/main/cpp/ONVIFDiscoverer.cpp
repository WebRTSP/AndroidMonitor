#include <jni.h>

#include <memory>

#include "Helpers/Actor.h"

#include "JVMHelpers.h"

#include "ONVIF/wsdd.nsmap"
#include "gsoap/plugin/wsddapi.h"
#include "ONVIF/SOAP.h"


namespace {

enum {
    SOAP_LISTEN_BACKLOG = 10,
    SOAP_LISTEN_TIME = 5,
};

const char* ONVIF_DISCOVERY_URL = "soap.udp://239.255.255.250:3702";
}

class ONVIFDiscoverer
{
public:
    enum class State {
        Idle = 0,
        Scanning = 1,
        Error = 2,
    };

    ONVIFDiscoverer(JNIEnv*, jobject oppositeBank) noexcept;
    void discover() noexcept;

private:
    void onDiscovered(const char* endpoint) noexcept;

private:
    JavaVM *const _javaVm;
    JNIEnv *const _jniEnv;
    const jobject _oppositeBank;
    const jmethodID _onStateChangedJni;
    const jmethodID _onDiscoveredJni;
    JNIEnv* _actorJniEnv = nullptr;
    std::shared_ptr<Actor> _actor;

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
            discoverer->onDiscovered(match.XAddrs);
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
    printf("wsdd_event_ResolveMatches tid:%s RelatesTo:%s\n", MessageID, RelatesTo);
    printMatch(*match);
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
    _javaVm(GetJavaVM(env)),
    _jniEnv(env),
    _oppositeBank(env->NewGlobalRef(oppositeBank)),
    _onStateChangedJni(GetMethodID(env, oppositeBank, "onStateChangedJni", "(I)V")),
    _onDiscoveredJni(GetMethodID(env, oppositeBank, "onDiscoveredJni", "(Ljava/lang/String;)V"))
{
    static thread_local std::weak_ptr<Actor> sharedActor;

    if(sharedActor.expired()) {
        _actor = std::make_shared<Actor>();
        sharedActor = _actor;
    } else {
        _actor = sharedActor.lock();
    }

    _actor->postAction([this] () {
        _javaVm->AttachCurrentThread(&_actorJniEnv, nullptr);
    });
}

void ONVIFDiscoverer::onDiscovered(const char* endpoint) noexcept
{
    _actorJniEnv->CallVoidMethod(
        _oppositeBank,
        _onDiscoveredJni,
        _actorJniEnv->NewStringUTF(endpoint));
}

void ONVIFDiscoverer::discover() noexcept
{
    _actor->postAction([this] () {
        SOAP listenSoap(SOAP_IO_UDP);
        // listenSoap->connect_flags |= SO_BROADCAST; ??
        listenSoap->user = this;

        SOAP_SOCKET listenSocket = soap_bind(listenSoap, nullptr, 0, SOAP_LISTEN_BACKLOG);
        if (!soap_valid_socket(listenSocket)) {
            soap_print_fault(listenSoap, stderr);
        }

        const char* type = "";
        const char* scope = nullptr; // "onvif://www.onvif.org/";

        const char* probeMessageId = soap_wsa_rand_uuid(listenSoap);
        const soap_status probeResult = soap_wsdd_Probe(
            listenSoap,
            SOAP_WSDD_ADHOC,
            SOAP_WSDD_TO_TS,
            ONVIF_DISCOVERY_URL,
            probeMessageId,
            nullptr,
            type,
            scope,
            nullptr);
        if(probeResult != SOAP_OK) {
            soap_print_fault(listenSoap, stderr);
        }

        soap_wsdd_listen(listenSoap, SOAP_LISTEN_TIME);
    });
}

extern "C"
JNIEXPORT jlong JNICALL
Java_org_webrtsp_monitor_ONVIFDiscoverer_jniOpen(JNIEnv *env, jobject thiz)
{
    ONVIFDiscoverer* discoverer = new ONVIFDiscoverer(env, thiz);

    return reinterpret_cast<jlong>(discoverer);
}

extern "C"
JNIEXPORT void JNICALL
Java_org_webrtsp_monitor_ONVIFDiscoverer_jniClose(
    JNIEnv *env,
    jobject /*thiz*/,
    jlong handle)
{
    delete reinterpret_cast<ONVIFDiscoverer*>(handle);
}

extern "C"
JNIEXPORT void JNICALL
Java_org_webrtsp_monitor_ONVIFDiscoverer_jniDiscover(
    JNIEnv *env,
    jobject /*thiz*/,
    jlong handle)
{
    ONVIFDiscoverer* discoverer = reinterpret_cast<ONVIFDiscoverer*>(handle);

    discoverer->discover();
}
