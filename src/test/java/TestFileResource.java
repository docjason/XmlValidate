import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.junit.Test;
import org.mitre.xml.validate.FileResource;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by mathews on 11/2/2014.
 */
public class TestFileResource {

    private final SAXBuilder builder;

    public TestFileResource() {
        builder = new SAXBuilder();
    }

    @Test(expected = JDOMException.class)
    public void testBadXml() throws JDOMException, IOException {
        File file = new File("data/bad/bad.xml");
        FileResource res = new FileResource(System.out, file, null);
        res.getDocument(builder);
    }

    @Test
    public void testBadKml() throws JDOMException, IOException {
        File file = new File("data/bad/badColor.kml");
        FileResource res = new FileResource(System.out, file, null);
        res.setSummary(true);
        Document doc = res.getDocument(builder);
        assertNotNull(doc);
        assertEquals(0, res.getErrors());
        assertEquals(0, res.getWarnings());
        assertTrue(res.getStats().isEmpty());
    }

}
