/*
 *  FileResource.java
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

import java.io.*;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.Enumeration;
import java.util.zip.ZipInputStream;

/**
 * File resource that implements building a JDOM Document
 * from a file reference.
 * 
 * @author Jason Mathews, MITRE Corp.
 * Date: Feb 13, 2009 7:17:23 PM
 */
public class FileResource extends Resource {
    
    private final File file;

    public FileResource(PrintStream out, File file, String schemaNamespace) {
        super(out, file.toString(), schemaNamespace);
        this.file = file;
    }

    public Document getDocument(SAXBuilder builder) throws JDOMException, IOException {
        if (doc == null) doc = buildDocument(builder);
        return doc;
    }

    public String getSource() {
        return file.toString();
    }

    public File getFile() {
        return file;
    }

    private Document buildDocument(SAXBuilder builder) throws JDOMException, IOException {
		// if KMZ file has .kml extension then out of luck - it will fail to parse
		// KMZ files must have .kmz extension - case doesn't matter
        if (!file.getName().toLowerCase().endsWith(".kmz")) {
            return builder.build(file);
        }
        // otherwise try finding KML in compressed KMZ file
        // NOTE: only the first "root" KML file is fetched. Supporting KML files will not be validated.
        ZipFile zf;
        try {
		 	zf = new ZipFile(file);
		} catch (ZipException ze) {

			// attempt #2
			// some KMZ files fail to open using ZipFile but work using ZipInputStream
			// bug was present in JRE 1.6.0_45 but appears to have been fixed in 1.7.0
			// URL: http://www.campinglimens.com/Camping_Limens.kmz
			// file had invalid timestamps in the zip entry header
			ZipInputStream zis = null;
			try {
				zis = new ZipInputStream(new FileInputStream(file));
				ZipEntry entry;
				while ((entry = zis.getNextEntry()) != null) {
					if (entry.getName().toLowerCase().endsWith(".kml")) {
						printFile();
						String msg = ze.toString();// e.g. java.util.zip.ZipException: error in opening zip file
						out.println("WARN: ZipFile failed [retry using ZipInputStream]: " + msg);
						stats.add("WARN: " + msg);
						warnings++;
						return builder.build(zis,
							file.getAbsoluteFile().toURI().toString());
					}
				}
			} catch(IOException ioe) {
				// ignore ZipInputStream exceptions and throw original exception if next attempt also fails
			}
			if (zis != null) {
				try {
					zis.close();
				} catch (IOException ioe) {
					// ignore
				}
			}

			// attempt #3
			// some .kmz files are really KML text files... verify header
			// examples:
			//  http://www.strandbewertung.de/strandbewertung.kmz => Content-Type: application/vnd.google-earth.kmz
			//  http://hemendikhortik.zxq.net/Eslovenia_en.kmz => Content-Type: text/plain
			DataInputStream is = null;
			try {
				is = new DataInputStream(new FileInputStream(file));
				final short hdr = is.readShort();
				// KMZ/ZIP header start with bytes: PK\0x3\0x4
				if (hdr != 0x504B) {
					// try as KML (XML) file
					Document doc = builder.build(file);
					final String msg = "WARN: " + ze; // e.g. java.util.zip.ZipException: error in opening zip file
					stats.add(msg);
					printFile();
					out.println(msg);
					out.println("WARN: KMZ file is invalid/mislabeled. Retry as KML");
					warnings++;
					return doc;
				}
			} catch(Exception e) {
				// ignore retry exception and allow ZipException to be rethrown
			} finally {
				if (is != null)
					try {
						is.close();
					} catch (IOException ioe) {
						// ignore
					}
			}

			throw ze; // rethrow exception if all attempts fail
		}

		// ZipFile was successfully created above
		// try to find the root kml file
        try {
            Enumeration<? extends ZipEntry> e = zf.entries();
            while (e.hasMoreElements()) {
                ZipEntry entry = e.nextElement();
                //   Simply find first kml file in the archive
                //
                //   Note that KML documentation loosely defines that it takes first root-level KML file
                //   in KMZ archive as the main KML document but Google Earth (version 4.3 as of Dec-2008)
                //   actually takes the first kml file regardless of name (e.g. doc.kml which is convention only)
                //   and whether its in the root folder or subfolder. Otherwise would need to keep track
                //   of the first KML found but continue if first KML file is not in the root level then
                //   backtrack in stream to first KML if no root-level KML is found.
                if (entry.getName().toLowerCase().endsWith(".kml")) {
                    return builder.build(zf.getInputStream(entry),
                            file.getAbsoluteFile().toURI().toString());
                }
            }
            throw new IOException("Failed to find KML content in KMZ file");
        } finally {
            zf.close();
        }
    }
}
