/*
 *  XmlValidate.java
 *
 *  (C) Copyright MITRE Corporation 2009
 *
 *  The program is provided "as is" without any warranty express or implied, including
 *  the warranty of non-infringement and the implied warranties of merchantability and
 *  fitness for a particular purpose.  The Copyright owner will not be liable for any
 *  damages suffered by you as a result of using the Program.  In no event will the
 *  Copyright owner be liable for any special, indirect or consequential damages or
 *  lost profits even if the Copyright owner has been advised of the possibility of
 *  their occurrence.
 *
 */
package org.mitre.xml.validate;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jdom2.*;
import org.jdom2.input.JDOMParseException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;

/**
 * XmlValidate validates XML documents in several methods depending on the task
 * in hand.  XML documents are validated against provided XML schema instance documents in
 * three different approaches.
 *
 * First documents are checked as being well-formed XML.  If parsing
 * with non-validating parser fails then validation stops there.
 *
 * Next there are 3 methods to validate XML documents:
 *
 * 1) -map option maps schema namespaces to given target schema locations.
 * Rewrites XML with location of schema instance document then validates against
 * target schema.  If schema namespace not available then it does not validate
 * the document.  This validates document against the actual schema namespace
 * defined in the document as opposed to the other approaches that rewrite
 * the XML documents to a target schema. You can use -schemaLocation argument
 * to explicitly add an individual schema location mapping.
 *
 *  old: <kml xmlns="http://earth.google.com/kml/2.1">
 *
 *  new: <kml xmlns="http://earth.google.com/kml/2.1"
 * 		xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 * 		xsi:schemaLocation="http://earth.google.com/kml/2.1 file:/C:/xml/kml21.xsd">
 *
 * Note in this mode if the root element is "kml" and it has no default namespace then
 * namespace is set to http://earth.google.com/kml/2.0 which is then used
 * to validate the document.
 *
 * 2) Specifying a schema but no namespace implies "noNamespaceSchemaLocation"
 * to add to all XML documents and validate against that schema.  
 *
 *  no namespace schema:
 *      <event xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 *       xsi:noNamespaceSchemaLocation="C:/cot/schema/Event.xsd">
 *
 * 3) And finally specify -ns and -schema options to provide a target namespace URI
 * and schema location, which overrides the declared namespace.  All XML documents
 * specified in search location(s) will be rewritten and the target schemaLocation
 * will be added regardless of the default namespace of that document.  For example,
 * KML 2.1 documents can be validated against the 2.2 schema, and vice versa.
 *
 * <pre>
 * 		-schema=C:/pathToXsd/kml22.xsd -ns=http://www.opengis.net/kml/2.2
 * </pre>
 *
 * Note that XML is reformatted so errors/warnings with line/column numbers are respect to
 * the reformatted content not the original but context is printed at that line/column number
 * after each error if applicable so errors can be tracked down and corrected in original
 * XML document.  If you want the reformatted XML document printed then enable -dump option.
 * If the error is in the XML Schema not the instance document then the context will not
 * be printed. 
 *
 * @author Jason Mathews, MITRE Corp.
 * Date: Apr 17, 2008 2:33:56 PM
 *
 * History:
 *  4/17/08 Original version
 *  2/01/09 Added -map option
 *  2/13/09 Added additional namespace locations in schemaLocation if locations defined in map
 *          Added -x option and support for KMZ files
 *          Added -dump argument
 *          Added support to validate from URLs in addition to file system
 *  3/07/09 Added check for "kml" as root element with no namespace in -map mode and use KML 2.0 namespace
 *  3/23/09 Added -debug argument
 *          load all validation targets into list processing after all options are set
 *          if target file does not exist then try to fetch as a URL
 * 11/05/09 Added special check for beta namespace http://earth.google.com/kml/2.2
 *          and auto-change to OGC namespace
 * 08/23/10 Added assignment of namespace locations to non-root elements in -map mode
 * 05/13/11 Added kml mode for special KML handling
 * 			Assume KML 2.0 instance if root element doesn't have a default namespace
 * 			and root element is one of following: { Placemark, GroundOverlay, NetworkLink, ScreenOverlay }
 * 			Added check for KML content in files with .kmz extension. Retry such files as text.
 * 11/14/13 Migrate JDOM 1.1 to JDOM 2.0.5
 * 05/23/14 Add -schemaLocation argument to add individual namespace-to-schema mappings
 *
 * @see http://www.w3.org/TR/xmlschema-0/
 *
 * TODO: keep track of normalized error messages for summary
 */
public class XmlValidate implements ErrorStatus {

    /** Validation feature id */
    protected static final String VALIDATION_FEATURE =
            "http://xml.org/sax/features/validation";

    /** Schema validation feature id */
    protected static final String SCHEMA_VALIDATION_FEATURE =
            "http://apache.org/xml/features/validation/schema";

    /** Schema full checking feature id */
    protected static final String SCHEMA_FULL_CHECKING_FEATURE =
            "http://apache.org/xml/features/validation/schema-full-checking";

