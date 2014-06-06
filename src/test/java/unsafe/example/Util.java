package unsafe.example;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;

/** Assorted utility functions */
public class Util {
	/** Load the a resource file as string using the specified class' classloader. */
	public static String loadResource(Class aClass, String name) throws IOException {
		final StringWriter sw = new StringWriter();
		try (final InputStreamReader in = new InputStreamReader(aClass.getResourceAsStream(name))) {
			char[] buffer = new char[4096];
			int count;
			while ( (count = in.read(buffer)) != -1 ) {
				sw.write(buffer, 0, count);
			}
		}
		return sw.toString();
	}
}
