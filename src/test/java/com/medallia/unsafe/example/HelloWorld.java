package com.medallia.unsafe.example;

import com.medallia.unsafe.Driver;
import com.medallia.unsafe.NativeFunction;
import com.medallia.unsafe.NativeModule;

/** Simple "Hello World" example */
public class HelloWorld {
	public static void main(String[] args) {
		final NativeModule nativeModule = Driver.compileInMemory(
				"#include<stdio.h>\n" +
						"extern \"C\" void foo() {" +
						"printf(\"hello world!\\n\");" +
						"}");
		// Check for errors compiling
		if (nativeModule.hasErrors()) {
			System.out.println("Errors:\n " + nativeModule.getErrors());
			return;
		}

		final NativeFunction foo = nativeModule.getFunctionByName("foo");
		foo.invoke();
	}
}