    protected static final String LOAD_DTD_GRAMMAR =
            "http://apache.org/xml/features/nonvalidating/load-dtd-grammar";  // [TRUE]

    protected static final String LOAD_EXTERNAL_DTD =
            "http://apache.org/xml/features/nonvalidating/load-external-dtd"; // [TRUE]

    protected static final String CONTINUE_AFTER_FATAL_FEATURE =
            "http://apache.org/xml/features/continue-after-fatal-error"; // [FALSE]

    protected static final Namespace xsiNamespace = Namespace.getNamespace("xsi",
            "http://www.w3.org/2001/XMLSchema-instance" );

	private static final String VALID_PREFIX = "*valid* ";
	private static final String VALID_XMLNS_PREFIX = VALID_PREFIX + "xmlns=";
	// private static final String NS_GOOGLE_KML_EXT = "http://www.google.com/kml/ext/2.2";

	private java.util.Map<String, String> schemaMap;

    private final Set<String> extensionSet = new HashSet<>();

	private final java.util.Map<String, Set<String>> errorMap = new HashMap<>();

    private final SAXBuilder builder;
    private final SAXBuilder validatingBuilder;
    private String schemaUri;
    private String schemaNamespace;

    private boolean verbose;
    private boolean summary;
    private boolean debug;
    private int dumpLevel;
    private int dumpLimit;

    private int warnings;
    private int errors;
    private int fileCount;

    private long startTime;
    private String homeDir = ".";

    private PrintStream out = System.out;
    private int validFiles;

    private final Map<String, Integer> stats = new TreeMap<>();
	private boolean kmlMode, kmzMode;

	private static final Set<String> KML_ELEMENTS = new HashSet<>(5);

	static {
		// possible non-kml root elements in KML document with no namespace
		KML_ELEMENTS.add("Placemark");
		KML_ELEMENTS.add("GroundOverlay");
		KML_ELEMENTS.add("NetworkLink");
		KML_ELEMENTS.add("ScreenOverlay");
		KML_ELEMENTS.add("PhotoOverlay");
	}

	public XmlValidate() {
        extensionSet.add("xml"); // default target: XML documents only
        builder = new SAXBuilder();
        //parser = new DOMParser();
        //try {
        builder.setFeature(VALIDATION_FEATURE, false);
        builder.setFeature(SCHEMA_FULL_CHECKING_FEATURE, false);
        builder.setFeature(SCHEMA_VALIDATION_FEATURE, false);
        builder.setFeature(LOAD_DTD_GRAMMAR, false);
        builder.setFeature(LOAD_EXTERNAL_DTD, false);
		builder.setFeature("http://xml.org/sax/features/external-general-entities", false);
		builder.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		// builder.setFeature("http://xml.org/sax/features/namespaces", true);

        /*
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setValidating(true);
        spf.setNamespaceAware(true);
        try {
            validatingBuilder = spf.newSAXParser();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }
        */

        validatingBuilder = new SAXBuilder(XMLReaders.XSDVALIDATING);

        //validatingBuilder.setValidation(true);
        //validatingBuilder.setEntityResolver(this);

        //validatingParser = new DOMParser();
        validatingBuilder.setFeature(VALIDATION_FEATURE, true);
        validatingBuilder.setFeature(SCHEMA_FULL_CHECKING_FEATURE, true);
        validatingBuilder.setFeature(SCHEMA_VALIDATION_FEATURE, true);
        validatingBuilder.setFeature(LOAD_DTD_GRAMMAR, false);
        validatingBuilder.setFeature(LOAD_EXTERNAL_DTD, false);
        // validatingBuilder.setExpandEntities(false);
        // validatingBuilder.setFeature("http://apache.org/xml/features/validation/unparsed-entity-checking", false);
        // validatingBuilder.setFeature("http://xml.org/sax/features/external-general-entities", false);
        // validatingBuilder.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        // validatingBuilder.setFeature(CONTINUE_AFTER_FATAL_FEATURE, false);
    }

	public Set<String> getExtensionSet() {
		return extensionSet;
	}

	public void setKmlMode(boolean kmlMode) {
		this.kmlMode = kmlMode;
	}

	public void setKmzMode(boolean kmzMode) {
		this.kmzMode = kmzMode;
	}

	public void setSummary(boolean summary) {
		this.summary = summary;
	}

	/**
	 * Set dump level to print reformatted XML documents.
	 * 			0 -> no output [default],
	 * 			1 -> print KML on errors only,
	 * 			2 -> print all inputs");
	 * @param dumpLevel
	 */
	public void setDumpLevel(int dumpLevel) {
		this.dumpLevel = dumpLevel;
	}

	@Override
	public void addWarning(String s) {
		if(s != null) out.println(s);
		warnings++;
	}

	@Override
	public void addError(String s) {
		if(s != null) out.println(s);
		errors++;
	}

	public int getWarnings() {
		return warnings;
	}

	public int getErrors() {
		return errors;
	}

