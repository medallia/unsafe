package com.medallia.thunk.example;

import com.medallia.thunk.ThunkBuilder;
import com.medallia.thunk.NativeBindings;
import com.medallia.unsafe.Driver;
import com.medallia.unsafe.Native;
import com.medallia.unsafe.NativeFunction;
import com.medallia.unsafe.NativeModule;

/**
 * Example of an automatically generated thunk.
 */
public class AutoFastCall {
	/**
	 * Create native bindings for this class.
	 * Should be kept around for the lifetime of this class.
	 */
	private static final NativeBindings BINDINGS = ThunkBuilder.initializeNative(AutoFastCall.class);

	/**
	 * Holds pointers to functions needed by the native code.
	 * Must be initialized by a call to {@link com.medallia.thunk.NativeBindings#getFunctionPointers(com.medallia.unsafe.NativeModule)}
	 * passing a suitable
	 */
	@Native
	private final long[] functions;

	public AutoFastCall(NativeModule implementation) {
		functions = BINDINGS.getFunctionPointers(implementation);
	}

	/** The native method signature */
	public native int square(int anInt);

	public static void main(String[] args) throws NoSuchMethodException {
		final NativeModule implementation = Driver.compileInMemory("#include<jni.h>\n" +
				"jint square(JNIEnv* env, jobject self, jint x) { return x*x; }");
		final AutoFastCall autoFastCall = new AutoFastCall(implementation);
		long sumOfSquares = 0;
		System.out.println("--- testing slow call ---");
		final NativeFunction slowCall = implementation.getFunctionByName(ThunkBuilder.getMangledName(AutoFastCall.class.getMethod("square", int.class)));
		for (int i = 0; i < 10; i++) {
			long start = System.nanoTime();
			sumOfSquares += (Long) slowCall.invoke(null, null, i);
			long end = System.nanoTime();
			System.out.println( (end-start)/1e3 + "us" );
		}
		System.out.println("sum: " + sumOfSquares);

		System.out.println("--- testing fast call ---");
		sumOfSquares = 0;
		for (int i = 0; i < 10; i++) {
			long start = System.nanoTime();
			sumOfSquares += autoFastCall.square(i);
			long end = System.nanoTime();
			System.out.println( (end-start)/1e3 + "us" );
		}
		System.out.println("sum: " + sumOfSquares);
	}
}
