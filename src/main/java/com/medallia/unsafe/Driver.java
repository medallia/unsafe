package com.medallia.unsafe;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides methods to compile C/C++ code in-memory.
 */
public class Driver {
	/** List of arguments with default include directories for the current platform. */
	public static final List<String> DEFAULT_INCLUDE_ARGS;

	/**
	 * Compiles the specified source code using a virtual file named {@code fileName}.
	 * This method passes parameter to includes all platform default directories plus JNI headers.
	 * @param sourceCode code to be compiled
	 * @param additionalCompilerArgs additional arguments for Clang
	 * @return a compiled NativeModule
	 */
	public static NativeModule compileInMemory(String sourceCode, String... additionalCompilerArgs) {
		final List<String> compilerArgs = new ArrayList<>();
		compilerArgs.addAll(DEFAULT_INCLUDE_ARGS);
		Collections.addAll(compilerArgs, additionalCompilerArgs);
		return compileInMemory(null, sourceCode, compilerArgs.toArray(new String[compilerArgs.size()]));
	}

	/**
	 * Compiles the specified source code using a virtual file named {@code fileName}.
	 * It passes the {@code compilerArgs} Clang.
	 * Note that this method does not pass any default parameters to the the compiler.
	 * @param fileName name of the virtual file
	 * @param sourceCode code to be compiled
	 * @param compilerArgs additional arguments for Clang
	 * @return a compiled NativeModule
	 */
	public static NativeModule compileInMemory(String fileName, String sourceCode, String[] compilerArgs) {
		if (sourceCode == null) {
			throw new IllegalArgumentException("missing source code");
		}
		if (fileName == null) {
			fileName = "code.cpp";
		}
		if (compilerArgs == null) {
			compilerArgs = new String[0];
		}
		for (String compilerArg : compilerArgs) {
			if (compilerArg == null) {
				throw new IllegalArgumentException("some compiler arguments are null");
			} else if ("-g".equals(compilerArg.trim()) || "-pg".equals(compilerArg.trim())) {
				throw new IllegalArgumentException("Unsupported argument: " + compilerArg);
			}
		}

		return compileInMemory0(fileName, sourceCode, compilerArgs);
	}



	private static native NativeModule compileInMemory0(String fileName, String sourceCode, String[] compilerArgs);
	static native Object invoke(NativeFunction function, Object[] args);
	static native NativeFunction[] getFunctions(NativeModule nativeModule);
	static native void delete(NativeModule nativeModule);

	/**
	 * Initializes the native code. Should be called once before any other native methods in this class.
	 */
	private static native void initializeNativeCode();

	/** Command used to invoke the platform's compiler. */
	private static final String CPP_COMPILER = System.getProperty("unsafe.cpp.compiler", "/usr/bin/g++");

	/** Suffix used on OSX to mark framework directories */
	private static final String FRAMEWORK_SUFFIX = " (framework directory)";

	/** An {@link java.lang.AutoCloseable} temporary file that is deleted on close */
	private static class TempFile implements AutoCloseable {
		private final File file;

		/**
		 * Creates a new temporary file.
		 * @param  prefix     The prefix string to be used in generating the file's
		 *                    name; must be at least three characters long
		 *
		 * @param  suffix     The suffix string to be used in generating the file's
		 *                    name; may be <code>null</code>, in which case the
		 *                    suffix <code>".tmp"</code> will be used
		 *
		 * @throws IOException if a file could not be created.
		 */
		private TempFile(String prefix, String suffix) throws IOException { file = File.createTempFile(prefix, suffix); }
		/** @return the underlying file */
		public File getFile() { return file; }
		@Override public String toString() { return file.toString(); }
		@Override public void close() throws IOException { Files.deleteIfExists(file.toPath()); }
	}

	/** @return a {@link java.nio.file.Path} to the current JDK's java home. */
	private static Path getJavaHome() {
		Path javaHome = Paths.get(System.getProperty("java.home"));
		return javaHome.endsWith("jre") ? javaHome.getParent() : javaHome;
	}

	/** @return a list with parameters for all default platform includes. */
	private static List<String> loadDefaultIncludeSearchPaths() throws IOException, InterruptedException {
		final List<String> result = new ArrayList<>();
		try (final TempFile source = new TempFile("dummy", ".cpp");
			 final TempFile output = new TempFile("output", ".log")) {

			// Ask the current g++ compiler to get us the list of include directories
			final Process process = new ProcessBuilder(CPP_COMPILER, "-v", source.toString())
					.redirectError(Redirect.to(output.getFile()))
					.start();
			process.waitFor(); // FIXME: use a timeout on Java 8
			boolean capturing = false;
			// Parse the output and build the include parameters
			try (final BufferedReader br = new BufferedReader(new FileReader(output.getFile()))) {
				for (String line = br.readLine(); line != null; line = br.readLine()) {
					if ("#include <...> search starts here:".equals(line)) {
						capturing = true;
					} else if ("End of search list.".equals(line)) {
						break;
					} else if (capturing) {
						String pathName = line.trim();
						boolean isFramework = false;
						if (pathName.endsWith(FRAMEWORK_SUFFIX)) {
							pathName = pathName.substring(0, pathName.length() - FRAMEWORK_SUFFIX.length());
							isFramework = true;
						}

						final Path normalized = Paths.get(pathName).normalize();
						if (normalized.toFile().exists()) {
							result.add((isFramework ? "-F" : "-I") + normalized);
						}
					}
				}
			}

			// Add JNI includes
			final Path javaHome = getJavaHome();
			result.add("-I" + javaHome.resolve("include"));
			result.add("-I" + javaHome.resolve("include/darwin"));
		}
		return result;
	}

	static {
		System.loadLibrary("UnsafeDriver");
		initializeNativeCode();

		List<String> defaultIncludeArgs = Collections.emptyList();
		try {
			defaultIncludeArgs = Collections.unmodifiableList(loadDefaultIncludeSearchPaths());
		} catch (IOException|InterruptedException e) {
			e.printStackTrace();
		}
		DEFAULT_INCLUDE_ARGS = defaultIncludeArgs;
	}
}