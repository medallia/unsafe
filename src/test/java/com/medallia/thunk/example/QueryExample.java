package com.medallia.thunk.example;

import com.medallia.thunk.NativeBindings;
import com.medallia.thunk.ThunkBuilder;
import com.medallia.unsafe.Driver;
import com.medallia.unsafe.Native;
import com.medallia.unsafe.NativeModule;

public class QueryExample {

	/** Compiled query thunk */
	public static class Query {
		private static final NativeBindings BINDINGS = ThunkBuilder.initializeNative(Query.class);
		@Native private final long[] functions;

		public Query(NativeModule implementation) {
			functions = BINDINGS.getFunctionPointers(implementation);
		}

		public native long process(long[] rawData);
	}

	public static void main(String[] args) {
		final NativeModule nativeModule = Driver.compileInMemory("#include<jni.h>\n" +
				"jlong process(JNIEnv* env, jobject self, jlongArray data) {\n" +
				"jint len = env->GetArrayLength(data);\n" +
				"jlong* rawData = env->GetLongArrayElements(data, NULL);\n" +
				"jlong result = 0;\n" +
				"for (int i = 0; i < len; ++i)\n" +
				"for (int j = 0; j < len; ++j)\n" +
				"result+=rawData[i] + rawData[j];\n" +
				"env->ReleaseLongArrayElements(data, rawData, JNI_ABORT);\n" +
				"return result/len;\n" +
				"}", "-O3");
		final long[] data = new long[11000];
		for (int i = 0; i < data.length; i++) {
			data[i] = (long) (Math.random() * 10);
		}
		final Query query = new Query(nativeModule);

		for (int i = 0; i < 10; i++) {
			long start = System.nanoTime();
			final long avg = avg(data);
			long end = System.nanoTime();
			System.out.println(end - start);
			System.out.println(avg);
			start = System.nanoTime();
			final long process = query.process(data);
			end = System.nanoTime();
			System.out.println(end - start);
			System.out.println(process);
		}
	}

	static long avg(long[] array) {
		long result = 0;
		for (int i = 0; i < array.length; i++) {
			for (int j = 0; j < array.length; j++) {
				result += array[j] + array[i];
			}
		}
		return result/array.length;
	}
}
