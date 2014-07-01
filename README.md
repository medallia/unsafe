Unsafe
======

a Java library to compile and run C++ code in-memory based on Clang+LLVM:

```java
final NativeModule nativeModule = Driver.compileInMemory(
		"#include<stdio.h>\n" +
		"extern \"C\" void foo() {" +
		"printf(\"hello world!\\n\");" +
		"}");

nativeModule.getFunctionByName("foo").invoke();
```
This library is designed for performance sensitive applications where access and generation of fast native code on the fly gives a performance edge.
It is not intended for integration, mainly because the linker is not exposed in the API to keep it simple.

Features
========

 - Compile and run C++ code using Clang+LLVM
 - Dynamic invocation of compiled functions with basic argument marshalling from/to Java
 - Built in support for JNI types on compiled code
 - Implement native methods on the fly
 - Automatic thunk generation for fast Java to native calling

There are two main packages:

- **com.medallia.unsafe**: The low-level api. 
- **com.medallia.thunk**: an automatic thunk builder to dynamically implement JNI methods.

The low-level API provides a simple reflective interface for compiled modules.
Even though it is practical, it is awfully slow. The call cost of invoking NativeFunction.invoke() is about 1 ms. To avoid this, you should use the automatic thunk generator, which lets you implement JNI methods on the fly. A JNI method call ia about 1000x faster.

Building
========

Make sure that you have a properly set JAVA_HOME environment variable.


Go to the directory where you checked out the project:

```
cd unsafe
```

Then you need to checkout and build Clang (it will take some time):

```
./bin/checkout-clang.sh
./bin/build-clang.sh
```

The scripts will checkout the correct version for you.
Once that's done, you can build the JNI library:

```
cd jni
make
```
Depending on how your build environment is setup, you might need to pass the JAVA_HOME variable to make.

To run the examples, you will have to pass the JNI library directory in the java.library.path property, for example:

```
-Djava.library.path=/Users/<your username>/projects/unsafe/jni
```