	public int getFileCount() {
		return fileCount;
	}

    private void addStatus(String error, Exception e) {
		String message = e.getMessage();
		if (e instanceof JDOMParseException) {
			int ind = message.lastIndexOf(": ");
			if (ind > 0) message = message.substring(ind + 2);
		}
		addStatus(error + ": " + e.getClass().getName() + ": " + message);
    }

	private void addStatus(Resource res, String key) {
		Integer count = stats.get(key);
        if (count == null) {
			count = Integer.valueOf(1); // first occurrence of this key
			if (res != null && key.startsWith("ERROR:")) {
				// e.g. ERROR: cvc-complex-type.2.1
				res.printFile();
				out.println(key);
			}
		}
        else count = count + 1; // increment counter
        stats.put(key, count);
	}

    private void addStatus(String key) {
		addStatus(null, key);
    }

    public void validate(File file) {
        if (file.isDirectory()) {
            if (verbose) out.println("dir: " + file);
            for (File f : file.listFiles()) {
                if (f.isDirectory()) {
                    if (!f.getName().equals(".svn")) // don't recurse .svn directories
                        validate(f);
                } else {
                    String name = f.getName().toLowerCase(Locale.ROOT);
                    int ind = name.lastIndexOf('.');
                    if (ind != -1 && extensionSet.contains(name.substring(ind + 1))) {
                        if (f.length() == 0) {
							if (summary) {
								addStatus("ERROR: zero length file");
								addStatusError(f.toString(), "zero length file");
							} else {
								if (verbose) out.println("\nSkip: " + f);
								if (debug) System.out.println("skip zero length file: " + f);
							}
                            continue;
                        }
                        // if (f.getName().endsWith(".xml") || f.getName().endsWith(".kml"))
						final FileResource resource = new FileResource(out, f, schemaNamespace);
						validate(resource);
						if (kmzMode && resource.isKmzFile()) {
							checkKmzResource(f);
						}
                    }
                }
            }
            return;
        }

		// skip over zero-length files
		if (file.length() == 0) {
			if (summary) {
				addStatus("ERROR: zero length file");
				addStatusError(file.toString(), "zero length file");
			} else {
				if (verbose) out.println("\nSkip: " + file);
				if (debug) System.out.println("skip zero length file");
			}
			return;
		}

		final FileResource resource = new FileResource(out, file, schemaNamespace);
		validate(resource);
		if (kmzMode && resource.isKmzFile()) {
			checkKmzResource(file);
		}
    }

	private void checkKmzResource(File file) {
		try(
			KmzExplorer visitor = new KmzExplorer(out, file, schemaNamespace, this);
		) {
			Resource res;
			while ((res = visitor.next()) != null) {
				validate(res);
			}
		} catch (IOException e) {
			out.println("\tparse failed: " + e);
		}
	}

	public boolean validate(Resource res) {
        if (verbose) res.printFile();
        // record time when validation process starts
        if (fileCount++ == 0) startTime = System.currentTimeMillis();

        if (summary) res.setSummary(true);

        try {
            Document doc = getDocument(res);
            if (doc != null) {
                schemaValidation(res);
				return true;
			}
        } catch (OutOfMemoryError e) {
            res.printFile();
            out.println("\tparse failed: " + e);
            System.gc();
        } catch (JDOMParseException e) {
            // if we get here then document is not well-formed and getDocument failed
		   /*
				JDOMParseException: Error on line 1 of document file:/C:/kml/data/RestauranteGamboa.kml: The entity "nbsp" was referenced, but not declared.
				JDOMParseException: Error on line 35 of document file:/C:/kml/data/sites_default_files_GERS047_20-_20AOI3.kml: The prefix "gx" for element "gx:balloonVisibility" is not bound.
				JDOMParseException: Error on line 36687 of document file:/C:/kml/data/fewo4you.kml: Invalid byte 2 of 2-byte UTF-8 sequence.
			 */
            if (summary) {
                addStatus("ERROR", e);
	            // TODO: keep track of normalized error messages for summary
				addStatusError(res, "parse failed: JDOMParseException: " + getNestedMessage(e));
            }  else {
				res.printFile();
                out.format("\tparse failed: JDOMParseException: %s at line: %d column: %d%n",
                        e.getMessage(), e.getLineNumber(), e.getColumnNumber());
				if (debug) e.printStackTrace();
			}
			errors++;
        } catch (JDOMException e) {
            if (summary) {
                addStatus("ERROR", e);
				addStatusError(res, String.format("parse failed: %s: %s%n",
						getExceptionName(e), getNestedMessage(e)));
            } else {
				res.printFile();
                out.println("\tparse failed: " + e);
                if (debug) e.printStackTrace();
            }
            errors++;
        } catch (IOException e) {
            if (summary) {
                addStatus("ERROR", e);
				addStatusError(res, "parse failed: " + e);
			} else {
				res.printFile();
				out.println("\tparse failed: " + e);
				if (debug) e.printStackTrace();
			}
			errors++;
        } finally {
            for (String msg : res.getStats()) {
                addStatus(res, msg);
            }
        }

        return false;
    }

