package unsafe.example;

import unsafe.Driver;
import unsafe.NativeModule;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;

/** Runs a simple benchmark using AVX, SSE and Clang's auto-vectorization */
public class AvxBenchmark {
	public static void main(String[] args) throws IOException {
		final NativeModule nativeModule = Driver.compileInMemory(loadResource("avx.cpp"),
				"-std=c++11",
				"-O3");
		// Check for errors compiling
		if (nativeModule.hasErrors()) {
			System.out.println("Errors:\n " + nativeModule.getErrors());
			return;
		}

		nativeModule.getFunctionByName("main").invoke();
	}

	private static String loadResource(String name) throws IOException {
		final StringWriter sw = new StringWriter();
		try (final InputStreamReader in = new InputStreamReader(AvxBenchmark.class.getResourceAsStream(name))) {
			char[] buffer = new char[4096];
			int count;
			while ( (count = in.read(buffer)) != -1 ) {
				sw.write(buffer, 0, count);
			}
		}
		return sw.toString();
	}
}
