package unsafe;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Provides method to compile C/C++ code in-memory.
 */
public class Driver {
	/**
	 * Compiles the specified source code using a virtual file named "code.cpp".
	 * @param sourceCode code to be compiled
	 * @return a compiled NativeModule
	 */
	public static NativeModule compileInMemory(String sourceCode) {
		return compileInMemory(sourceCode, null);
	}

	/**
	 * Compiles the specified source code using a virtual file named {@code fileName}.
	 * It passes the {@code compilerArgs} Clang. Do not use "-g" as an argument since MCJIT
	 * does not fully support it and will likely crash.
	 * @param sourceCode code to be compiled
	 * @param compilerArgs additional arguments for Clang
	 * @return a compiled NativeModule
	 */
	public static NativeModule compileInMemory(String sourceCode, String[] compilerArgs) {
		return compileInMemory(null, sourceCode, compilerArgs);
	}
	/**
	 * Compiles the specified source code using a virtual file named {@code fileName}.
	 * It passes the {@code compilerArgs} Clang. Do not use "-g" as an argument since MCJIT
	 * does not fully support it and will likely crash.
	 * @param fileName name of the virtual file
	 * @param sourceCode code to be compiled
	 * @param compilerArgs additional arguments for Clang
	 * @return a compiled NativeModule
	 */
	public static NativeModule compileInMemory(String fileName, String sourceCode, String[] compilerArgs) {
		if (sourceCode == null) {
			throw new IllegalArgumentException("missing source code");
		}
		if (fileName == null) {
			fileName = "code.cpp";
		}
		if (compilerArgs == null) {
			compilerArgs = new String[0];
		}
		for (String compilerArg : compilerArgs) {
			if (compilerArg == null) {
				throw new IllegalArgumentException("some compiler arguments are null");
			}
		}

		return compileInMemory0(fileName, sourceCode, compilerArgs);
	}
	private static native NativeModule compileInMemory0(String fileName, String sourceCode, String[] compilerArgs);
	static native Object invoke(NativeFunction function, Object[] args);
	static native NativeFunction[] getFunctions(NativeModule nativeModule);
	static native void delete(NativeModule nativeModule);
	private static native void initializeNativeCode();

	static {
		System.loadLibrary("UnsafeDriver");
		initializeNativeCode();
	}

