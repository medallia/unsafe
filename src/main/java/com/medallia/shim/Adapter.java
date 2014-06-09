package com.medallia.shim;

import com.medallia.unsafe.Driver;
import com.medallia.unsafe.NativeFunction;
import com.medallia.unsafe.NativeModule;

import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public abstract class Adapter {

	/** Holds pointers to functions */
	private final long[] functions;

	protected Adapter(NativeBindings bindings, NativeModule implementation) {
		functions = new long[bindings.nativeMethods.size()];

		for (Method nativeMethod : bindings.nativeMethods) {
			final NativeFunction functionByName = implementation.getFunctionByName(nativeMethod.getName());
			if (functionByName == null) {
				throw new IllegalArgumentException("Missing implementation for: " + nativeMethod);
			}
			// We should get the pointer to the function here somehow.
		}
	}

	static class NativeBindings {
		private final NativeModule nativeModule;
		private final List<Method> nativeMethods;

		private NativeBindings(NativeModule nativeModule, List<Method> nativeMethods) {
			this.nativeModule = nativeModule;
			this.nativeMethods = nativeMethods;
		}
	}

	public static NativeBindings initializeNative(Class aClass) {
		final List<Method> nativeMethods = new ArrayList<>();
		for (Method method : aClass.getDeclaredMethods()) {
			if (Modifier.isNative(method.getModifiers())) {
				nativeMethods.add(method);
			}
		}

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
			generateNativeMethodShim(pw, nativeMethod, i);
		}

		generateRegisterNative(nativeMethods, pw);

		pw.println("}");


		System.out.println(sw);

		final NativeModule nativeModule = Driver.compileInMemory(sw.toString());
		if (nativeModule.hasErrors()) {
			System.out.println(nativeModule.getErrors());
		}

		final NativeFunction registerNative = nativeModule.getFunctionByName("registerNative");
		registerNative.invoke(null, aClass);
		return new NativeBindings(nativeModule, nativeMethods);
	}

	private static void generateRegisterNative(List<Method> nativeMethods, IndentedPrintWriter pw) {
		pw.println("void registerNative(JNIEnv* env, jclass fastCallClass) {");
		pw.indent();
		pw.println("functionsFldId = env->GetFieldID(fastCallClass, \"functions\", \"[J\");");
		pw.println("JNINativeMethod methods[] = {");
		pw.indent();
		for (Method nativeMethod : nativeMethods) {
			generateRegisterNativeForMethod(pw, nativeMethod);
		}
		pw.dedent();
		pw.println("};");
		pw.printf("env->RegisterNatives(fastCallClass, methods, %d);", nativeMethods.size());
		pw.dedent();
		pw.println("}");
	}



	private static void generateRegisterNativeForMethod(IndentedPrintWriter pw, Method nativeMethod) {
		pw.printf("{ (char*)\"%s\", (char*)\"", nativeMethod.getName());
		pw.print("(");
		for (Class<?> argType : nativeMethod.getParameterTypes()) {
			pw.print(toJavaSignature(argType));
		}
		pw.print(")");
		pw.print(toJavaSignature(nativeMethod.getReturnType()));
		pw.printf("\", (void*)%s },\n", nativeMethod.getName());
	}

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


	private static void generateGetFunctionHelper(IndentedPrintWriter pw) {
		pw.println("jlong _getFunction(JNIEnv* env, jobject self, jint index) {");
		pw.indent();
		pw.println("jlong functionPtr;");
		pw.println("env->GetLongArrayRegion((jlongArray) env->GetObjectField(self, functionsFldId), index, 1, &functionPtr);");
		pw.println("return functionPtr;");
		pw.dedent();
		pw.println("}");
	}

	private static void generateNativeMethodShim(IndentedPrintWriter pw, Method nativeMethod, int index) {
		pw.printf("%s %s(JNIEnv* env, jobject self", toJNIType(nativeMethod.getReturnType()), nativeMethod.getName());
		int argNo = 0;
		for (Class<?> argType : nativeMethod.getParameterTypes()) {
			pw.printf(", %s arg%d", toJNIType(argType), argNo++);
		}
		pw.println(") {");
		pw.indent();

		if (nativeMethod.getReturnType() != Void.TYPE) {
			pw.print("return ");
		}

		// Cast the function pointer to the correct type
		pw.printf("((%s(*)(JNIEnv*, jobject", toJNIType(nativeMethod.getReturnType()));
		for (Class<?> argType : nativeMethod.getParameterTypes()) {
			pw.printf(", %s", toJNIType(argType));
		}
		pw.printf("))_getFunction(env, self, %d))", index);

		// Call it
		pw.printf("(env, self");
		argNo = 0;
		for (Class<?> argType : nativeMethod.getParameterTypes()) {
			pw.printf(", arg%d", argNo++);
		}
		pw.println(");");


		pw.dedent();
		pw.println("}");
	}

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

	public static void main(String[] args) {
		System.out.println(new Test(null));
	}
}
