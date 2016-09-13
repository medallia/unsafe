package com.medallia.unsafe;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a compiled native module.
 * Instances for this class can be obtained by calling one of
 * {@link Driver#compileInMemory(String, String, String[])} methods.
 */
public class NativeModule {
	/** Pointer to underlying module. Used by native code, do not rename. */
	@Native
	private final long modulePtr;

	/** Functions available in the compiled module. Might be empty. */
	private final NativeFunction[] functions;

	/** Index for {@link com.medallia.unsafe.NativeFunction} by decorated name. */
	private final Map<String, NativeFunction> nameIndex;

	/** Any compilation errors */
	private final String errors;

	/**
	 * Creates a new {@link com.medallia.unsafe.NativeModule}.
	 * Used by native code, do not change.
	 */
	@Native
	private NativeModule(long modulePtr, String errors) {
		this.modulePtr = modulePtr;
		this.errors = errors;
		this.functions = Driver.getFunctions(this);
		this.nameIndex = new HashMap<>();
		for (NativeFunction function : functions) {
			nameIndex.put(function.getName(), function);
		}
	}

	/** @return all functions contained in this module.  */
	public NativeFunction[] getFunctions() {
		return functions;
	}


	/**
	 * Looks up a function by it's decorated name.
	 * The name follows C/C++ naming decoration conventions.
	 * @param name the function name
	 * @return the NativeFunction or null if not found.
	 */
	public NativeFunction getFunctionByName(String name) {
		return nameIndex.get(name);
	}

	/**
	 * Finds a similar function in this module
 	 * @param simpleName simple name to look for
	 * @return the first match
	 */
	public String findSimilar(String simpleName) {
		return nameIndex.keySet().stream()
				.filter(s -> s.contains(simpleName)).findAny()
				.orElse(null);
	}

	/** @return true if there are any errors */
	public boolean hasErrors() {
		return !errors.isEmpty();
	}

	/** @return any compilation errors */
	public String getErrors() {
		return errors;
	}

	@Override
	public String toString() {
		return "NativeModule <0x" + Long.toHexString(modulePtr) + ">";
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		Driver.delete(this);
	}
}
