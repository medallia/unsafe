package unsafe;

public class NativeFunction {
	private NativeModule parent;

	private String name;

	/** Pointer to the underlying llvm::Function. Used by native code. */
	private long functionPtr;

	public String getName() {
		return name;
	}

	public Object invoke(Object... args) {
		return Driver.invoke(this, args == null ? new Object[0] : args);
	}

	@Override
	public String toString() {
		return "NativeFunction '" + name + "' <0x" + Long.toHexString(functionPtr) + ">";
	}
}
