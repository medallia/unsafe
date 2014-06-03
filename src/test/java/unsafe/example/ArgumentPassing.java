package unsafe.example;

import unsafe.Driver;
import unsafe.NativeFunction;
import unsafe.NativeModule;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Shows how arguments are passed to and from Java
 */
public class ArgumentPassing {
	public static void main(String[] args) {
		final String code = "#include <jni.h>\n" +
				"extern \"C\" int foo(JNIEnv * env, jobject x, jobjectArray y, int ll) {" +
				"jmethodID toStringId = env->GetMethodID(env->GetObjectClass(x),\"toString\", \"()Ljava/lang/String;\");" +
				"env->CallObjectMethod(x, toStringId);" +
				"return ll;" +
				"}";

		// Since we will require JNI, we need to pass the necessary include directories to the Clang compiler.
		final Path javaHome = getJavaHome();
		final Path xcodeRoot = Paths.get("/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain");
		final NativeModule nativeModule = Driver.compileInMemory(code,
				new String[]{
						"-I" + javaHome.resolve("include"),
						"-I" + javaHome.resolve("include/darwin"),
						"-I" + xcodeRoot.resolve("usr/lib/c++/v1"),
						"-I" + xcodeRoot.resolve("usr/lib/clang/5.1/include"),
						"-I" + xcodeRoot.resolve("usr/include"),
				}
		);

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

	private static Path getJavaHome() {
		Path javaHome = Paths.get(System.getProperty("java.home"));
		return javaHome.endsWith("jre") ? javaHome.getParent() : javaHome;
	}
}
