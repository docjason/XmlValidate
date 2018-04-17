/*
 *  KmzExplorer.java
 *
 *  (C) Copyright MITRE Corporation 2014
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

import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Jason Mathews, MITRE Corp.
 * Created on 6/30/2014.
 */
public class KmzExplorer implements Closeable {

	private final File file;
	private final PrintStream out;
	private final String schemaNamespace;
	private ZipFile zf;
	private final Enumeration<? extends ZipEntry> e;

	public KmzExplorer(PrintStream out, File file, String schemaNamespace) throws IOException {
		this.out = out;
		this.file = file;
		this.schemaNamespace = schemaNamespace;
		zf = new ZipFile(file);
		e = zf.entries();
		while (e.hasMoreElements()) {
			ZipEntry entry = e.nextElement();
			// skip first/root kml file
			if (entry.getName().toLowerCase(Locale.ROOT).endsWith(".kml")) break;
		}
	}

	public Resource next() {
		while (e.hasMoreElements()) {
			ZipEntry entry = e.nextElement();
			if (entry.getName().toLowerCase(Locale.ROOT).endsWith(".kml")) {
				return new KmzResource(entry);
			}
		}
		return null;
	}

	public void close() {
		if (zf != null) {
			try {
				zf.close();
			} catch (IOException ex) {
				// ignore
			}
			zf = null;
		}
	}

	private class KmzResource extends Resource {

		private final ZipEntry entry;

		public KmzResource(ZipEntry entry) {
			super(KmzExplorer.this.out,
					file.toString() + '/' + entry.getName(), schemaNamespace);
			this.entry = entry;
		}

		@Override
		public String getSource() {
			return targetFile;
		}

		@Override
		public Document getDocument(SAXBuilder builder) throws JDOMException, IOException {
			if (doc == null) {
				InputStream is = null;
				try {
					is = zf.getInputStream(entry);
					doc = builder.build(is,
							file.getAbsoluteFile().toURI().toString());
				} finally {
					if (is != null)
						try {
							is.close();
						} catch (IOException ioe) {
							// ignore
						}
				}
			}
			return doc;
		}
	}
}
