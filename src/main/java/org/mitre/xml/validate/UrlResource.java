/*
 *  UrlResource.java
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
import org.jdom2.input.SAXBuilder;

import java.net.URL;
import java.net.URLConnection;
import java.io.InputStream;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.PrintStream;
import java.util.Locale;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

/**
 * URL resource that implements building a JDOM Document
 * from a URL.
 * 
 * @author Jason Mathews, MITRE Corp.
 * Date: Feb 13, 2009 7:08:07 PM
 */
public class UrlResource extends Resource {
    
    private final URL url;

    public UrlResource(PrintStream out, URL url, String schemaNamespace) {
        super(out, url.toString(), schemaNamespace);
        this.url = url;
    }

    public Document getDocument(SAXBuilder builder) throws JDOMException, IOException {
        if (doc == null) doc = builder.build(getInputStream(url), url.toExternalForm());
        return doc;
    }

    public String getSource() {
        return url.toExternalForm();
    }

    /**
     * This method gets the correct input stream for a URL.  If the URL
     * is to a KMZ resource then the first KML entry inside the zip input stream
     * is returned in an inputStream.
     *
     * @param url The url to the XML resource
     * @return The InputStream used to validate and parse the xml resource
     * @throws java.io.IOException when an I/O error prevents a document
     *         from being fully parsed.
     */
    public static InputStream getInputStream(URL url) throws IOException {
        // Open the connection
        URLConnection conn = url.openConnection();

        // Connect to get the response headers
        conn.connect();

        if ("application/vnd.google-earth.kmz".equals(conn.getContentType()) ||
                url.getFile().toLowerCase(Locale.ROOT).endsWith(".kmz")) {
            // kmz file requires special handling
			// NOTE: some files ending with .kmz are actually KML (XML) files
			// examples:
			//  http://www.strandbewertung.de/strandbewertung.kmz => Content-Type: application/vnd.google-earth.kmz
			//  http://hemendikhortik.zxq.net/Eslovenia_en.kmz => Content-Type: text/plain
            ZipInputStream zis = new ZipInputStream(conn.getInputStream());
            ZipEntry entry;
            //   Simply find first kml file in the archive
            //
            //   Note that KML documentation loosely defines that it takes first root-level KML file
            //   in KMZ archive as the main KML document but Google Earth (version 4.3 as of Dec-2008)
            //   actually takes the first kml file regardless of name (e.g. doc.kml which is convention only)
            //   and whether its in the root folder or subfolder. Otherwise would need to keep track
            //   of the first KML found but continue if first KML file is not in the root level then
            //   backtrack in stream to first KML if no root-level KML is found.
            while ((entry = zis.getNextEntry()) != null) {
                // find first KML file in archive
                if (entry.getName().toLowerCase(Locale.ROOT).endsWith(".kml")) {
                    return zis; // start reading from stream
                }
            }
            throw new IOException("Failed to find KML content in KMZ file");
        }

        // Else read the raw bytes.
        return new BufferedInputStream(conn.getInputStream());
    }

}
