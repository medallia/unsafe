package unsafe.example;

import unsafe.Driver;
import unsafe.NativeModule;

import java.io.IOException;

/** Runs a simple benchmark using AVX, SSE and Clang's auto-vectorization */
public class AvxBenchmark {
	public static void main(String[] args) throws IOException {
		final NativeModule nativeModule = Driver.compileInMemory(Util.loadResource(AvxBenchmark.class, "avx.cpp"),
				"-std=c++11",
				"-O3");
		// Check for errors compiling
		if (nativeModule.hasErrors()) {
			System.out.println("Errors:\n " + nativeModule.getErrors());
			return;
		}

		nativeModule.getFunctionByName("main").invoke();
	}
}
