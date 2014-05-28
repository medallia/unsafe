#include "Driver.h"

#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     unsafe_Driver
 * Method:    compileInMemory0
 * Signature: (Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)Lunsafe/NativeModule;
 */
JNIEXPORT jobject JNICALL Java_unsafe_Driver_compileInMemory0
  (JNIEnv * env, jclass clazz, jstring fileName, jstring sourceCode, jobjectArray compilerArgs) {
  return nullptr;
}

/*
 * Class:     unsafe_Driver
 * Method:    invoke
 * Signature: (Lunsafe/NativeFunction;[Ljava/lang/Object;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_unsafe_Driver_invoke
  (JNIEnv * env, jclass clazz, jobject aNativeFunction, jobjectArray arguments) {
  return nullptr;
}

/*
 * Class:     unsafe_Driver
 * Method:    getFunctionName
 * Signature: (Lunsafe/NativeFunction;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_unsafe_Driver_getFunctionName
  (JNIEnv * env, jclass clazz, jobject aNativeFunction) {
  return nullptr;
}

/*
 * Class:     unsafe_Driver
 * Method:    getFunctions
 * Signature: (Lunsafe/NativeModule;)[Lunsafe/NativeFunction;
 */
JNIEXPORT jobjectArray JNICALL Java_unsafe_Driver_getFunctions
  (JNIEnv * env, jclass clazz, jobject aNativeModule) {
  return nullptr;
}

#ifdef __cplusplus
}
#endif
