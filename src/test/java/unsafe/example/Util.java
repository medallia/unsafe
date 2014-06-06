package unsafe.example;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;

public class Util {
	public static String loadResource(String name) throws IOException {
		final StringWriter sw = new StringWriter();
		try (final InputStreamReader in = new InputStreamReader(Util.class.getResourceAsStream(name))) {
			char[] buffer = new char[4096];
			int count;
			while ( (count = in.read(buffer)) != -1 ) {
				sw.write(buffer, 0, count);
			}
		}
		return sw.toString();
	}
}
