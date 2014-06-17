package com.medallia.thunk.example;

import com.medallia.thunk.NativeBindings;
import com.medallia.thunk.ThunkBuilder;
import com.medallia.unsafe.Driver;
import com.medallia.unsafe.Native;
import com.medallia.unsafe.NativeModule;
import com.medallia.unsafe.example.Util;

import java.io.IOException;

/**
 * Micro-benchmark that measures call cost from JNI into Java
 */
public class ReverseCall {
	private static final NativeBindings BINDINGS = ThunkBuilder.initializeNative(ReverseCall.class);

	@Native
	private final long[] functions;

	public ReverseCall(NativeModule implementation) {
		functions = BINDINGS.getFunctionPointers(implementation);
	}

	public long benchmarked(long val) {
		if (val == 0) System.out.println("ReverseCall.benchmarked");
		return val;
	}

	public native long benchmark(int n);

	public static void main(String[] args) throws IOException {
		final NativeModule implementation = Driver.compileInMemory(Util.loadResource(ReverseCall.class, "jni2java.cpp"), "--std=c++11");
		if (implementation.hasErrors()) {
			System.out.println(implementation.getErrors());
			return;
		}

		final ReverseCall rc = new ReverseCall(implementation);
		final int n = 10_000_000;

		final long totalNs = rc.benchmark(n);
		System.out.printf("JNI to Java - per-call cost: %dns (%d calls in %ss)%n", totalNs / n, n, totalNs/1e9);

		long total = 0;
		long start = System.nanoTime();
		for (int i = 0; i < n; i++) {
			total += rc.benchmarked(i);
		}
		long end = System.nanoTime();

		if (total == n/2L*(n-1)) // just in case hotspot is too clever
			System.out.printf("Java to JNI - per-call cost: %dns (%d calls in %ss)%n", (end-start) / n, n, (end-start)/1e9);
	}
}
