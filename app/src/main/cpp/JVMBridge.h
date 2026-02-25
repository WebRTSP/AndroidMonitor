#pragma once

#include <jni.h>


class JVMBridge {
public:
    JVMBridge(JNIEnv*, jobject oppositeBank) noexcept;

    JavaVM* javaVm() const noexcept { return _javaVm; };
    JNIEnv* jniEnv() const noexcept { return _jniEnv; };

    jmethodID getMethodID(const char* name, const char* sig) const noexcept;

    void callVoidMethod(jmethodID, ...) const noexcept;
    void callVoidMethod(JNIEnv*, jmethodID, ...) const noexcept;

private:
    JavaVM *const _javaVm;
    JNIEnv *const _jniEnv;
    const jobject _oppositeBank;
};
