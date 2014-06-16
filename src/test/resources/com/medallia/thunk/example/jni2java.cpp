#include <jni.h>
#include <chrono>


extern "C"
long benchmark(JNIEnv* env, jobject self, jint n) {
    const jclass clazz = env->GetObjectClass(self);
    jmethodID method = env->GetMethodID(clazz, "benchmarked", "(J)J");
    auto start = std::chrono::system_clock::now();
    for (int i = 0; i < n; ++i) {
        env->CallLongMethod(self, method, (jlong)i);
    }
    auto end = std::chrono::system_clock::now();
    std::chrono::nanoseconds elapsed = end - start;
    return elapsed.count();
}
