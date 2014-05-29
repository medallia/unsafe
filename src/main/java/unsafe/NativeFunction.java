package unsafe;

public class NativeFunction {
	/** Pointer to the underlying llvm::Function. Used by native code. */
	private long functionPtr;

	public String getName() {
		return Driver.getFunctionName(this);
	}

	public Object invoke(Object... args) {
		return Driver.invoke(this, args);
	}

	@Override
	public String toString() {
		return "NativeFunction '" + getName() + "' <0x" + Long.toHexString(functionPtr) + ">";
	}
}
