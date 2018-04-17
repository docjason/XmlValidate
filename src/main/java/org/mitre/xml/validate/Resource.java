/*
 *  Resource.java
 *
 *  (C) Copyright MITRE Corporation 2009
 *
 *  The program is provided "as is" without any warranty express or implied, including
 *  the warranty of non-infringement and the implied warranties of merchantibility and
 *  fitness for a particular purpose.  The Copyright owner will not be liable for any
 *  damages suffered by you as a result of using the Program.  In no event will the
 *  Copyright owner be liable for any special, indirect or consequential damages or
 *  lost profits even if the Copyright owner has been advised of the possibility of
 *  their occurrence.
 *
 */
package org.mitre.xml.validate;

import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.output.XMLOutputter;
import org.jdom2.input.SAXBuilder;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Abstract Resource object used by XmlValidator
 * whose implementation may be a URL or a File.
 * 
 * @author Jason Mathews, MITRE Corp.
 *  Date: Feb 13, 2009 7:07:51 PM
 *
 * Changes:
 *  03/23/09 Suppress printing error context for XML Schema errors as opposed
 *           to validation errors in the target XML source.
 */
public abstract class Resource implements ErrorHandler {

    protected Document doc;

    private String schemaNamespace;

	protected final String targetFile;

    private LineNumberReader lnr;

    private boolean schemaPrinted;

    private boolean summary;

    // used in validateFile/printFile to print file summary info once on errors or verbose mode
    private boolean printed;

    private String xmlContent;

    protected final PrintStream out;

    int warnings;

    int errors;

	protected final Set<String> stats = new HashSet<>();

    private int dumpLevel;
    private int dumpLimit;
    protected final boolean debug;
    private String defaultNamespace;

    public Resource(PrintStream out, String target, String schemaNamespace) {
        this.out = out;
        this.targetFile = target;
        this.schemaNamespace = schemaNamespace;
        debug = Boolean.getBoolean("debug");
    }

    public abstract String getSource();

    public int getErrors() {
        return errors;
    }

    public int getWarnings() {
        return warnings;
    }

    public void printFile() {
        if (!printed) {
            if (targetFile != null) out.println("\nCheck: " + targetFile);
            printed = true;
        }
        // dump XML for dump Level == 1 (on error condition)
         if (dumpLevel == 1)
            dumpContent();
    }

    public void dumpContent() {
        if (dumpLevel != 0 && xmlContent != null) {
            String outContent = xmlContent;
            if (dumpLimit > 0 && xmlContent.length() > dumpLimit)
                outContent = xmlContent.substring(0, dumpLimit) + "...";  // dump partial output
            out.println(outContent);
            out.println();
            dumpLevel = 0; // don't dump again
        }
    }

    public abstract Document getDocument(SAXBuilder builder) throws JDOMException, IOException;

	public Set<String> getStats() {
		return stats;
	}
    
