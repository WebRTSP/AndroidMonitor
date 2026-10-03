#include "main.h"

#include <openssl/ssl.h>

#include <jni.h>

#include "ONVIF/onvif.nsmap"

#include "Signalling/Log.h"

#include "GStreamerHelpers.h"


static SSL_CTX* sslCtx = nullptr;

SSL_CTX* DefaultSSLContext() noexcept
{
    return sslCtx;
}

jint JNI_OnLoad(JavaVM* vm, void* /*reserved*/)
{
    void* envPtr;
    if (vm->GetEnv(&envPtr, JNI_VERSION_1_6) != JNI_OK)
        return JNI_ERR;

    sslCtx = SSL_CTX_new(TLS_client_method());
    if(!sslCtx)
        return JNI_ERR;

    InitGStreamer();

#ifndef NDEBUG
    InitWsClientLogger(spdlog::level::trace);
#endif

    return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM* vm, void* /*reversed*/)
{
    if(sslCtx)
        SSL_CTX_free(sslCtx);
}