	private void addStatusError(Resource res, String err) {
		if (res.isPrinted()) {
			// output for file already dumped with an error/warning so just dump the new error and continue
			// otherwise append error to error map and dump at end
			out.println("-  " + err);
			return;
		}
		addStatusError(res.getSource(), err);
	}

	private void addStatusError(String source, String err) {
		//String source = res.getSource();
		Set<String> sources = errorMap.get(err);
		if (sources == null) {
			sources = new HashSet<>();
			errorMap.put(err, sources);
		}
		sources.add(source);
	}

	private static String getExceptionName(JDOMException e) {
		String name = e.getClass().getName();
		int ind = name.lastIndexOf('.');
		if (ind != -1) name = name.substring(ind + 1);
		return name;
	}

	private static String getNestedMessage(Exception e) {
		String msg = e.getMessage();
		// if message has appended nested message then skip outer message
		int ind = msg.indexOf(": ");
		if (ind != -1) return msg.substring(ind+2);
		return msg;
	}

	private Document getDocument(Resource res) throws JDOMException, IOException {
        Document doc = res.getDocument(builder);
        Iterator<Content> it = doc.getDescendants();
        //boolean modified = false;
        while (it.hasNext()) {
			Content obj = it.next();
            if (obj instanceof DocType) {
                //DocType docType = (DocType)obj;
                it.remove();
            }
        }
        Element root = doc.getRootElement();
        Namespace rootNS = root.getNamespace();
        Attribute schemaLocation = root.getAttribute("schemaLocation", xsiNamespace);

        //List namespaces = root.getAdditionalNamespaces();
        //List attrs = root.getAttributes();
        //if (hasSchemaNameSpace)
        //    root.setAttribute("schemaLocation", schemaUri, xsiNamespace);

		if (summary) {
			final String name = root.getName();
			addStatus("root element=" + name);
			if (kmlMode && (name == null || !name.equals("kml") && !KML_ELEMENTS.contains(name))) {
				res.printFile();
				out.println("non-kml root element: " + name);
				addStatus("non-kml root element");
			}
			if (rootNS == null) {
				// can rootNS be null? or if no default namespace do we get the static default namespace object ??
				// is there a distinction btwn no root namespace and no default namespace ??
				res.printFile();
				out.println("xmlns=no root namespace");
				addStatus("xmlns=no root namespace");
				res.setDefaultNamespace("no root namespace");
			} else {
				final String uri = rootNS.getURI();
				if (uri.isEmpty()) {
					res.printFile();
					out.println("no default namespace");
					addStatus("xmlns=no default namespace");
				} else {
					addStatus("xmlns=" + uri);
					res.setDefaultNamespace(uri);
					if (kmlMode && !uri.contains("/kml")) {
						res.printFile();
						out.println("non-kml root namespace: " + uri); // debug for kml testing
					}
				}
			}
		}

		// first check if we use schema mapping to find associated Schema location for the namespace
        if (schemaMap != null) {
			// rootNS should never be null. Even if no default namespace should have
			// empty rootNS with empty string as its namespace prefix and associated URI.
            if (rootNS == null) {
				if (!summary) {
					res.printFile();
					out.println("INFO: no root namespace");
				}
                return null;
            }
            schemaNamespace = rootNS.getURI();
            if (schemaNamespace == null || schemaNamespace.isEmpty()) {
				if (!summary) {
					res.printFile();
					out.println("INFO: no root namespace");
				}
                // if "kml" root element and no default namespace then use KML 2.0 namespace
				// and associated schema otherwise cannot validate XML document.
				final String rootName = root.getName();
				if ("kml".equals(rootName) || kmlMode && KML_ELEMENTS.contains(rootName)) {
                    schemaNamespace = "http://earth.google.com/kml/2.0";
                    Namespace ns = Namespace.getNamespace(schemaNamespace);
                    changeNamespace(root, ns);
                } else {
                    return null;
                }
            } else if (schemaNamespace.equals("http://earth.google.com/kml/2.2")) {
                // http://earth.google.com/kml/2.2 was a beta pre-OGC namespace: map to real namespace
                // and update the DOM to reflect new namespace
                schemaNamespace = "http://www.opengis.net/kml/2.2";
                Namespace ns = Namespace.getNamespace(schemaNamespace);
                changeNamespace(root, ns);
            }
            String schemaLoc = schemaMap.get(schemaNamespace);
            if (schemaLoc == null) {
                res.printFile();
                out.println("INFO: namespace not registered: " + schemaNamespace);
				if (summary) addStatus("INFO: namespace not registered");
                return null;
            }
            /* schemaLocation attribute value consists of one or more pairs of URI references,
             * separated by white space. The first member of each pair is a namespace name,
             * and the second member of the pair is a hint describing where to find an
             * appropriate schema document for that namespace.
             */
            res.setSchemaNamespace(schemaNamespace);
            StringBuilder schemaLocBuf = new StringBuilder();
            schemaLocBuf.append(schemaNamespace).append(' ').append(schemaLoc);
            List<String> namespaces = new LinkedList<>();
            namespaces.add(schemaNamespace);
            // for each namespace check if defined in map
            // boolean hasGx = false;
            for (Namespace ns : root.getAdditionalNamespaces()) {
                String nsURI = ns.getURI();
				if (nsURI.isEmpty()) {
					// The empty string, though it is a legal URI reference, cannot be used as a namespace name.
					continue;
				}
                schemaLoc = schemaMap.get(nsURI);
                // if ("gx".equals(ns.getPrefix()) || NS_GOOGLE_KML_EXT.equals(nsURI)) hasGx = true;
                if (schemaLoc != null) {
                    if (verbose)
                        out.format("getAdditionalNamespace %s -> %s%n", nsURI, schemaLoc);
                    //root.removeNamespaceDeclaration(ns);
                    //root.addNamespaceDeclaration(Namespace.getNamespace(ns.getPrefix(), ns.getURI() + " " + schemaLoc));
                    // e.g.  xmlns:gx="http://www.google.com/kml/ext/2.2">
                    schemaLocBuf.append(' ').append(nsURI).append(' ').append(schemaLoc);
                    namespaces.add(nsURI);
                } else if (verbose && !"http://www.w3.org/2001/XMLSchema-instance".equals(nsURI)) {
					addWarning("WARN: Cannot find location of schema: " + nsURI);
				}
                // root.setAttribute(ns.getPrefix(), ns.getURI() + " " + schemaLoc, xmlns);
                // root.setNamespace(Namespace.getNamespace(ns.getPrefix(), ns.getURI() + " " + schemaLoc));
            }
		/*
				if ("kml".equals(root.getName()) && !hasGx) {
				String nsURI = NS_GOOGLE_KML_EXT;
				schemaLoc = schemaMap.get(nsURI);
				if (schemaLoc != null) {
					// auto-add gx namespace
					root.addNamespaceDeclaration(Namespace.getNamespace("gx", nsURI));
					namespaces.add(nsURI);
					schemaLocBuf.append(' ').append(nsURI).append(' ').append(schemaLoc);
				}
			}
		*/
            root.setAttribute("schemaLocation", schemaLocBuf.toString(), xsiNamespace);
            // if namespace declared on non-root elements then lookup schemaLocation locations
            for (Element child : root.getChildren()) {
				checkNamespace(child, namespaces);
            }
        } else if (schemaNamespace != null) {
            // next check if XML document needs to change to user-defined target namespace
            // xsi:schemaLocation="http://www.opengis.net/kml/2.2 file:/C:/xml/kml22.xsd"
            // xsi:schemaLocation="http://www.opengis.net/kml/2.2 http://schemas.opengis.net/kml/2.2.0/ogckml22.xsd"
            Namespace ns = Namespace.getNamespace(schemaNamespace);
            // System.err.println("schemaLoc=" + schemaNamespace);
            // System.err.println("rootNS=" + rootNS);
            if (ns.equals(rootNS)) {
                if (verbose)
                    System.err.println("same target namespace: " + ns.getURI());
            } else {
                if (verbose)
                    System.err.println("change namespace: "
                            + (rootNS == null ? "<null>" : rootNS.getURI())
                            + " -> " + ns.getURI());
                changeNamespace(root, ns);
            }
            root.setAttribute("schemaLocation", schemaNamespace + " "
                    + schemaUri, xsiNamespace);
        } else {
            // xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            // xsi:noNamespaceSchemaLocation="C:/cot/xsd/Event.xsd">
            if (schemaLocation != null) {
                schemaLocation.detach();
                System.err.println("\tdetach schemaLocation: "
                        + schemaLocation.getValue());
            }
			if (schemaUri != null) {
            	root.setAttribute("noNamespaceSchemaLocation", schemaUri,
                    xsiNamespace);
			}
        }

        return doc;
    }

