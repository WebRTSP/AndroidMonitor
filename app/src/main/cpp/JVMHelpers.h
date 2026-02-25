#pragma once

#include <jni.h>

JavaVM* GetJavaVM(JNIEnv* env);

jmethodID GetMethodID(
    JNIEnv* env,
    jobject oppositeBank,
    const char* name,
    const char* sig);
