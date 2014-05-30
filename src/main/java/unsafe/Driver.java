package unsafe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
		final String code = "#include <jni.h>\n" +
				"extern \"C\" int foo(JNIEnv * env, jobject x, jobjectArray y, int ll) {" +
				"jmethodID toStringId = env->GetMethodID(env->GetObjectClass(x),\"toString\", \"()Ljava/lang/String;\");" +
				"env->CallObjectMethod(x, toStringId);" +
				"return ll;" +
				"}";

		final ExecutorService executorService = Executors.newFixedThreadPool(4);

		for (int i = 0; i < 1000; i++) {
			final int iter = i;
			executorService.submit(new Runnable() {
				@Override
				public void run() {
					NativeModule nativeModule = compileInMemory(code,
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
						final Object result = function.invoke(null, new Object() {
							@Override
							public String toString() {
								System.out.println("Called .toString() from native code");
								return super.toString();
							}
						}, new String[]{"and out it went"}, iter);
						System.out.println("result = " + result);
						final long end = System.nanoTime();
						System.out.println((end - start) / 1e3 + "us");
					}

				}
			});
		}

		System.gc();
		System.out.println("Done!");
		System.in.read();
	}
}