package com.medallia.unsafe;

import org.junit.Test;

import java.lang.reflect.Array;

import static java.util.Arrays.asList;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

/** Basic {@link NativeFunction#invoke(Object...)} tests */
public class InvokeTest {
	@Test public void testJNIEnv() {
		final NativeModule nativeModule = Driver.compileInMemory("#include<jni.h>\n" +
				"extern \"C\" jboolean test(JNIEnv* env) { return env != 0; }");
		assertFalse(nativeModule.getErrors(), nativeModule.hasErrors());
		assertThat((long)nativeModule.getFunctionByName("test").invoke((Object)null), is(1L));
	}

	@Test public void testString() {
		final NativeModule nativeModule = Driver.compileInMemory("#include<jni.h>\n" +
				"extern \"C\" jstring test(jstring s) { return s; }");
		assertFalse(nativeModule.getErrors(), nativeModule.hasErrors());
		assertThat((String)nativeModule.getFunctionByName("test").invoke("out it goes"), is("out it goes"));
	}

	@Test public void testClass() {
		final NativeModule nativeModule = Driver.compileInMemory("#include<jni.h>\n" +
				"extern \"C\" jclass test(jclass s) { return s; }");
		assertFalse(nativeModule.getErrors(), nativeModule.hasErrors());
		assertThat(nativeModule.getFunctionByName("test").invoke(InvokeTest.class), is((Object)InvokeTest.class));
	}

	@Test public void testNumeric() {
		// For simplicity, we treat boolean as a numeric value
		for (String type : asList("boolean", "byte", "char", "short", "int", "long")) {
			final NativeModule nativeModule = Driver.compileInMemory("#include<jni.h>\n" +
					"extern \"C\" j" + type + " test(j"+ type+ " s) { return s; }");
			assertFalse(nativeModule.getErrors(), nativeModule.hasErrors());
			final long l = (long) (Math.random() * 100);
			assertThat((long)nativeModule.getFunctionByName("test").invoke(l), is(l));
		}
	}

	@Test public void testArray() {
		for (Class type : asList(Boolean.TYPE, Byte.TYPE, Character.TYPE, Short.TYPE, Integer.TYPE, Long.TYPE, Float.TYPE, Double.TYPE)) {
			final NativeModule nativeModule = Driver.compileInMemory("#include<jni.h>\n" +
					"extern \"C\" j" + type + "Array test(j"+ type+ "Array s) { return s; }");
			assertFalse(nativeModule.getErrors(), nativeModule.hasErrors());
			final Object array = Array.newInstance(type, 10);
			assertThat(nativeModule.getFunctionByName("test").invoke(array), is(array));
		}
		// Check Object[] which is special cased
		final NativeModule nativeModule = Driver.compileInMemory("#include<jni.h>\n" +
				"extern \"C\" jobjectArray test(jobjectArray s) { return s; }");
		assertFalse(nativeModule.getErrors(), nativeModule.hasErrors());
		assertThat((Object[])nativeModule.getFunctionByName("test").invoke((Object)new Object[10]), is(new Object[10]));
		assertThat((String[])nativeModule.getFunctionByName("test").invoke((Object)new String[10]), is(new String[10]));
		assertThat((int[][])nativeModule.getFunctionByName("test").invoke((Object)new int[2][10]), is(new int[2][10]));
	}
}
