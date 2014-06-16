#include "Driver.h"
#include "NativeModule.h"

// Mapping from JNI types as seen by LLVM to Java types
static const std::map<std::string,std::string> LLVM_TO_JAVA_TYPES {
    { "class._jstring"      , "java.lang.String"},
    { "class._jclass"       , "java.lang.Class" },
    { "class._jbooleanArray", "[Z"              },
    { "class._jbyteArray"   , "[B"              },
    { "class._jcharArray"   , "[C"              },
    { "class._jshortArray"  , "[S"              },
    { "class._jintArray"    , "[I"              },
    { "class._jlongArray"   , "[J"              },
    { "class._jfloatArray"  , "[F"              },
    { "class._jdoubleArray" , "[D"              }
};

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
    } javaClass;

    // java.lang.Long
    struct {
        jmethodID constructor;
    } javaLong;
} IDS;

// Will be caught and changed to a java.lang.IllegalArgumentException
struct IllegalArgumentException {
    std::string message;
};

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

extern "C" {
    /*
     * Class:     com.medallia.unsafe.Driver
     * Method:    initializeNativeCode
     * Signature: ()V
     */
    JNIEXPORT void JNICALL Java_com_medallia_unsafe_Driver_initializeNativeCode
    (JNIEnv * env, jclass driverClass) {
        llvm::llvm_start_multithreaded();
        llvm::InitializeNativeTarget();

        // Lookup commonly used method and field ids.
        // Note that it is not safe to cache classes
        const jclass nativeFunction_jClass = env->FindClass("com/medallia/unsafe/NativeFunction");
        IDS.nativeFunction.constructor = env->GetMethodID(nativeFunction_jClass, "<init>", "(JLjava/lang/String;Lcom/medallia/unsafe/NativeModule;J)V");
        IDS.nativeFunction.functionPtrFldId = env->GetFieldID(nativeFunction_jClass, "functionPtr", "J");
        IDS.nativeFunction.parentFldId = env->GetFieldID(nativeFunction_jClass, "parent", "Lcom/medallia/unsafe/NativeModule;");
        
        const jclass nativeModule_jClass = env->FindClass("com/medallia/unsafe/NativeModule");
        IDS.nativeModule.constructor = env->GetMethodID(nativeModule_jClass, "<init>", "(JLjava/lang/String;)V");
        IDS.nativeModule.modulePtrFldId = env->GetFieldID(nativeModule_jClass, "modulePtr", "J");

        const jclass javaClass_jClass = env->FindClass("java/lang/Class");
        IDS.javaClass.getNameMtdId = env->GetMethodID(javaClass_jClass, "getName", "()Ljava/lang/String;");
        
        const jclass javaLong_jClass = env->FindClass("java/lang/Long");
        IDS.javaLong.constructor = env->GetMethodID(javaLong_jClass, "<init>", "(J)V");
    }
        
    /*
     * Class:     com.medallia.unsafe.Driver
     * Method:    compileInMemory0
     * Signature: (Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)Lcom/medallia/unsafe/NativeModule;
     */
    JNIEXPORT jobject JNICALL Java_com_medallia_unsafe_Driver_compileInMemory0
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
        return env->NewObject(env->FindClass("com/medallia/unsafe/NativeModule"), IDS.nativeModule.constructor,
                              (jlong) nativeModule,
                              env->NewStringUTF(nativeModule->errors.c_str()));
    }

    /*
     * Class:     com.medallia.unsafe.Driver
     * Method:    invoke
     * Signature: (Lcom/medallia/unsafe/NativeFunction;[Ljava/lang/Object;)Ljava/lang/Object;
     */
    JNIEXPORT jobject JNICALL Java_com_medallia_unsafe_Driver_invoke
    (JNIEnv * env, jclass clazz, jobject aNativeFunction, jobjectArray arguments) {
        try {
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
                throw IllegalArgumentException { std::string("Expected ") + std::to_string(argTypes.size()) + " arguments" };
            }

            for (const llvm::Argument& argDef : argTypes) {
                llvm::GenericValue val;
                bool set = false;
                jobject javaVal = env->GetObjectArrayElement(arguments, argDef.getArgNo());
                jclass javaValClass = javaVal ? env->GetObjectClass(javaVal) : nullptr;
                if (argDef.getType()->isIntegerTy()) {
                    jmethodID longValueMtdId = javaValClass ? env->GetMethodID(javaValClass, "longValue", "()J") : nullptr;
                    if (!longValueMtdId) {
                        throw IllegalArgumentException { std::string("Could not find longValue() method for arg ") + std::to_string(argDef.getArgNo()) };
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
                            } else if (LLVM_TO_JAVA_TYPES.count(structType->getName())) {
                                // Check that the arg is of the expected type
                                if (javaVal) {
                                    const std::string expectedJavaType = LLVM_TO_JAVA_TYPES.at(structType->getName());
                                    std::string name = getClassName(env, javaValClass);
                                    if(name != expectedJavaType) {
                                        throw IllegalArgumentException {
                                            std::string("expected a ") + expectedJavaType + std::string(" for arg #") + std::to_string(argDef.getArgNo())
                                            + std::string(" but got a ") + name
                                        };
                                    }
                                }
                                // Just pass the JNI Java object
                                val.PointerVal = javaVal;
                                set = true;
                            } else if (structType->getName() == "class._jobjectArray") {
                                // Check that the arg is in fact an object array of any type
                                // Java arrays are covariant, so this complicates matters a bit
                                if (javaVal) {
                                    std::string name = getClassName(env, javaValClass);
                                    // Complain if the type name is not an object array "[L" or an array of arrays "[["
                                    if(name.compare(0, 2, "[L") != 0 && name.compare(0, 2, "[[") != 0) {
                                        throw IllegalArgumentException {
                                            std::string("expected an object array for arg #") + std::to_string(argDef.getArgNo())
                                            + std::string(" but got a ") + getClassName(env, javaValClass)
                                        };
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
                    std::string msg;
                    llvm::raw_string_ostream os(msg);
                    os << "Unsupported argument type '" << *argDef.getType() << "' for argument #" << argDef.getArgNo();
                    throw IllegalArgumentException { msg };
                }
                
                nativeArgs[argDef.getArgNo()] = val;
            }
        
            llvm::GenericValue result = nativeModule->runFunction(func, nativeArgs);
            
            // Convert the return value to a suitable Java value
            if(func->getReturnType()->isIntegerTy() && result.IntVal.getNumWords() == 1) {
                return env->NewObject(env->FindClass("java/lang/Long"), IDS.javaLong.constructor, (jlong)*result.IntVal.getRawData());
            } else if (func->getReturnType()->isPointerTy()) {
                const llvm::Type* elementType = func->getReturnType()->getPointerElementType();
                if (elementType->isStructTy()) {
                    const llvm::StructType* structType = static_cast<const llvm::StructType*>(elementType);
                    if (!structType->isLiteral()) {
                        if (LLVM_TO_JAVA_TYPES.count(structType->getName())) {
                            return (jobject) result.PointerVal;
                        }
                    }
                }
            }
            
            return nullptr;
        } catch (IllegalArgumentException ex) {
            const jclass exClass = env->FindClass("java/lang/IllegalArgumentException");
            env->ThrowNew(exClass, ex.message.c_str());
            return nullptr;
        }
    }

    /*
     * Class:     com.medallia.unsafe.Driver
     * Method:    getFunctions
     * Signature: (Lcom/medallia/unsafe/NativeModule;)[Lcom/medallia/unsafe/NativeFunction;
     */
    JNIEXPORT jobjectArray JNICALL Java_com_medallia_unsafe_Driver_getFunctions
    (JNIEnv * env, jclass clazz, jobject aNativeModule) {
        // Get a reference to a NativeModule
        const NativeModule* nativeModule = (NativeModule*) env->GetLongField(aNativeModule, IDS.nativeModule.modulePtrFldId);

        // Get all native functions
        const std::vector<llvm::Function*> nativeFunctions = nativeModule->getFunctions();

        // Wrap them in Java objects
        const jclass nativeFunctionJavaClass = env->FindClass("com/medallia/unsafe/NativeFunction");

        jobjectArray result = env->NewObjectArray((jsize)nativeFunctions.size(), nativeFunctionJavaClass, nullptr);
        for (jsize i = 0; i < nativeFunctions.size(); ++i) {
            const jobject javaNativeFunction = env->NewObject(nativeFunctionJavaClass, IDS.nativeFunction.constructor,
                                                              (jlong)nativeFunctions[i],
                                                              env->NewStringUTF(nativeFunctions[i]->getName().str().c_str()),
                                                              aNativeModule,
                                                              nativeFunctions[i]->isIntrinsic() ? 0L : nativeModule->getPointerToFunction(nativeFunctions[i]));
            env->SetObjectArrayElement(result, i, javaNativeFunction);
        }

        return result;
    }


    /*
     * Class:     unsafe_Driver
     * Method:    delete
     * Signature: (Lcom/medallia/unsafe/NativeModule;)V
     */
    JNIEXPORT void JNICALL Java_com_medallia_unsafe_Driver_delete
    (JNIEnv * env, jclass clazz, jobject aNativeModule) {
        // Delete the native module
        delete (NativeModule*) env->GetLongField(aNativeModule, IDS.nativeModule.modulePtrFldId);
    }
}