    private void handleException(String s, SAXParseException exception) {
        if (summary) {
			/*
			cvc-attribute.3: The value '6742738' of attribute 'id' on element 'Placemark' is not valid with respect to its type, 'ID'.
			cvc-complex-type.2.4.a: Invalid content was found starting with element 'LatLonAltBox'. One of '{"http://www.opengis.net/kml/2.2":RegionSimpleExtensionGroup, "http://www.opengis.net/kml/2.2":RegionObjectExtensionGroup}' is expected.
			cvc-complex-type.2.4.d: Invalid content was found starting with element 'Style'. No child element is expected at this point.
			cvc-complex-type.3.2.2: Attribute 'maxLength' is not allowed to appear in element 'Snippet'.
			cvc-datatype-valid.1.2.1: '#ff0000ff' is not a valid value for 'hexBinary'.
			cvc-enumeration-valid: Value 'relative' is not facet-valid with respect to enumeration '[clampToGround, relativeToGround, absolute]'. It must be a value from the enumeration.
			cvc-id.2: There are multiple occurrences of ID value 'placemarkAlgeria'.
			cvc-length-valid: Value '000000' with length = '3' is not facet-valid with respect to length '4' for type 'color'.
			cvc-maxInclusive-valid: Value '270.0' is not facet-valid with respect to maxInclusive '1.8E2' for type 'angle180Type'.
			cvc-type.3.1.2: Element 'description' is a simple type, so it must have no element information item [children].
			 */
			String message = exception.getMessage();
			int ind = message.indexOf(':');
			if (ind > 0) message = message.substring(0,ind);
			stats.add(s + ": " + message);
			return;
		}
		printFile();
        if (!schemaPrinted && schemaNamespace != null) {
            out.println(schemaNamespace);
            schemaPrinted = true;
        }
        if (debug) exception.printStackTrace();
        int lineNumber = exception.getLineNumber();
        out.print(s + ": SAXParseException " + exception
                + "\nLine: " + lineNumber
                + ", column: " + exception.getColumnNumber());
        String pubId = exception.getPublicId();
        if (pubId != null) out.println(", publicId=" + pubId);
        String sysId = exception.getSystemId();
        if (sysId != null) out.println(", systemId=" + sysId);
        out.println();
        // if systemId not null then assume error is in XML Schema not XML source
        // so don't try to show error context in XML source.
        // TODO: can systemId be non-null and have error in XML source ??
        if (lnr != null && lineNumber != -1 && sysId == null) {
            try {
                String line = null;
                // line numbers show be in increasing order so should never have to backtrack
                if (lnr.getLineNumber() > lineNumber) {
                    lnr.reset();
                    if (debug) System.err.println("DEBUG: reset line number: " + lnr.getLineNumber());
                }
                while (lnr.getLineNumber() < lineNumber && (line = lnr.readLine()) != null) {
                    // skip lines until we reach target line number
                }
                if (line != null && line.length() != 0) {
                    int col = exception.getColumnNumber() - 1;
                    //out.println("col=" + col + " ll="+line.length());
                    if (col > 0 && col <= line.length()) {
                        if (col > 80)
                            line = "..." + line.substring(col - 50, col) + "***" + line.substring(col);
                        else
                            line = line.substring(0, col) + "***" + line.substring(col);
                    } // else out.format("linelen: %d col: %d%n", line.length(), col);
                    line = line.trim();
                    if (line.length() > 80)
                        line = line.substring(0, 78) + "...";
                    out.format("%d: %s%n", lineNumber, line);
                }
            } catch (IOException e) {
                if (debug) e.printStackTrace();
                // otherwise ignore
            }
        }
    }

    public void warning(SAXParseException exception) throws SAXException {
        handleException("WARN", exception);
        warnings++;
    }

    public void error(SAXParseException exception) throws SAXException {
        handleException("ERROR", exception);
        errors++;
    }

    public void fatalError(SAXParseException exception) throws SAXException {
        handleException("FATAL", exception);
        errors++;
    }

    public String getXmlContent() {
        if (xmlContent == null) {
            XMLOutputter xo = new XMLOutputter();
            xo.setFormat(org.jdom2.output.Format.getPrettyFormat());

            // TODO: non-UTF8 encoding is overriden with UTF-8 type

            xmlContent = xo.outputString(doc);
            lnr = new LineNumberReader(new StringReader(xmlContent));
        }
        return xmlContent;        
    }

    public String getDefaultNamespace() {
		return defaultNamespace;
    }

    public void setSchemaNamespace(String schemaNamespace) {
        this.schemaNamespace = schemaNamespace;
    }

    public void setDumpLevel(int dumpLevel) {
        this.dumpLevel = dumpLevel;
    }

    public void setDumpLimit(int dumpLimit) {
        this.dumpLimit = dumpLimit;
    }

    public void setSummary(boolean summary) {
        this.summary = summary;
    }

    public void setDefaultNamespace(String defaultNamespace) {
		this.defaultNamespace = defaultNamespace;
    }

    public void close() {
        if (lnr != null)
            try {
                lnr.close();
            } catch (IOException e) {
                // ignore
            }
    }

    /*
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        //out.println("entity=" + publicId + " " + systemId);
        return null;
    }
    */

	public boolean isPrinted() {
		return printed;
	}

}
