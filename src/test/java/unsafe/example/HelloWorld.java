package unsafe.example;

import unsafe.Driver;
import unsafe.NativeFunction;
import unsafe.NativeModule;

/** Simple "Hello World" example */
public class HelloWorld {
	public static void main(String[] args) {
		final NativeModule nativeModule = Driver.compileInMemory(
				"#include<stdio.h>\n" +
						"extern \"C\" void foo() {" +
						"printf(\"hello world!\\n\");" +
						"}",
		new String [] {
"-I/usr/include/c++/4.8",
"-I/usr/include/x86_64-linux-gnu/c++/4.8",
"-I/usr/include/c++/4.8/backward",
"-I/usr/lib/gcc/x86_64-linux-gnu/4.8/include",
"-I/usr/local/include",
"-I/usr/lib/gcc/x86_64-linux-gnu/4.8/include-fixed",
"-I/usr/include/x86_64-linux-gnu",
"-I/usr/include",
		}
		);
		// Check for errors compiling
		if (nativeModule.hasErrors()) {
			System.out.println("Errors:\n " + nativeModule.getErrors());
			return;
		}

		final NativeFunction foo = nativeModule.getFunctionByName("foo");
		foo.invoke();
	}
}
