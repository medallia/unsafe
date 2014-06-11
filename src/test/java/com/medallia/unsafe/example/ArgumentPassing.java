package com.medallia.unsafe.example;

import com.medallia.unsafe.Driver;
import com.medallia.unsafe.NativeFunction;
import com.medallia.unsafe.NativeModule;

/**
 * Shows how arguments are passed to and from Java from the dynamic calling interface.
 */
public class ArgumentPassing {
	public static void main(String[] args) {
		final String code = "#include <jni.h>\n" +
				"extern \"C\" int foo(JNIEnv * env, jobject x, jobjectArray y, int ll) {" +
				"jmethodID toStringId = env->GetMethodID(env->GetObjectClass(x),\"toString\", \"()Ljava/lang/String;\");" +
				"env->CallObjectMethod(x, toStringId);" +
				"return ll;" +
				"}";

		final NativeModule nativeModule = Driver.compileInMemory(code);

		// Check for errors compiling
		if (nativeModule.hasErrors()) {
			System.out.println("Errors:\n " + nativeModule.getErrors());
			return;
		}

		NativeFunction function = nativeModule.getFunctionByName("foo");

		final Object result = function.invoke(
				null, // JNIEnv*, can be anything, it will be magically replaced by the driver with a proper pointer
				new Object() {
					@Override
					public String toString() {
						System.out.println("Called from native code!");
						return super.toString();
					}
				},
				null,
				42
		);
		System.out.println("result = " + result);
	}
}
