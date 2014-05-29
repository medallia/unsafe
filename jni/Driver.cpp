#include "Driver.h"
#include "NativeModule.h"

// Convert a java string to std::string
const std::string toString(JNIEnv* env, jstring javaString) {
    const char * utfChars = env->GetStringUTFChars(javaString, nullptr);
    const std::string result(utfChars);
    env->ReleaseStringUTFChars(javaString, utfChars);
    return result;
}


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
    std::vector<std::string> args;
    const jsize nArgs = env->GetArrayLength(compilerArgs);
    for (jsize i = 0; i < nArgs; i++) {
        args.push_back(toString(env, static_cast<jstring>(env->GetObjectArrayElement(compilerArgs, i))));
    }

    NativeModule* nativeModule = new NativeModule(
        toString(env, fileName),
        toString(env, sourceCode),
        args
    );
    const jclass nativeModuleJavaClass = env->FindClass("unsafe/NativeModule");
    const jmethodID constructor = env->GetMethodID(nativeModuleJavaClass, "<init>", "()V");
    const jobject javaNativeModule = env->NewObject(nativeModuleJavaClass, constructor);
    const jfieldID modulePtrField = env->GetFieldID(nativeModuleJavaClass, "modulePtr", "J");
    env->SetLongField(javaNativeModule, modulePtrField, (jlong)nativeModule);
    return javaNativeModule;
}

/*
 * Class:     unsafe_Driver
 * Method:    invoke
 * Signature: (Lunsafe/NativeFunction;[Ljava/lang/Object;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_unsafe_Driver_invoke
(JNIEnv * env, jclass clazz, jobject aNativeFunction, jobjectArray arguments) {
    // Find the llvm::Function*
    const jclass nativeFunctionJavaClass = env->GetObjectClass(aNativeFunction);
    const jfieldID functionPtrField = env->GetFieldID(nativeFunctionJavaClass, "functionPtr", "J");
    llvm::Function* func = (llvm::Function*) env->GetLongField(aNativeFunction, functionPtrField);

    // and the module object
    const jfieldID parentField = env->GetFieldID(nativeFunctionJavaClass, "parent", "Lunsafe/NativeModule;");
    const jobject aNativeModule = env->GetObjectField(aNativeFunction, parentField);

    if (!aNativeModule) return nullptr;
    // extract the NativeModule instance
    const jclass nativeModuleJavaClass = env->GetObjectClass(aNativeModule);
    const jfieldID modulePtrField = env->GetFieldID(nativeModuleJavaClass, "modulePtr", "J");
    NativeModule* nativeModule = (NativeModule*) env->GetLongField(aNativeModule, modulePtrField);

    const jsize nArgs = env->GetArrayLength(arguments);
    // TODO : transform args and return values
    std::vector<llvm::GenericValue> nativeArgs;
    nativeModule->runFunction(func, nativeArgs);

    return nullptr;
}

/*
 * Class:     unsafe_Driver
 * Method:    getFunctionName
 * Signature: (Lunsafe/NativeFunction;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_unsafe_Driver_getFunctionName
(JNIEnv * env, jclass clazz, jobject aNativeFunction) {
    const jclass nativeFunctionJavaClass = env->GetObjectClass(aNativeFunction);
    const jfieldID functionPtrField = env->GetFieldID(nativeFunctionJavaClass, "functionPtr", "J");
    const llvm::Function* func = (llvm::Function*) env->GetLongField(aNativeFunction, functionPtrField);
    return env->NewStringUTF(func->getName().str().c_str());
}

/*
 * Class:     unsafe_Driver
 * Method:    getFunctions
 * Signature: (Lunsafe/NativeModule;)[Lunsafe/NativeFunction;
 */
JNIEXPORT jobjectArray JNICALL Java_unsafe_Driver_getFunctions
(JNIEnv * env, jclass clazz, jobject aNativeModule) {
    // Get a reference to a NativeModule
    const jclass nativeModuleJavaClass = env->GetObjectClass(aNativeModule);
    const jfieldID modulePtrField = env->GetFieldID(nativeModuleJavaClass, "modulePtr", "J");
    const NativeModule* nativeModule = (NativeModule*) env->GetLongField(aNativeModule, modulePtrField);

    // Get all native functions
    const std::vector<llvm::Function*> nativeFunctions = nativeModule->getFunctions();

    // Wrap them in Java objects
    const jclass nativeFunctionJavaClass = env->FindClass("unsafe/NativeFunction");
    const jmethodID constructor = env->GetMethodID(nativeFunctionJavaClass, "<init>", "()V");
    const jfieldID functionPtrField = env->GetFieldID(nativeFunctionJavaClass, "functionPtr", "J");
    const jfieldID parentField = env->GetFieldID(nativeFunctionJavaClass, "parent", "Lunsafe/NativeModule;");

    jobjectArray result = env->NewObjectArray(nativeFunctions.size(), nativeFunctionJavaClass, nullptr);
    for (jsize i = 0; i < nativeFunctions.size(); ++i) {
        const jobject javaNativeFunction = env->NewObject(nativeFunctionJavaClass, constructor);
        env->SetLongField(javaNativeFunction, functionPtrField, (jlong)nativeFunctions[i]);
        env->SetObjectField(javaNativeFunction, parentField, aNativeModule);
        env->SetObjectArrayElement(result, i, javaNativeFunction);
    }

    return result;
}

#ifdef __cplusplus
}
#endif
