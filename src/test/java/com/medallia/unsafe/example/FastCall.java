package com.medallia.unsafe.example;

import com.medallia.unsafe.Driver;
import com.medallia.unsafe.NativeFunction;
import com.medallia.unsafe.NativeModule;

import java.io.IOException;

/**
 * This example shows how to create a proxy to minimize call cost to native code, while
 * being able to still dynamically generate code.
 * This is about 3 orders of magnitude faster than using plain {@link com.medallia.unsafe.NativeFunction#invoke(Object...)}
 *
 * The generated code must conform to a fixed interface.
 */
public class FastCall {
	/** The pointer to the target function. This is called by native code. */
	private final long functionPtr;

	/** This is needed to prevent garbage collection of the module containing the compiled code. */
	private final NativeFunction function;

	public FastCall(NativeFunction initializer) {
		this.function = initializer;
		// Call the initializer to obtain a function pointer to the actual target
		this.functionPtr = (Long)initializer.invoke();
	}

	/**
	 * The implementation for this function will be compiled and loaded in the static initializer below.
	 * When called, it will execute the function pointed at by {@link #functionPtr}.
	 */
	public native int process(int arg);

	/** This is needed to hold a reference to the implementation of {@link #process(int)} for the duration of this class. */
	private static final NativeModule HANDLER;

	static {
		// When the class is initialized, we compile an implementation for the process() method
		// and register it with the JVM for this class. See fastCallShim.cpp.
		try {
			HANDLER = Driver.compileInMemory(Util.loadResource(FastCall.class, "fastCallShim.cpp"));
			if (HANDLER.hasErrors()) {
				System.err.println(HANDLER.getErrors());
			}
			HANDLER.getFunctionByName("registerNative").invoke("ignored");
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	public static void main(String[] args) {
		final NativeModule nativeModule = Driver.compileInMemory("#include<jni.h>\n" +
				// The actual useful function we want to run
				"extern \"C\" jint square(JNIEnv * env, jint in) { return in*in; }\n" +
				// This is a helper function to get the pointer to the 'square' function
				"extern \"C\" jlong initializer() { return (jlong) square; }");

		if (nativeModule.hasErrors()) {
			System.out.println(nativeModule.getErrors());
			return;
		}

		System.out.println("-- testing slow call ---");
		long sumOfSquares = 0;
		final NativeFunction slowCall = nativeModule.getFunctionByName("square");
		for (int i = 0; i < 10; i++) {
			long start = System.nanoTime();
			sumOfSquares += (Long) slowCall.invoke(null, i);
			long end = System.nanoTime();
			System.out.println( (end-start)/1e3 + "us" );
		}
		System.out.println("sum: " + sumOfSquares);

		System.out.println("-- testing fast call ---");
		sumOfSquares = 0;
		final FastCall fastCall = new FastCall(nativeModule.getFunctionByName("initializer"));
		for (int i = 0; i < 10; i++) {
			long start = System.nanoTime();
			sumOfSquares += fastCall.process(i);
			long end = System.nanoTime();
			System.out.println( (end-start)/1e3 + "us" );
		}
		System.out.println("sum: " + sumOfSquares);
	}
}
