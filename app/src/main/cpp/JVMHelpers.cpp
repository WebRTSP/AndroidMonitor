#include "JVMHelpers.h"


JavaVM* GetJavaVM(JNIEnv* env)
{
    JavaVM* javaVm = nullptr;
    env->GetJavaVM(&javaVm);
    return javaVm;
}

jmethodID GetMethodID(
    JNIEnv* env,
    jobject oppositeBank,
    const char* name,
    const char* sig)
{
    jclass oppositeBankClass = env->GetObjectClass(oppositeBank);
    jmethodID methodId = env->GetMethodID(oppositeBankClass, name, sig);
    env->DeleteLocalRef(oppositeBankClass);
    return methodId;
}
