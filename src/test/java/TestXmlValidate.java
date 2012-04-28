import junit.framework.TestCase;
import org.mitre.xml.validate.UrlResource;
import org.mitre.xml.validate.XmlValidate;

import java.io.File;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

/**
 * @author Jason Mathews, MITRE Corp.
 *
 * Date: 4/27/12 11:10 AM
 */
public class TestXmlValidate extends TestCase {

	public void testUrlResource() throws MalformedURLException {
		URL url = new File("data/big.kmz").toURI().toURL();
		XmlValidate validator = new XmlValidate();
		// add namespace map to validator
		validator.setMap(new File("ns.map"));
		validator.validate(new UrlResource(System.out, url, null));
		url = new File("data/placemark.kml").toURI().toURL();
		validator.validate(new UrlResource(System.out, url, null));
		assertEquals(2, validator.getFileCount());
	}

	public void testBadXml() {
		XmlValidate validator = new XmlValidate();
		validator.validate(new File("data/bad.xml"));
		validator.setSummary(true);
		validator.dumpStatus();
		assertEquals(1, validator.getFileCount());
	}

	public void testCotXml() {
		// Test XML document with no namespace
		// this will force adding xsi:noNamespaceSchemaLocation attribute to root element for validation
		XmlValidate validator = new XmlValidate();
		validator.setSchema(new File("schemas/Event.xsd"));
		validator.setVerbose(true);
		validator.setDebug(true);
		validator.setHomeDir(".");
		validator.setDumpLevel(2);
		validator.validate(new File("data/cot.xml"));
		//validator.setSummary(true);
		validator.dumpStatus();
		assertEquals(1, validator.getFileCount());
		assertEquals(0, validator.getErrors());
		assertEquals(0, validator.getWarnings());
	}

	public void testKmlDirRecurse() {
		// Validate all KML and KMZ documents using KML 2.1 Schema
		XmlValidate validator = new XmlValidate();
		Set<String> extensionSet = validator.getExtensionSet();
		extensionSet.clear();
		extensionSet.add("kml");
		extensionSet.add("kmz");

		// http://code.google.com/apis/kml/schema/kml21.xsd
		validator.setSchema(new File("schemas/kml21.xsd"));
		validator.setNamespace("http://earth.google.com/kml/2.1");
		validator.setSummary(true);

		//validator.setVerbose();
		validator.validate(new File("data"));
		validator.dumpStatus();

		assertEquals(13, validator.getFileCount());
		// assertEquals(2, validator.getErrors());
		// assertTrue(validator.getErrors() != 0);
		assertEquals(1, validator.getWarnings());
	}

	public void testGpxFile() {
		// Validate all GPX/KML/KMZ documents against the declared namespace
		XmlValidate validator = new XmlValidate();
		validator.setSummary(true);
		Set<String> extensionSet = validator.getExtensionSet();
		// add namespace map to validator
		validator.setMap(new File("ns.map"));
		extensionSet.clear();
		extensionSet.add("kml");
		extensionSet.add("kmz");
		extensionSet.add("gpx");
		validator.validate(new File("data"));
		assertEquals(14, validator.getFileCount());
		assertTrue(validator.getErrors() != 0);
	}
}
