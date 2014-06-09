package com.medallia.shim.example;

import com.medallia.shim.Adapter;
import com.medallia.unsafe.Driver;
import com.medallia.unsafe.NativeFunction;
import com.medallia.unsafe.NativeModule;


public class AutoFastCall extends Adapter {
	/** Keeps a reference to native bindings for this class. */
	private static final NativeBindings BINDINGS = initializeNative(AutoFastCall.class);

	public AutoFastCall(NativeModule implementation) {
		super(BINDINGS, implementation);
	}

	/** The native method signature */
	public native int square(int anInt);

	public static void main(String[] args) {
		final NativeModule implementation = Driver.compileInMemory("#include<jni.h>\n" +
				"extern \"C\" jint square(JNIEnv* env, jobject self, jint x) { return x*x; }");
		final AutoFastCall autoFastCall = new AutoFastCall(implementation);
		long sumOfSquares = 0;
		System.out.println("--- testing slow call ---");
		final NativeFunction slowCall = implementation.getFunctionByName("square");
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