    private void schemaValidation(Resource res) throws IOException, JDOMException {
        String xmlContent = res.getXmlContent();

        res.setDumpLevel(dumpLevel);
        res.setDumpLimit(dumpLimit);
        if (dumpLevel == 2) {
            // dump KML for all inputs
            res.printFile();
            res.dumpContent();
        }

        try {
            //schemaPrinted = false;
            validatingBuilder.setErrorHandler(res);
            // validatingBuilder.setEntityResolver(res);
            validatingBuilder.build(new StringReader(xmlContent));
            if (res.errors == 0) {
                validFiles++; // no errors
                if (verbose) out.println("\t *OK*");

                if (summary) {
                    String defaultNamespace = res.getDefaultNamespace();
                    if (defaultNamespace == null) defaultNamespace = "no default namespace";
                    addStatus(VALID_XMLNS_PREFIX + defaultNamespace);
                }
            }
        } finally {
            res.close();
            errors += res.errors;
            warnings += res.warnings;
        }
    }

    // recursively change namespace in all elements
    // TODO: this should only change the default/root element namespace not
    // blindly changing all namespaces in all elements
    private static void changeNamespace(Element parent, Namespace ns) {
        for (Element child : parent.getChildren()) {
			changeNamespace(child, ns);
        }
        parent.setNamespace(ns);
    }

