#include <jni.h>


static jfieldID functionPtrField;

// Function that handles the native call
JNIEXPORT jint JNICALL handler(JNIEnv *env, jobject self, jint in) {
    // Get's the function pointer from the object, casts and issues the call.
    return ((jint(*)(JNIEnv *, jint))env->GetLongField(self, functionPtrField))(env, in);
}

extern "C"
void registerNative(JNIEnv* env) {
    const jclass fastCallClass = env->FindClass("unsafe/example/FastCall");

    // Lookup and store the field id
    functionPtrField = env->GetFieldID(fastCallClass, "functionPtr", "J");

    // Register the JNI method handler
    JNINativeMethod method = { (char*)"process", (char*)"(I)I", (void*)handler };
    env->RegisterNatives(fastCallClass, &method, 1);
}