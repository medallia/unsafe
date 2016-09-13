package com.medallia.unsafe.thunk;

import com.medallia.unsafe.NativeFunction;
import com.medallia.unsafe.NativeModule;

import java.lang.reflect.Method;
import java.util.List;

/** Bindings to native methods. */
public class NativeBindings {
	/**
	 * We keep a reference to the {@link com.medallia.unsafe.NativeModule} holding the thunk to prevent
	 * it from being prematurely garbage collected
	 */
	@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
	private final NativeModule nativeModule;

	/** List of native methods in the order used by thunk generation. */
	private final List<Method> nativeMethods;

	NativeBindings(NativeModule nativeModule, List<Method> nativeMethods) {
		this.nativeModule = nativeModule;
		this.nativeMethods = nativeMethods;
	}

	/**
	 * Given an implementation, it builds a function pointer array suitable to be used on the class for which these
	 * bindings where created.
	 * @param implementation a {@link com.medallia.unsafe.NativeModule} containing all the required method implementations.
	 * @return an array of pointers.
	 */
	public long[] getFunctionPointers(NativeModule implementation) {
		if (implementation.hasErrors()) {
			throw new IllegalArgumentException("Implementation has errors:\n" + implementation.getErrors());
		}
		final long[] functions = new long[nativeMethods.size()];

		for (int i = 0; i < nativeMethods.size(); i++) {
			final Method nativeMethod = nativeMethods.get(i);
			final String mangledName = ThunkBuilder.getMangledName(nativeMethod);
			final NativeFunction compiledFunction = implementation.getFunctionByName(mangledName);
			if (compiledFunction == null) {
				String similar = implementation.findSimilar(nativeMethod.getName());
				throw new IllegalArgumentException("Missing implementation for: " + nativeMethod + " (" + mangledName + ")."
						+ (similar == null? "" : " Maybe you meant: " + similar));
			}
			// We should get the pointer to the function here somehow.
			functions[i] = compiledFunction.getPointerToCompiledFunction();
		}

		return functions;
	}
}
