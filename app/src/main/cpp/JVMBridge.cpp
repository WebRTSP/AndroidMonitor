#include "JVMBridge.h"

#include "JVMHelpers.h"


JVMBridge::JVMBridge(JNIEnv* env, jobject oppositeBank) noexcept:
    _javaVm(GetJavaVM(env)),
    _jniEnv(env),
    _oppositeBank(env->NewGlobalRef(oppositeBank))
{
}

jmethodID JVMBridge::getMethodID(const char* name, const char* sig) const noexcept {
    return GetMethodID(_jniEnv, _oppositeBank, name, sig);
}

void JVMBridge::callVoidMethod(jmethodID method, ...) const noexcept {
    va_list args;
    va_start(args, method);
    _jniEnv->CallVoidMethod(_oppositeBank, method, args);
    va_end(args);
}

void JVMBridge::callVoidMethod(JNIEnv* jniEnv, jmethodID method, ...) const noexcept {
    va_list args;
    va_start(args, method);
    jniEnv->CallVoidMethodV(_oppositeBank, method, args);
    va_end(args);
}