    private void checkNamespace(Element parent, List<String> namespaces) {
        List<String> localNamespaces = new LinkedList<>(namespaces);
        StringBuilder schemaLocBuf = new StringBuilder();
        Namespace ns = parent.getNamespace();
        if (ns != null) {
            String nsURI = ns.getURI();
            if (!namespaces.contains(nsURI)) {
                String schemaLoc = schemaMap.get(nsURI);
                if (schemaLoc != null) {
                    schemaLocBuf.append(nsURI).append(' ').append(schemaLoc);
                    localNamespaces.add(nsURI);
                    if (verbose) out.format("assign Namespace %s -> %s%n", nsURI, schemaLoc);
                }
            }
        }
        // for each namespace check if defined in map
        for (Namespace item : parent.getAdditionalNamespaces()) {
            String nsURI = item.getURI();
            if (namespaces.contains(nsURI)) continue;
            String schemaLoc = schemaMap.get(nsURI);
            if (schemaLoc != null) {
                if (schemaLocBuf.length() != 0) schemaLocBuf.append(' ');
                schemaLocBuf.append(nsURI).append(' ').append(schemaLoc);
                localNamespaces.add(nsURI);
                if (verbose) out.format("assign Namespace %s -> %s%n", nsURI, schemaLoc);
            }
        }
        if (schemaLocBuf.length() != 0) {
            parent.setAttribute("schemaLocation", schemaLocBuf.toString(), xsiNamespace);
        }
        for (Element child : parent.getChildren()) {
			checkNamespace(child, localNamespaces);
        }
    }

	public void addSchemaLocation(String ns, String schemaLocation) {
		if (schemaMap == null) schemaMap = new HashMap<>();
		schemaMap.put(ns, schemaLocation);
	}

