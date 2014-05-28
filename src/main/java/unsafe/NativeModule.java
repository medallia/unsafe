package unsafe;

/** Represents a native module */
public class NativeModule {
	/** Pointer to underlying module. Used by native code. */
	private long modulePtr;

	/** @return all functions contained in this module.  */
	public NativeFunction[] getFunctions() {
		return Driver.getFunctions(this);
	}
}