	public static void main(String[] args) throws IOException {
//		final String code = "#include <jni.h>\n" +
//				"extern \"C\" int foo(JNIEnv * env, jobject x, jobjectArray y, int ll) {" +
//				"jmethodID toStringId = env->GetMethodID(env->GetObjectClass(x),\"toString\", \"()Ljava/lang/String;\");" +
//				"env->CallObjectMethod(x, toStringId);" +
//				"return ll;" +
//				"}";


		final String code = "//============================================================================\n" +
				"// Name        : avx2.cpp\n" +
				"// Author      : \n" +
				"// Version     :\n" +
				"// Copyright   : Your copyright notice\n" +
				"// Description : Hello World in C, Ansi-style\n" +
				"//============================================================================\n" +
				"\n" +
				"#include <stdio.h>\n" +
				"#include <stdlib.h>\n" +
				"#include <immintrin.h>\n" +
				"#include <vector>\n" +
				"#include <functional>\n" +
				"#include <cstdlib>\n" +
				"#include <cstdio>\n" +
				"#include <iostream>\n" +
				"#include <chrono>\n" +
				"#include <ctime>\n" +
				"#include <stdint.h>\n" +
				"#include <random>\n" +
				"#include <cassert>\n" +
				"\n" +
				"using namespace std;\n" +
				"\n" +
				"static const int WARMUP = 20000;\n" +
				"\n" +
				"static const int TIMES = 500000;\n" +
				"\n" +
				"int countNormal(float * data, int len) {\n" +
				"\tint count = 0;\n" +
				"\tfor(int i = 0; i < len; ++i)\n" +
				"\t\tif (data[i] >= 0.0)\n" +
				"\t\t\tcount++;\n" +
				"\treturn count;\n" +
				"}\n" +
				"\n" +
				"int countSSE(float *data, int len) {\n" +
				"\tassert(len % 4 == 0);\n" +
				"\n" +
				"\tint loops = len / 4;\n" +
				"\n" +
				"\tconst __m128 zero = _mm_setzero_ps(); // set to [0,0,0,0]\n" +
				"\t__m128i ones = _mm_set_epi32(1,1,1,1); // set to [1,1,1,1]\n" +
				"\t__m128i counts = _mm_setzero_si128(); // accumulating counts\n" +
				"\tfor(int i = 0; i < loops; i++) {\n" +
				"\t\t__m128 vec = _mm_loadu_ps(&data[i*4]); // load data from memory\n" +
				"\t\t__m128i mask = (__m128i) _mm_cmpgt_ps(vec, zero); // create mask\n" +
				"\t\t__m128i anded = _mm_and_si128(mask, ones); // and mask with [1,1,1,1]\n" +
				"\t\tcounts = _mm_add_epi32(counts, anded); // add result to counts\n" +
				"\t}\n" +
				"\n" +
				"\t// Horizontal add counts and return\n" +
				"\t__m128i out;\n" +
				"\t_mm_store_si128(&out, counts);\n" +
				"\tint *ptr = (int *)&out;\n" +
				"\treturn ptr[0] + ptr[1] + ptr[2] + ptr[3];\n" +
				"}\n" +
				"\n" +
				"\n" +
				"// Not faster -- delay for cmp to finish.\n" +
				"int countSSEinv(float *data, int len) {\n" +
				"\tassert(len % 8 == 0);\n" +
				"\n" +
				"\tint loops = len / 4;\n" +
				"\n" +
				"\t__m128i counts = _mm_setzero_si128();\n" +
				"\tfor(int i = 0; i < loops; i+=2) {\n" +
				"\t\t{\n" +
				"\t\t\t__m128 vec = _mm_loadu_ps(&data[i*4]);\n" +
				"\t\t\t__m128i mask = (__m128i) _mm_cmpgt_ps(vec, _mm_setzero_ps());\n" +
				"\t\t\tcounts = _mm_add_epi32(counts, mask);\n" +
				"\t\t}\n" +
				"\n" +
				"\t\t{\n" +
				"\t\t\t__m128 vec = _mm_loadu_ps(&data[(i+1)*4]);\n" +
				"\t\t\t__m128i mask = (__m128i) _mm_cmpgt_ps(vec, _mm_setzero_ps());\n" +
				"\t\t\tcounts = _mm_add_epi32(counts, mask);\n" +
				"\t\t}\n" +
				"\t}\n" +
				"\t__m128i out;\n" +
				"\t_mm_store_si128(&out, counts);\n" +
				"\tint *ptr = (int *)&out;\n" +
				"\treturn -(ptr[0] + ptr[1] + ptr[2] + ptr[3]);\n" +
				"}\n" +
				"\n" +
				"int countSSEp(float *data, int len) {\n" +
				"\tassert(len % 8 == 0);\n" +
				"\n" +
				"\tint loops = len / 4;\n" +
				"\n" +
				"\t__m128i countsA = _mm_setzero_si128();\n" +
				"\t__m128i countsB = _mm_setzero_si128();\n" +
				"\tfor(int i = 0; i < loops; i+=2) {\n" +
				"\t\t\t__m128i maskA = (__m128i) _mm_cmpgt_ps(_mm_loadu_ps(&data[i*4]), _mm_setzero_ps());\n" +
				"\t\t\t__m128i maskB = (__m128i) _mm_cmpgt_ps(_mm_loadu_ps(&data[(i+1)*4]), _mm_setzero_ps());\n" +
				"\t\t\tcountsA = _mm_add_epi32(countsA, maskA);\n" +
				"\t\t\tcountsB = _mm_add_epi32(countsB, maskB);\n" +
				"\t}\n" +
				"\t__m128i out;\n" +
				"\t_mm_store_si128(&out, _mm_add_epi32(countsA, countsB));\n" +
				"\tint *ptr = (int *)&out;\n" +
				"\treturn -(ptr[0] + ptr[1] + ptr[2] + ptr[3]);\n" +
				"}\n" +
				"\n" +
				"#ifdef __AVX2__\n" +
				"int countAVX(float *data, int len) {\n" +
				"\tassert(len % 8 == 0);\n" +
				"\n" +
				"\tint loops = len / 8;\n" +
				"\n" +
				"\t__m256i countsA = _mm256_setzero_si256();\n" +
				"\t__m256i countsB = _mm256_setzero_si256();\n" +
				"\tfor(int i = 0; i < loops; i+=2) {\n" +
				"\t\t\t__m256i maskA = (__m256i) _mm256_cmp_ps(_mm256_loadu_ps(&data[i*8]), _mm256_setzero_ps(), _CMP_GE_OS);\n" +
				"\t\t\t__m256i maskB = (__m256i) _mm256_cmp_ps(_mm256_loadu_ps(&data[(i+1)*8]), _mm256_setzero_ps(), _CMP_GE_OS);\n" +
				"\t\t\tcountsA = _mm256_add_epi32(countsA, maskA);\n" +
				"\t\t\tcountsB = _mm256_add_epi32(countsB, maskB);\n" +
				"\t}\n" +
				"\t__m256i out;\n" +
				"\t_mm256_store_si256(&out, _mm256_add_epi32(countsA, countsB));\n" +
				"\tint *ptr = (int *)&out;\n" +
				"\treturn -(ptr[0] + ptr[1] + ptr[2] + ptr[3] + ptr[4] + ptr[5] + ptr[6] + ptr[7]);\n" +
				"}\n" +
				"#endif\n" +
				"\n" +
				"void benchmark(const std::string &str, function<int(float*, int)> call, vector<float> data) {\n" +
				"        long long int count = 0;\n" +
				"        for (int i = 0; i < WARMUP; ++i)\n" +
				"                count += call(data.data(), data.size());\n" +
				"\n" +
				"        auto start = std::chrono::system_clock::now();\n" +
				"\n" +
				"        for (int i = 0; i < TIMES; ++i)\n" +
				"                count += call(data.data(), data.size());\n" +
				"        auto end = std::chrono::system_clock::now();\n" +
				"        std::chrono::duration<double> elapsed_seconds = end - start;\n" +
				"\n" +
				"        cout << str << \": \" << count << \" \" << elapsed_seconds.count() << endl;\n" +
				"}\n" +
				"\n" +
				"extern \"C\" int foo(void) {\n" +
				"\tvector<float> data;\n" +
				"\n" +
				"\tstd::default_random_engine generator;\n" +
				"\tstd::uniform_real_distribution<float> distribution(-1.0, 1.0);\n" +
				"\n" +
				"\tfor(int i = 0; i < 2000; ++i)\n" +
				"\t\tdata.push_back(distribution(generator));\n" +
				"\n" +
				"\tbenchmark(\"normal\", [](float *input, int len) { return countNormal(input, len); }, data);\n" +
				"\n" +
				"\tbenchmark(\"sse\", [](float *input, int len) { return countSSE(input, len); }, data);\n" +
				"\tbenchmark(\"ssei\", [](float *input, int len) { return countSSEinv(input, len); }, data);\n" +
				"\tbenchmark(\"ssep\", [](float *input, int len) { return countSSEp(input, len); }, data);\n" +
				"\n" +
				"#ifdef __AVX2__\n" +
				"\tbenchmark(\"avx\", [](float *input, int len) { return countAVX(input, len); }, data);\n" +
				"#endif\n" +
				"\n" +
				"\treturn EXIT_SUCCESS;\n" +
				"}\n";
		final ExecutorService executorService = Executors.newFixedThreadPool(8);


		for (int i = 0; i < 1000; i++) {
			final int iter = i;
			executorService.submit(new Runnable() {
				@Override
				public void run() {
					final NativeModule nativeModule = compileInMemory(code,
							new String[] {
									"-Wall",
									"-std=c++11",
									"-I/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/../lib/c++/v1",
									"-I/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/../lib/clang/5.1/include",
									"-I/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/include",
									"-I/Library/Java/JavaVirtualMachines/jdk1.7.0_21.jdk/Contents/Home/include",
									"-I/Library/Java/JavaVirtualMachines/jdk1.7.0_21.jdk/Contents/Home/include/darwin"

							}
					);
					if (nativeModule.hasErrors()) {
						System.out.println("Errors:\n " + nativeModule.getErrors());
					}

					NativeFunction function = nativeModule.getFunctionByName("foo");

					if (function != null) {
						final long start = System.nanoTime();
						final Object result = function.invoke();
						System.out.println("result = " + result);
						final long end = System.nanoTime();
						System.out.println((end - start) / 1e3 + "us");
					}

				}
			});
		}

		System.gc();
		System.out.println("Done!");
	}
}