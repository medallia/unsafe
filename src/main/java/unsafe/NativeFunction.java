package unsafe;

/**
 * A compiled native function.
 */
public class NativeFunction {

	/**
	 * Pointer to the underlying llvm::Function.
	 * Used by native code, do not rename.
	 */
	private final long functionPtr;

	/**
	 * The {@link unsafe.NativeModule} that originated this {@link unsafe.NativeFunction}.
	 * Used by native code, do not rename.
	 */
	private final NativeModule parent;

	/** this function's decorated name */
	private final String name;

	/** Called by native code. */
	@SuppressWarnings("UnusedDeclaration")
	private NativeFunction(long functionPtr, String name, NativeModule parent) {
		this.functionPtr = functionPtr;
		this.name = name;
		this.parent = parent;
	}

	/** @return the function's decorated name */
	public String getName() {
		return name;
	}

	/** @return the {@link unsafe.NativeModule} this function is a member of */
	public NativeModule getParent() {
		return parent;
	}

	/**
	 * Invokes this function.
	 * This method supports the following conversions from Java objects:
	 * <ul>
	 *     <li>any integer type is assigned the result of calling {@code longValue()} on the passed object</li>
	 *     <li>{@code JNIEnv*} is assigned the current JNI environment pointer. The passed Java argument is ignored. </li>
	 *     <li>{@code jobject} the java object is passed as is. </li>
	 *     <li>{@code jstring} the string is passed as is. Throws an {@link java.lang.IllegalArgumentException}
	 *     if the argument is not a string </li>
	 *     <li>{@code jobjectArray} the array is passed as is. Throws an {@link java.lang.IllegalArgumentException}
	 *     if the argument is not a valid java object array. Note that an array of arrays (e.g. int[][]) is an object array.</li>
	 * </ul>
	 *
	 * For return values the following conversions from native types apply:
	 * <ul>
	 *     <li>any integer type is converted to a long</li>
	 *     <li>{@code jobject} the java object is returned as is. </li>
	 *     <li>{@code jstring} the string is returned as is. </li>
	 *     <li>{@code jobjectArray} the array is returned as is.</li>
	 *     <li>all other values are converted to null.</li>
	 * </ul>
	 * @param args arguments to the function.
	 * @throws java.lang.IllegalArgumentException if the provided number of arguments does not match the function
	 * 		   or if a type conversion is not available.
	 * @return the function's return value or null.
	 */
	public Object invoke(Object... args) {
		return Driver.invoke(this, args == null ? new Object[0] : args);
	}

	@Override
	public String toString() {
		return "NativeFunction '" + name + "' <0x" + Long.toHexString(functionPtr) + ">";
	}
}
