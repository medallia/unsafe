package unsafe;

public class Driver {
	/**
	 * When passed as an argument to a {@link unsafe.NativeFunction#invoke(Object...)}
	 * it will be replaced by the current JNI environment pointer.
	 * This allows compiled code to call Java objects.
	 */
	public static final Object JNI_ENV = new Object();

	/**
	 * Compiles the specified source code using a virtual file named {@code fileName}.
	 * It passes the {@code compilerArgs} Clang. Do not use "-g" as an argument since MCJIT
	 * does not fully support it and will likely crash.
	 * @param fileName name of the virtual file
	 * @param sourceCode code to be compiled
	 * @param compilerArgs additional arguments for Clang
	 * @return a compiled NativeModule
	 */
	private static NativeModule compileInMemory(String fileName, String sourceCode, String[] compilerArgs) {
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
	static native String getFunctionName(NativeFunction function);
	static native NativeFunction[] getFunctions(NativeModule nativeModule);

	static {
		System.loadLibrary("UnsafeDriver");
	}

}