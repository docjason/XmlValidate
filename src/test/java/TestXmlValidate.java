import junit.framework.TestCase;
import org.mitre.xml.validate.UrlResource;
import org.mitre.xml.validate.XmlValidate;

import java.io.File;
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
		XmlValidate validator = new XmlValidate();
        validator.setSummary(true);
		// add namespace map to validator
		validator.setMap(new File("ns.map"));
		URL url = new File("data/kmz/big.kmz").toURI().toURL();
		UrlResource resource = new UrlResource(System.out, url, null);
		assertNotNull(resource.getSource());
		validator.validate(resource);
		url = new File("data/kml/placemark.kml").toURI().toURL();
		resource = new UrlResource(System.out, url, "http://www.opengis.net/kml/2.2");
		validator.validate(resource);
		assertEquals(2, validator.getFileCount());
        assertEquals(0, validator.getErrors());
        assertEquals(0, validator.getWarnings());
	}

	public void testBadXml() {
		XmlValidate validator = new XmlValidate();
        validator.setSummary(true);
        validator.setMap(new File("ns.map"));
        validator.validate(new File("data/bad/bad.xml"));
		validator.dumpStatus();
		assertEquals(1, validator.getFileCount());
        assertEquals(1, validator.getErrors());
        assertEquals(0, validator.getWarnings());
    }

	public void testBadKml() {
		XmlValidate validator = new XmlValidate();
		validator.setMap(new File("ns.map"));
		validator.setKmlMode(true);
		validator.setDumpLevel(1);
		validator.validate(new File("data/bad/badColor.kml"));
		validator.dumpStatus();
		assertEquals(1, validator.getFileCount());
		assertTrue(validator.getErrors() != 0);
        assertEquals(0, validator.getWarnings());
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
        validator.validate(new File("data/xml/cot.xml"));
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
		validator.setKmlMode(true);

		//validator.setVerbose();
		validator.validate(new File("data/kml"));
		validator.validate(new File("data/bad/zero.kml"));
		//validator.validate(new File("data/kmz"));
		validator.dumpStatus();

		assertEquals(9, validator.getFileCount());
		// assertEquals(2, validator.getErrors());
		// assertTrue(validator.getErrors() != 0);
		assertEquals(0, validator.getWarnings());
	}

	public void testGpxFile() {
		// Validate all GPX/KML/KMZ documents against the declared namespace
		XmlValidate validator = new XmlValidate();
		validator.setSummary(true);
		validator.setKmlMode(true);
		Set<String> extensionSet = validator.getExtensionSet();
		// add namespace map to validator
		validator.setMap(new File("ns.map"));
		extensionSet.clear();
		extensionSet.add("kml");
		extensionSet.add("kmz");
		extensionSet.add("gpx");
		validator.validate(new File("data"));
		assertEquals(18, validator.getFileCount());
		assertTrue(validator.getErrors() != 0);
        assertEquals(1, validator.getWarnings());
    }
}
