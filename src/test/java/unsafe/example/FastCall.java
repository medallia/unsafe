package unsafe.example;

import unsafe.Driver;
import unsafe.NativeFunction;
import unsafe.NativeModule;

import java.io.IOException;

public class FastCall {
	/** This is called by native code */
	private final long functionPtr;

	/** This is needed to prevent garbage collection of the module containing the compiled code */
	private final NativeFunction function;

	public FastCall(NativeFunction initializer) {
		this.function = initializer;
		this.functionPtr = (Long)initializer.invoke();
	}


	public native int process(int arg);

	/** This is needed to hold a reference to the compiled module for the duration of this class. */
	private static final NativeModule HANDLER;
	static {
		try {
			HANDLER = Driver.compileInMemory(Util.loadResource("fastCallShim.cpp"));
			if (HANDLER.hasErrors()) {
				System.err.println(HANDLER.getErrors());
			}
			HANDLER.getFunctionByName("registerNative").invoke("ignored");
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	public static void main(String[] args) {
		System.out.println("args = " + args);
		final NativeModule nativeModule = Driver.compileInMemory("#include<jni.h>\n" +
				// Teh actual useful function we want to run
				"extern \"C\" jint square(JNIEnv * env, jint in) { return in*in; }\n" +
				// This is a helper function to get the pointer to the 'square' function
				"extern \"C\" jlong initializer() { return (jlong) square; }");

		if (nativeModule.hasErrors()) {
			System.out.println(nativeModule.getErrors());
			return;
		}
		System.out.println("nativeModule = " + nativeModule);

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

		System.out.println("-- testing fast call");
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
