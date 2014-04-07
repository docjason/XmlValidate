import junit.framework.TestCase;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.mitre.xml.validate.FileResource;

import java.io.File;
import java.io.IOException;

/**
 * @author Jason Mathews, MITRE Corp.
 * Date: 9/29/13 9:11 PM
 */
public class TestKmzFile extends TestCase {

	private final SAXBuilder builder;

	public TestKmzFile() {
		builder = new SAXBuilder();
	}

	public void testKmzFiles() throws JDOMException, IOException {
		for(File f : new File("data/kmz").listFiles()) {
			checkFile(f);
		}
	}

	public void testBadKmzFiles() throws JDOMException, IOException {
		// test bad KMZ files: bad-too-large.kmz, nokml.kmz, notKmz.kmz, reallyHtml.kmz
		for(File f : new File("data/bad").listFiles()) {
			if (!f.getName().endsWith(".kmz")) continue;
			boolean expectException = ! f.getName().endsWith("notKmz.kmz");
			try {
				checkFile(f);
				if (expectException) fail("expected exception");
			} catch(Exception e) {
				if (!expectException) fail("unexpected exception");
			}
		}
	}

	private void checkFile(File file) throws JDOMException, IOException {
		FileResource res = new FileResource(System.out, file, "http://www.opengis.net/kml/2.2");
		assertNotNull(res.getFile());
		Document doc = res.getDocument(builder);
		assertNotNull(doc);
		// kml or Placemark
		String name = doc.getRootElement().getName();
		if (!name.equals("kml") && !name.equals("Placemark"))
			fail("root element expected to be kml or Placemark but instead is " + name);
	}
}