    /**
     *
     * @param file
     */
    public void setMap(File file) {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(file));
            schemaMap = new HashMap<>();
            String s;
            while ((s = in.readLine()) != null) {
                if (s.length() == 0 || s.startsWith("#"))
                    continue;
                int ind = s.indexOf('=');
                // expecting lines of the form:
                // namespace-uri = schema-location abs-file-path or URL
                if (ind < 1) continue;
                String ns = s.substring(0, ind).trim();
                String schemaLocation = s.substring(ind + 1).trim();
                if (schemaLocation.startsWith("${XV_HOME}") && homeDir != null) {
					String old = schemaLocation;
                    schemaLocation = homeDir + schemaLocation.substring(10);
					if (debug) System.err.printf("XXX: add %s > %s%n", old, schemaLocation);
				}
                if (!schemaLocation.startsWith("http:")) {
                    File loc = new File(schemaLocation); // .replace("\\","/")
                    if (loc.exists())
                        schemaLocation = loc.getAbsoluteFile().toURI().toString();
                    // otherwise is it a URL or bad file name ??
                    else System.err.println("INFO: " + schemaLocation + " does not exist locally");
                }
				if (debug) out.printf("Set %s -> %s%n", ns, schemaLocation);
                schemaMap.put(ns, schemaLocation);
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            if (in != null)
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
        }
        if (verbose)
            System.err.println(schemaMap);
    }

    public void setSchema(String schemaUri) {
        this.schemaUri = schemaUri;
    }

    public void setSchema(File schemaFile) {
        schemaUri = schemaFile.getAbsoluteFile().toURI().toASCIIString();
    }

    public void setNamespace(String schemaNamespace) {
        this.schemaNamespace = schemaNamespace;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = true;
    }

    public void setHomeDir(String homeDir) {
        File dir = null;
        if (homeDir != null) {
            dir = new File(homeDir);
        }
        if (dir != null && dir.isDirectory()) {
            try {
                this.homeDir = dir.getCanonicalPath();
				if (debug) System.err.println("XXX: set home dir=" + homeDir);
			} catch (IOException e) {
                this.homeDir = dir.getAbsolutePath();
            }
        } else {
            System.err.println("Invalid home directory: " + homeDir);
            usage();
        }
    }

    public void setOutputStream(PrintStream out) {
        this.out = out;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
        System.setProperty("debug", Boolean.toString(debug)); // set debugging mode
    }

    public void dumpStatus() {
		long elapsed = System.currentTimeMillis() - startTime;

		if (summary && !errorMap.isEmpty()) {
			for (Iterator<String> it  = errorMap.keySet().iterator(); it.hasNext(); ) {
				String key = it.next();
				Set<String> sources = errorMap.get(key);
				if (sources.size() == 1) {
					out.println("\nCheck: " + sources.iterator().next());
					out.println("-  " + key);
					it.remove();
					//errorMap.remove(entry.getKey());
				}
			}
			// dump multi-file lists
			for (Map.Entry<String,Set<String>> entry : errorMap.entrySet()) {
				out.println("\nERROR: " + entry.getKey());
				for (String source : entry.getValue()) {
					out.println("  " + source);
				}
			}
			out.println("\n-----------------------------------------------------------------------------");
		}

		out.format("%nErrors: %d  Warnings: %d  Files: %d  Time: %d ms%n",
				errors, warnings, fileCount, elapsed);
		if (fileCount > 0) {
			out.format("Valid files %d/%d (%.0f%%)%n", validFiles,
					fileCount, 100.0 * validFiles / fileCount);
		}
		if (!stats.isEmpty()) {
			out.println("\nSummary:");
			for (Map.Entry<String, Integer> entry : stats.entrySet()) {
				final String key = entry.getKey();
				// (Valid) xmlns=http://www.opengis.net/kml/2.2
				if (key.startsWith(VALID_XMLNS_PREFIX)) continue;
				final Integer value = entry.getValue();
				if (key.startsWith("xmlns=")) {
					Integer validCount = stats.get(VALID_PREFIX + key);
					if (validCount != null) {
						out.format("%5d %-35s \tvalid: %3d (%2.0f%%)%n", value, key, validCount, 100.0 * validCount / value);
						continue;
					}
				}
				out.format("%5d %s%n", value, key);
			}
		}
	}

    private static void usage() {
        System.err.println("Usage: XmlValidate [options] [-map=file] | [-schema=file (-ns=uri)] <xml-document file, directory, or URL...>\n");
        System.err.println("Options:");
        System.err.println("\t[-map=<schema property file>    - schema map properties: namespace to file/URI mappings");
        System.err.println("\t[-schema=<path-to-xml-schema>   - set target schema");
		System.err.println("\t[-schemaLocation=ns=location    - add/override namespace to schema location mapping.");
		System.err.println("\t                                  Location can be a URL or file path to the schema file.");
		System.err.println("\t                                  Multiple -schemaLocation arguments may be provided");
        System.err.println("\t[-ns=schemaLocation namespace]  - set schemaLocation namespace (e.g. http://earth.google.com/kml/2.1)");
        System.err.println("\t[-dump[=n]]                     - print reformatted XML documents: dump=0 -> no output [default],");
        System.err.println("\t                                  1 -> print KML on errors only, 2 -> print all inputs");
        System.err.println("\t                                  if number not specified then 1 is assumed otherwise 0");
        System.err.println("\t-maxDump=n                      - set max length (in bytes) of XML output used for each document in dump");
		System.err.println("\t[-K]                            - KML mode for special KML validation");
		System.err.println("\t[-Z]                            - KMZ mode checks all kml files inside KMZ files");
        System.err.println("\t[-kml]                          - validate .kml files only");
        System.err.println("\t[-kmz]                          - validate .kml or .kmz files only");
        System.err.println("\t[-x=ExtensionList]              - add additional file extensions to list (default=xml)");
        System.err.println("\t                                  extensions separated by ':' (e.g. -x=gpx:x3d:svg)");
        System.err.println("\t[-S]                            - enable summary mode to only show the final total counts");
        System.err.println("\t[-v[=true]]                     - enable verbose mode");
        System.err.println("\t[-debug]                        - enable debug mode to print exception stack trace");
        System.err.println("\nExamples:");
        System.err.println("\t1) To check all .kml files against target KML 2.2 schema regardless of default schema used:");
        System.err.println("\t    XmlValidate -kml -schema=C:/pathToXsd/kml22.xsd -ns=http://www.opengis.net/kml/2.2 C:/pathToMyKmlFiles\n");
        System.err.println("\t2) To check all CoT .xml files against CoT Schema:");
        System.err.println("\t    XmlValidate -schema C:/pathToXsd/event.xsd C:/pathToMyCoTFiles\n");
        System.err.println("\t3) Validate kml and kmz files against local schemas as defined in KML files:");
        System.err.println("\t    XmlValidate -kmz -map=ns.map C:/pathToMyKmlFiles\n");
        System.err.println("\t4) Validate by URL for KML and target schema and print KML content");
        System.err.println("\t   if any errors are found but limit size of each file printed to first 4K:");
        System.err.println("\t    XmlValidate -dump -maxDump=4096 -ns=http://earth.google.com/kml/2.1\n" +
                "\t\t-schema=http://code.google.com/apis/kml/schema/kml21.xsd\n" +
                "\t\thttp://kml-samples.googlecode.com/svn/trunk/kml/kmz/simple/big.kmz\n");
		System.err.println("\t5) Validate by XML document with explicit schema location");
		System.err.println("\t    XmlValidate -schemaLocation=http://myExtension=ext.xsd example.xml");
        System.err.println("\nNote: XmlValidate command in examples above is a short-cut to the executable\n" +
                "such as: java -jar xmlValidate.jar or in equivalent batch file/shell script.");

        System.exit(1);
    }

    //
    // MAIN
    //

    /**
     * Main program entry point.
     */
    public static void main (String[] args) {

        if (args.length < 2) {
            usage();
        }

        XmlValidate validator = new XmlValidate();
        List<String> list = new ArrayList<>();

		// -home argument must be called before -map is processed
		for (String arg : args) {
			if (arg.equals("-debug")) {
				validator.setDebug(true);
			} else if (arg.startsWith("-home=")) {
				validator.setHomeDir(arg.substring(6));
			}
		}
        for (String arg : args) {
			String argLwr = arg.toLowerCase(Locale.ROOT);
            if (argLwr.startsWith("-ns=")) {
                validator.setNamespace(arg.substring(4));
            } else if (argLwr.startsWith("-map=")) {
                validator.setMap(new File(arg.substring(5)));
            } else if (argLwr.startsWith("-schema=")) {
                // if specify schema then drop any map selection
                validator.schemaMap = null;
                arg = arg.substring(8);
                File file = new File(arg);
                if (file.exists())
                    validator.setSchema(file);
                else
                    validator.setSchema(arg); // treat as URL
                /// System.err.println("schema=" + validator.schemaUri);
			} else if (argLwr.startsWith("-schemalocation=")) {
				String val = arg.substring(arg.indexOf('=') + 1);
				int ind = val.indexOf('=');
				if (ind > 0) {
					String ns = val.substring(0,ind);
					String schemaLocation = val.substring(ind + 1);
					validator.addSchemaLocation(ns, schemaLocation);
					if (validator.debug) validator.out.printf("Set %s -> %s%n", ns, schemaLocation);
				} else {
					System.err.println("Invalid argument value: " + arg);
					usage();
				}
            } else if (argLwr.startsWith("-v")) {
                if (arg.length() == 2 || arg.endsWith("=true"))
                    validator.setVerbose(true);
            } else if (arg.equals("-S")) {
				validator.summary = true;
			} else if (arg.equals("-K")) {
				validator.kmlMode = true;
            } else if (argLwr.equals("-kml")) {
                validator.extensionSet.clear();
                validator.extensionSet.add("kml");
            } else if (argLwr.equals("-kmz")) {
                validator.extensionSet.clear();
                validator.extensionSet.add("kml");
                validator.extensionSet.add("kmz");
			} else if (argLwr.equals("-z")) {
				validator.kmzMode = true;
				validator.extensionSet.add("kmz");
            } else if (arg.startsWith("-x=")) {
                if (arg.length() > 3) {
                    validator.extensionSet.addAll(Arrays.asList(arg.substring(3).split(":")));
                    System.err.println("Extensions=" + validator.extensionSet);
                }
            } else if (argLwr.equals("-debug")) {
                validator.setDebug(true);
            } else if (arg.equals("-d") || argLwr.equals("-dump")) {
                validator.dumpLevel = 1;
            } else if (argLwr.startsWith("-dump=")) {
                validator.dumpLevel = Integer.parseInt(arg.substring(6));
            } else if (argLwr.startsWith("-maxdump=")) {
                validator.dumpLimit = Integer.parseInt(arg.substring(9));
            } else if (argLwr.startsWith("-home=")) {
                // already handled as special case
            } else if (arg.startsWith("-h")) {
                usage();
            } else if (arg.startsWith("-")) {
                System.err.println("Invalid argument: " + arg);
                usage();
            } else list.add(arg);
        }

        // either 1) schema is defined for non-namespace schema validation;
        // or 2) -schema and -ns is defined for schema namespace validation.
        // or 3) schemaMap is defined.  Cannot run without one of these modes.
        // must have either schemaUri or schemaMap set otherwise not valid options
        if (validator.schemaUri == null && validator.schemaMap == null) {
            if (!list.isEmpty()) System.err.println("Must specify -map or -schema");
            usage();
        }

        for (String arg : list) {
            if (arg.startsWith("http:"))
                try {
                    // validate as URL
                    validator.validate(new UrlResource(validator.out, new URL(arg), validator.schemaNamespace));
                } catch (MalformedURLException e) {
                    System.err.println("WARN: bad URL " + arg + ": " + e.getMessage());
                }
            else {
                // validate target as local file if it exists
                File file = new File(arg);
                if (file.exists())
                    validator.validate(file);
                else
                    try {
                        // otherwise validate target as URL
                        validator.validate(new UrlResource(validator.out, new URL(arg), validator.schemaNamespace));
                    } catch (MalformedURLException e) {
                        System.err.println("WARN: file/URL not found " + arg + ": " + e.getMessage());
                    }
                    //System.err.println("WARN: file " + arg + " does not exist");
            }
        }

        if (validator.startTime != 0) {
			validator.dumpStatus();
        } else {
            // else fileCount = 0
            if (list.isEmpty())
                usage();
            else
                validator.out.format("%nErrors: %d  Warnings: %d  Files: %d%n",
                        validator.errors, validator.warnings, validator.fileCount);
        }
    }

}
