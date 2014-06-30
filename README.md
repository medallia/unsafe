Unsafe
======

a Java library to compile and run C++ code in-memory


```java
final NativeModule nativeModule = Driver.compileInMemory(
		"#include<stdio.h>\n" +
		"extern \"C\" void foo() {" +
		"printf(\"hello world!\\n\");" +
		"}");

nativeModule.getFunctionByName("foo").invoke();
```