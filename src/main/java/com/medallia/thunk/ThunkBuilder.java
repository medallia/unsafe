package com.medallia.thunk;

import com.medallia.io.IndentedPrintWriter;
import com.medallia.unsafe.Driver;
import com.medallia.unsafe.NativeFunction;
import com.medallia.unsafe.NativeModule;

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Dynamically implements all native methods of a class as thunks which delegate
 * the actual function implementation to {@link com.medallia.unsafe.NativeModule} compiled
 * at a later time.
 * <p/>
 * Calling a {@link com.medallia.unsafe.NativeFunction} through a dynamically generated thunk
 * is about 1000x faster than using {@link com.medallia.unsafe.NativeFunction#invoke(Object...)}.
 *
 * The following is a simple usage example:
 * <pre>
 *     class Example {
 *         private static final BINDINGS = ShimBuilder.initializeNative(Example.class);
 *
 *         private final long[] functions;
 *
 *         Example(NativeModule implementation) {
 *             functions = BINDINGS.getFunctionPointers(implementation);
 *         }
 *
 *         public native void foo(SomeObject args);
 *     }
 * </pre>
 *
 * The {@link #initializeNative(Class)} will implement on the fly all the native methods of the passed class,
 * returning a {@link com.medallia.thunk.NativeBindings} object.
 * <p>
 * The class should have an instance field called {@code functions} of type {@code long[]} which will be used by
 * the native methods to find the actual implementations. For example, in the example above, the {@code foo()}
 * method will be implemented as follows:
 * <pre>
 *     void foo(JNIEnv* env, jobject self, jobject arg0) {
 *         jlong functionPtr;
 *	       env->GetLongArrayRegion((jlongArray) env->GetObjectField(self, functionsFldId), 0 , 1, &functionPtr);
 *         ((void(*)(JNIEnv*, jobject, jobject))functionPtr)(env, self, arg0);
 *     }
 * </pre>
 *
 * This function will lookup the function pointer in element 0 of the {@code functions} array, cast it and invoke
 * passing the arguments.
 */
public abstract class ThunkBuilder {
	/**
	 * Creates a set of native bindings for a all native methods in the specified class.
	 * The returned {@link com.medallia.thunk.NativeBindings} should be held for the lifetime of the class,
	 * for example as a {@code static final} field.
	 *
	 * The specified class should have an instance field called {@code functions} of type {@code long[]} which
	 * will be used by the native code to access the actual implementation at runtime.
	 *
	 * The function pointer array should be obtained by calling {@link com.medallia.thunk.NativeBindings#getFunctionPointers(com.medallia.unsafe.NativeModule)}
	 * @param aClass class to be processed
	 * @return {@link NativeBindings} for the class.
	 */
	public static NativeBindings initializeNative(Class<?> aClass) {

		try {
			final Field functions = aClass.getDeclaredField("functions");
			if (Modifier.isStatic(functions.getModifiers())) {
				throw new IllegalArgumentException("'functions' field should be an instance field.");
			}
			if (!functions.getType().equals(long[].class)) {
				throw new IllegalArgumentException("'functions' field should be a long [].");
			}
		} catch (NoSuchFieldException e) {
			throw new IllegalArgumentException("Class should have a 'long[] functions' field declared", e);
		}

		final List<Method> nativeMethods = new ArrayList<>();
		for (Method method : aClass.getDeclaredMethods()) {
			if (Modifier.isNative(method.getModifiers())) {
				nativeMethods.add(method);
			}
		}

		final NativeModule nativeModule = Driver.compileInMemory(generateThunk(nativeMethods));
		if (nativeModule.hasErrors()) {
			throw new IllegalStateException(nativeModule.getErrors());
		}

		final NativeFunction registerNative = nativeModule.getFunctionByName("registerNative");
		registerNative.invoke(null, aClass);
		return new NativeBindings(nativeModule, nativeMethods);
	}

	/**
	 * Generates a thunk for all the specifed methods plus a {@code registerNative())
	 * that registers the generated thunks with the JVM
	 */
	private static String generateThunk(List<Method> nativeMethods) {
		final StringWriter sw = new StringWriter();
		final IndentedPrintWriter pw = new IndentedPrintWriter(sw);
		pw.println("#include <jni.h>");
		pw.println("jfieldID functionsFldId;");

		pw.println("extern \"C\" {");
		pw.println();

		generateGetFunctionHelper(pw);

		for (int i = 0; i < nativeMethods.size(); i++) {
			final Method nativeMethod = nativeMethods.get(i);
			pw.println();
			generateNativeMethodThunk(pw, nativeMethod, i);
		}

		generateRegisterNative(pw, nativeMethods);

		pw.println("}");

		return sw.toString();
	}

	/** Generates the {@code registerNative()} helper function for the specified native methods */
	private static void generateRegisterNative(IndentedPrintWriter pw, List<Method> nativeMethods) {
		pw.println("void registerNative(JNIEnv* env, jclass fastCallClass) {");
		pw.indent();
		pw.println("functionsFldId = env->GetFieldID(fastCallClass, \"functions\", \"[J\");");
		pw.println("JNINativeMethod methods[] = {");
		pw.indent();
		for (Method nativeMethod : nativeMethods) {
			generateJNINativeMethod(pw, nativeMethod);
		}
		pw.dedent();
		pw.println("};");
		pw.printf("env->RegisterNatives(fastCallClass, methods, %d);", nativeMethods.size());
		pw.dedent();
		pw.println("}");
	}

	/** Generates a JNINativeMethod struct for the specified native method. */
	private static void generateJNINativeMethod(IndentedPrintWriter pw, Method nativeMethod) {
		pw.printf("{ (char*)\"%s\", (char*)\"", nativeMethod.getName());
		pw.print("(");
		for (Class<?> argType : nativeMethod.getParameterTypes()) {
			pw.print(toJavaSignature(argType));
		}
		pw.print(")");
		pw.print(toJavaSignature(nativeMethod.getReturnType()));
		pw.printf("\", (void*)%s },\n", nativeMethod.getName());
	}

	/**
	 * Converts a {@link java.lang.Class} to it's JNI signature
	 * @param type a java class
	 * @return the JNI signature for the specified class
	 */
	private static String toJavaSignature(Class<?> type) {
		final String signature;
		if (type.isArray()) {
			signature = "[" + toJavaSignature(type.getComponentType());
		} else if (type.isPrimitive()) {
			if (type == Void.TYPE) {
				signature = "V";
			} else if (type == Boolean.TYPE) {
				signature = "Z";
			} else if (type == Byte.TYPE) {
				signature = "B";
			} else if (type == Character.TYPE) {
				signature = "C";
			} else if (type == Short.TYPE) {
				signature = "S";
			} else if (type == Integer.TYPE) {
				signature = "I";
			} else if (type == Long.TYPE) {
				signature = "J";
			} else if (type == Float.TYPE) {
				signature = "F";
			} else if (type == Double.TYPE) {
				signature = "D";
			} else {
				throw new AssertionError();
			}
		} else {
			// TODO: Check if inner classes (anonymous or otherwise) are properly handled
			signature = "L" + type.getName().replace('.','/')  + ";";
		}
		return signature;	}

	/** Generate a helper function to access a specific function pointer in the containing object. */
	private static void generateGetFunctionHelper(IndentedPrintWriter pw) {
		pw.println("inline jlong _getFunction(JNIEnv* env, jobject self, jint index) {");
		pw.indent();
		pw.println("jlong functionPtr;");
		pw.println("env->GetLongArrayRegion((jlongArray) env->GetObjectField(self, functionsFldId), index, 1, &functionPtr);");
		pw.println("return functionPtr;");
		pw.dedent();
		pw.println("}");
	}

	/**
	 *
	 * @param pw print writer used to emit the code
	 * @param nativeMethod the native method we want to generate the thunk for
	 * @param index index into the function table that will hold the pointer to the implementation at runtime.
	 */
	private static void generateNativeMethodThunk(IndentedPrintWriter pw, Method nativeMethod, int index) {
		pw.printf("%s %s(JNIEnv* env, jobject self", toJNIType(nativeMethod.getReturnType()), nativeMethod.getName());
		final Class<?>[] parameterTypes = nativeMethod.getParameterTypes();
		for (int i = 0; i < parameterTypes.length; i++) {
			final Class<?> argType = parameterTypes[i];
			pw.printf(", %s arg%d", toJNIType(argType), i);
		}
		pw.println(") {");
		pw.indent();

		if (nativeMethod.getReturnType() != Void.TYPE) {
			pw.print("return ");
		}

		// Cast the function pointer to the correct type
		pw.printf("((%s(*)(JNIEnv*, jobject", toJNIType(nativeMethod.getReturnType()));
		for (Class<?> argType : parameterTypes) {
			pw.printf(", %s", toJNIType(argType));
		}
		pw.printf("))_getFunction(env, self, %d))", index);

		// Call it
		pw.printf("(env, self");
		for (int i = 0; i < parameterTypes.length; i++) {
			pw.printf(", arg%d", i);
		}
		pw.println(");");

		pw.dedent();
		pw.println("}");
	}

	/**
	 * Convert a {@link java.lang.Class} to a string representing it's JNI type.
	 * @param type a class
	 * @return the JNI type for the class.
	 */
	private static String toJNIType(Class<?> type) {
		final String jniType;
		if (type.isArray()) {
			if (type.getComponentType().isPrimitive()) {
				jniType = toJNIType(type.getComponentType()) + "Array";
			} else {
				jniType = "jobjectArray";
			}
		} else if (type.isPrimitive()) {
			if (type == Void.TYPE) {
				jniType = "void";
			} else if (type == Byte.TYPE) {
				jniType = "jbyte";
			} else if (type == Boolean.TYPE) {
				jniType = "jboolean";
			} else if (type == Character.TYPE) {
				jniType = "jchar";
			} else if (type == Short.TYPE) {
				jniType = "jshort";
			} else if (type == Integer.TYPE) {
				jniType = "jint";
			} else if (type == Long.TYPE) {
				jniType = "jlong";
			} else if (type == Float.TYPE) {
				jniType = "jfloat";
			} else if (type == Double.TYPE) {
				jniType = "jdouble";
			} else {
				throw new AssertionError();
			}
		} else if (Class.class == type) {
			jniType = "jclass";
		} else if (String.class == type) {
			jniType = "jstring";
		} else {
			jniType = "jobject";
		}
		return jniType;
	}
}
