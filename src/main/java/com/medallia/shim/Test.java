package com.medallia.shim;

import com.medallia.unsafe.NativeModule;

public class Test extends Adapter {

	public Test(NativeModule implementation) {
		super(BINDINGS, implementation);
	}

	public native void foo(Test t);
	public native int[] bar(boolean a, byte b, char c, short d, int e, long f, float g, double h,  String s, Class someClass);
	public native int[] array(boolean[] a, byte[] b, char[] c, short[] d, int[] e, long[] f, float[] g, double[] h,  String s, Class someClass);

	/** Keeps a reference to the */
	private static final NativeBindings BINDINGS = initializeNative(Test.class);
}
