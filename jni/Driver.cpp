#include "Driver.h"
#include "NativeModule.h"

// Commonly used jmethodIDs and jfieldIDs
static struct {
    // unsafe.NativeFunction
    struct {
        jmethodID constructor;
        jfieldID functionPtrFldId;
        jfieldID parentFldId;
    } nativeFunction;

    // unsafe.NativeModule
    struct {
        jmethodID constructor;
        jfieldID modulePtrFldId;
    } nativeModule;

    // java.lang.Class
    struct {
        jmethodID getNameMtdId;
        jmethodID isArrayMtdId;
    } javaClass;

    // java.lang.Long
    struct {
        jmethodID constructor;
    } javaLong;
} IDS;

// Convert a java string to std::string
const std::string toString(JNIEnv* env, jstring javaString) {
    const char * utfChars = env->GetStringUTFChars(javaString, nullptr);
    std::string result(utfChars);
    env->ReleaseStringUTFChars(javaString, utfChars);
    return result;
}

// Returns a class's name
const std::string getClassName(JNIEnv* env, jclass aClass) {
    return toString(env,(jstring) env->CallObjectMethod(aClass, IDS.javaClass.getNameMtdId));
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
 * Method:    initializeNativeCode
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_unsafe_Driver_initializeNativeCode
(JNIEnv * env, jclass driverClass) {
    // Lookup commonly used method and field ids.
    // Note that it is not safe to cache classes
    const jclass nativeFunction_jClass = env->FindClass("unsafe/NativeFunction");
    IDS.nativeFunction.constructor = env->GetMethodID(nativeFunction_jClass, "<init>", "(JLjava/lang/String;Lunsafe/NativeModule;)V");
    IDS.nativeFunction.functionPtrFldId = env->GetFieldID(nativeFunction_jClass, "functionPtr", "J");
    IDS.nativeFunction.parentFldId = env->GetFieldID(nativeFunction_jClass, "parent", "Lunsafe/NativeModule;");
    
    const jclass nativeModule_jClass = env->FindClass("unsafe/NativeModule");
    IDS.nativeModule.constructor = env->GetMethodID(nativeModule_jClass, "<init>", "(J)V");
    IDS.nativeModule.modulePtrFldId = env->GetFieldID(nativeModule_jClass, "modulePtr", "J");

    const jclass javaClass_jClass = env->FindClass("java/lang/Class");
    IDS.javaClass.getNameMtdId = env->GetMethodID(javaClass_jClass, "getName", "()Ljava/lang/String;");
    IDS.javaClass.isArrayMtdId = env->GetMethodID(javaClass_jClass, "isArray", "()Z");
    
    const jclass javaLong_jClass = env->FindClass("java/lang/Long");
    IDS.javaLong.constructor = env->GetMethodID(javaLong_jClass, "<init>", "(J)V");
}
    
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
    
    // Create and initinalize a new unsafe.NativeModule
    return env->NewObject(env->FindClass("unsafe/NativeModule"), IDS.nativeModule.constructor, (jlong) nativeModule);
}

/*
 * Class:     unsafe_Driver
 * Method:    invoke
 * Signature: (Lunsafe/NativeFunction;[Ljava/lang/Object;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_unsafe_Driver_invoke
(JNIEnv * env, jclass clazz, jobject aNativeFunction, jobjectArray arguments) {
    // Find the llvm::Function*
    llvm::Function* func = (llvm::Function*) env->GetLongField(aNativeFunction, IDS.nativeFunction.functionPtrFldId);

    // and the module object
    const jobject aNativeModule = env->GetObjectField(aNativeFunction, IDS.nativeFunction.parentFldId);

    if (!aNativeModule) return nullptr;
    // extract the NativeModule instance
    NativeModule* nativeModule = (NativeModule*) env->GetLongField(aNativeModule, IDS.nativeModule.modulePtrFldId);

    
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
                            if(!env->CallBooleanMethod(javaValClass, IDS.javaClass.isArrayMtdId)) {
                                throwIllegalArgumentException(env,
                                                              std::string("expected an object array for arg #") + std::to_string(argDef.getArgNo())
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
    
    llvm::GenericValue result = nativeModule->runFunction(func, nativeArgs);
    
    if(func->getReturnType()->isIntegerTy() && result.IntVal.getNumWords() == 1) {
        return env->NewObject(env->FindClass("java/lang/Long"), IDS.javaLong.constructor, (jlong)*result.IntVal.getRawData());
    } else if (func->getReturnType()->isPointerTy()) {
        const llvm::Type* elementType = func->getReturnType()->getPointerElementType();
        if (elementType->isStructTy()) {
            const llvm::StructType* structType = static_cast<const llvm::StructType*>(elementType);
            if (!structType->isLiteral()) {
                if (   structType->getName() == "class._jobject"
                    || structType->getName() == "class._jstring"
                    || structType->getName() == "class._jobjectArray") {
                    return (jobject) result.PointerVal;
                }
            }
        }
    }
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
    const NativeModule* nativeModule = (NativeModule*) env->GetLongField(aNativeModule, IDS.nativeModule.modulePtrFldId);

    // Get all native functions
    const std::vector<llvm::Function*> nativeFunctions = nativeModule->getFunctions();

    // Wrap them in Java objects
    const jclass nativeFunctionJavaClass = env->FindClass("unsafe/NativeFunction");

    jobjectArray result = env->NewObjectArray((jsize)nativeFunctions.size(), nativeFunctionJavaClass, nullptr);
    for (jsize i = 0; i < nativeFunctions.size(); ++i) {
        const jobject javaNativeFunction = env->NewObject(nativeFunctionJavaClass, IDS.nativeFunction.constructor,
                                                          (jlong)nativeFunctions[i],
                                                          env->NewStringUTF(nativeFunctions[i]->getName().str().c_str()),
                                                          aNativeModule);
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
    const NativeModule* nativeModule = (NativeModule*) env->GetLongField(aNativeModule, IDS.nativeModule.modulePtrFldId);
    delete nativeModule;
}

#ifdef __cplusplus
}
#endif
