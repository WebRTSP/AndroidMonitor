#include <jni.h>

#include "GStreamerHelpers.h"


jint JNI_OnLoad(JavaVM* vm, void* /*reserved*/)
{
    void* envPtr;
    if (vm->GetEnv(&envPtr, JNI_VERSION_1_6) != JNI_OK)
        return JNI_ERR;

    InitGStreamer();

    return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM* vm, void* /*reversed*/)
{
}
