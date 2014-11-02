package org.mitre.xml.validate;

import org.jdom2.*;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Created by mathews on 11/2/2014.
 */
public class TestFileResource {

    private final SAXBuilder builder, validatingBuilder;

    public TestFileResource() {
        builder = new SAXBuilder();

        validatingBuilder = new SAXBuilder(XMLReaders.XSDVALIDATING);
        validatingBuilder.setFeature(XmlValidate.VALIDATION_FEATURE, true);
        validatingBuilder.setFeature(XmlValidate.SCHEMA_VALIDATION_FEATURE, true);
        validatingBuilder.setFeature(XmlValidate.SCHEMA_FULL_CHECKING_FEATURE, true);
        validatingBuilder.setFeature(XmlValidate.LOAD_DTD_GRAMMAR, false);
        validatingBuilder.setFeature(XmlValidate.LOAD_EXTERNAL_DTD, false);
        validatingBuilder.setFeature("http://xml.org/sax/features/external-general-entities", false);
        validatingBuilder.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    }

    @Test(expected = JDOMException.class)
    public void testBadXml() throws JDOMException, IOException {
        File file = new File("data/bad/bad.xml");
        FileResource res = new FileResource(System.out, file, null);
        res.getDocument(builder);
    }

    @Test
    public void testKml() throws JDOMException, IOException {
        File file = new File("data/kml/tessellate22.kml");
        FileResource res = getResource(file);
        res.setSummary(true);
        validatingBuilder.setErrorHandler(res);
        Document doc = res.getDocument(validatingBuilder);
        assertNotNull(doc);
        assertFalse(res.isKmzFile());

        assertEquals(0, res.getErrors());
        assertEquals(0, res.getWarnings());
        assertEquals(0, res.getStats().size());
    }

    @Test
    public void testBadKml() throws JDOMException, IOException {
        File file = new File("data/bad/badColor.kml");
        FileResource res = getResource(file);
        res.setSummary(true);
        validatingBuilder.setErrorHandler(res);
        Document doc = res.getDocument(validatingBuilder);
        assertNotNull(doc);
        assertFalse(res.isKmzFile());

        assertEquals(14, res.getErrors());
        assertEquals(0, res.getWarnings());
        assertEquals(5, res.getStats().size());
    }

    private FileResource getResource(File file) throws JDOMException, IOException {
        FileResource res = new FileResource(System.out, file, "http://www.opengis.net/kml/2.2");
        assertSame(file, res.getFile());

        // add schema location to root XML element so errors/warnings get generated
        Document doc = res.getDocument(builder);
        Element root = doc.getRootElement();
        root.setAttribute("schemaLocation", "http://www.opengis.net/kml/2.2 " +
                new File("schemas/kml22.xsd").toURI().toString(), XmlValidate.xsiNamespace);
        File outFile = new File("build/" + file.getName());
        FileWriter writer = new FileWriter(outFile);
        writer.write(res.getXmlContent());
        writer.close();
        return new FileResource(System.out, outFile, "http://www.opengis.net/kml/2.2");
    }

    @After
    public void tearDown() {
        validatingBuilder.setErrorHandler(null);
    }

}
