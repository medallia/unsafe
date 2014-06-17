package com.medallia.thunk;

import com.medallia.unsafe.Driver;
import com.medallia.unsafe.NativeModule;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/** Tests mangled name generation */
public class ManglingTest {
	// Do not call these, they are not implemented
	private native void primitiveMangling(boolean z, byte b, char c, short s, int i, long j, float f, double d);
	private native void arrayMangling(boolean[] z, byte[] b, char[] c, short[] s, int[] i, long[] j, float[] f, double[] d,
									  boolean[] z1, byte[] b1, char[] c1, short[] s1, int[] i1, long[] j1, float[] f1, double[] d1);
	private native void objectMangling(ManglingTest x, Class c, String s, int noise, Class c1, String s1, Class c2, Object o);

	@Test
	public void testMangling() {
		final NativeModule implementation = Driver.compileInMemory("#include<jni.h>\n" +
						"void primitiveMangling(JNIEnv* env, jobject self, jboolean z, jbyte b, jchar c, jshort s, jint i, jlong j, jfloat f, jdouble d) {}\n" +
						"void arrayMangling(JNIEnv* env, jobject self, " +
						"jbooleanArray z, jbyteArray b, jcharArray c, jshortArray s, jintArray i, jlongArray j, jfloatArray f, jdoubleArray d, " +
						"jbooleanArray z1, jbyteArray b1, jcharArray c1, jshortArray s1, jintArray i1, jlongArray j1, jfloatArray f1, jdoubleArray d1) {}\n" +
						"void objectMangling(JNIEnv* env, jobject self, jobject x, jclass c, jstring s, jint noise, jclass c1, jstring s1, jclass c2, jobject o) {}"
		);

		assertFalse(implementation.getErrors(), implementation.hasErrors());

		for (Method method : ManglingTest.class.getDeclaredMethods()) {
			if (Modifier.isNative(method.getModifiers())) {
				final String mangledName = ThunkBuilder.getMangledName(method);
				assertNotNull(mangledName + " not found in " + Arrays.toString(implementation.getFunctions()).replaceAll("\\[|, |\\]", "\n"),
						implementation.getFunctionByName(mangledName));
			}
		}
	}
}
