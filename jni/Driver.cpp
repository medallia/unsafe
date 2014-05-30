#include "Driver.h"
#include "NativeModule.h"

// Convert a java string to std::string
const std::string toString(JNIEnv* env, jstring javaString) {
    const char * utfChars = env->GetStringUTFChars(javaString, nullptr);
    std::string result;
    result.append(utfChars);
    env->ReleaseStringUTFChars(javaString, utfChars);
    return result;
}

// Returns a class's name
const std::string getClassName(JNIEnv* env, jclass aClass) {
    jclass classClass = env->GetObjectClass(aClass);
    jmethodID getNameMtdId = env->GetMethodID(classClass, "getName", "()Ljava/lang/String;");
    return toString(env,(jstring) env->CallObjectMethod(aClass, getNameMtdId));
}

// Throws an IllegalArgumentException
jint throwIllegalArgumentException(JNIEnv* env, std::string message ) {
    const jclass exClass = env->FindClass("java/lang/IllegalArgumentException");
    return env->ThrowNew(exClass, message.c_str());
}

jint throwIllegalArgumentException(JNIEnv* env, const llvm::Argument& argDef) {
    std::string msg;
    llvm::raw_string_ostream os(msg);
    os << "Unsupported argument type '" << *argDef.getType() << "' for argument #" << argDef.getArgNo();
    return throwIllegalArgumentException(env, os.str());
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

    
    // transform args and return values
    const jsize nArgs = env->GetArrayLength(arguments);
    std::vector<llvm::GenericValue> nativeArgs(nArgs);
    
    const llvm::Function::ArgumentListType& argTypes = func->getArgumentList();
    if (argTypes.size() != nArgs) {
        throwIllegalArgumentException(env, std::string("Expected ") + std::to_string(argTypes.size()) + " arguments");
        return nullptr;
    }

    for (const llvm::Argument& argDef : argTypes) {
        llvm::GenericValue val;
        bool set = false;
        jobject javaVal = env->GetObjectArrayElement(arguments, argDef.getArgNo());
        jclass javaValClass = javaVal ? env->GetObjectClass(javaVal) : nullptr;
        if (argDef.getType()->isIntegerTy()) {
            jmethodID longValueMtdId = javaValClass ? env->GetMethodID(javaValClass, "longValue", "()J") : nullptr;
            if (!longValueMtdId) {
                throwIllegalArgumentException(env, std::string("Could not find longValue() method for arg ") + std::to_string(argDef.getArgNo()));
                return nullptr;
            }
            jlong longVal = env->CallLongMethod(javaVal, longValueMtdId);
            val.IntVal = llvm::APInt(argDef.getType()->getPrimitiveSizeInBits(), longVal);
            set = true;
        } else if (argDef.getType()->isPointerTy()) {
            const llvm::Type* elementType = argDef.getType()->getPointerElementType();
            if (elementType->isStructTy()) {
                const llvm::StructType* structType = static_cast<const llvm::StructType*>(elementType);
                if (!structType->isLiteral()) {
                    if (structType->getName() == "class._jobject") {
                        // Just pass the JNI Java object
                        val.PointerVal = javaVal;
                        set = true;
                    } else if (structType->getName() == "struct.JNIEnv_") {
                        // Just pass the environment pointer, we don't care about the actual argument
                        val.PointerVal = env;
                        set = true;
                    } else if (structType->getName() == "class._jstring") {
                        // Check that the arg is a valid jstring
                        if (javaVal) {
                            std::string name = getClassName(env, javaValClass);
                            if(name != std::string("java.lang.String")) {
                                throwIllegalArgumentException(env,
                                                              std::string("expected a string for arg #") + std::to_string(argDef.getArgNo())
                                                              + std::string(" but got a ") + name);
                                return nullptr;
                            }
                        }
                        // Just pass the JNI Java object
                        val.PointerVal = javaVal;
                        set = true;
                    } else if (structType->getName() == "class._jobjectArray") {
                        // Check that the arg is in fact an object array of any type
                        if (javaVal) {
                            jclass classClass = env->GetObjectClass(javaValClass);
                            jmethodID isArrayMtdId = env->GetMethodID(classClass, "isArray", "()Z");
                            if(!env->CallBooleanMethod(javaValClass, isArrayMtdId)) {
                                throwIllegalArgumentException(env,
                                                              std::string("expected a string for arg #") + std::to_string(argDef.getArgNo())
                                                              + std::string(" but got a ") + getClassName(env, javaValClass));
                                return nullptr;
                            }
                        }
                        // Just pass the JNI Java object
                        val.PointerVal = javaVal;
                        set = true;
                    }
                }
            }
        }
        
        if (!set) {
            throwIllegalArgumentException(env, argDef);
            return nullptr;
        }
        
        nativeArgs[argDef.getArgNo()] = val;
    }
    
    nativeModule->runFunction(func, nativeArgs);

    return nullptr;
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
    const jfieldID nameField = env->GetFieldID(nativeFunctionJavaClass, "name", "Ljava/lang/String;");
    const jfieldID parentField = env->GetFieldID(nativeFunctionJavaClass, "parent", "Lunsafe/NativeModule;");

    jobjectArray result = env->NewObjectArray((jsize)nativeFunctions.size(), nativeFunctionJavaClass, nullptr);
    for (jsize i = 0; i < nativeFunctions.size(); ++i) {
        const jobject javaNativeFunction = env->NewObject(nativeFunctionJavaClass, constructor);
        env->SetLongField(javaNativeFunction, functionPtrField, (jlong)nativeFunctions[i]);
        env->SetObjectField(javaNativeFunction, parentField, aNativeModule);
        env->SetObjectField(javaNativeFunction, nameField, env->NewStringUTF(nativeFunctions[i]->getName().str().c_str()));
        env->SetObjectArrayElement(result, i, javaNativeFunction);
    }

    return result;
}


/*
 * Class:     unsafe_Driver
 * Method:    delete
 * Signature: (Lunsafe/NativeModule;)V
 */
JNIEXPORT void JNICALL Java_unsafe_Driver_delete
(JNIEnv * env, jclass clazz, jobject aNativeModule) {
    // Delete the native module
    const jclass nativeModuleJavaClass = env->GetObjectClass(aNativeModule);
    const jfieldID modulePtrField = env->GetFieldID(nativeModuleJavaClass, "modulePtr", "J");
    const NativeModule* nativeModule = (NativeModule*) env->GetLongField(aNativeModule, modulePtrField);
    delete nativeModule;
}

#ifdef __cplusplus
}
#endif
