XmlValidate is a quick & flexible XML validator capable of performing
bulk validation of XML documents against XML Schemas as defined
in the XML documents or forcing another schema target. 

Setup
=====

From the command-line run gradle test command to build the project
and run the tests.
	> gradle test
	
Several .bat files are provided to run XmlValidate with various
pre-defined options. These are located in the 'bin' directory.

To run these .bat files you must first run the following command:
	> gradle setup
	
This creates 'bin/setup.bat' file, which locates the required jar files
and is called by each of the .bat files (e.g. bin/xv.bat).

To test installation of XmlValidate run the run.bat script
at the top-level of the distribution.

If the output matches the following then the jars, test data,
schemas, and batch files are in the correct locations.

== run.bat output ==

	This test batch file shows several typical ways
	to use XmlValidate with the sample XML data
	located in data directory, schemas in schemas/,
	and wrapper .bat files in /bin.
	==========================================

	Validate CoT XML document with non-namespace schema
	> bin\xv -schema=schemas\Event.xsd -v data\cot.xml

	Check: data\cot.xml
		 *OK*

	Errors: 0  Warnings: 0  Files: 1  Time: 250 ms
	Valid files 1/1 (100%)

	==========================================

	Validate all KML/KMZ documents against KML 2.1 Schema
	> bin\kml21 -kmz data

	Errors: 0  Warnings: 0  Files: 5  Time: 328 ms
	Valid files 5/5 (100%)

	Same as running the following:
	> bin\xv -kmz -ns=http://earth.google.com/kml/2.1 -schema=schemas\kml21.xsd data

	==========================================

	Validate all GPX/KML/KMZ documents
	> bin\xv.bat -v -kmz -x=gpx data/xml data/kml data/kmz
	dir: data\xml

	Check: data\xml\mystic_basin_trail.gpx
		 *OK*
	dir: data\kml

	Check: data\kml\data-ext-atom.kml
	assign Namespace http://www.w3.org/2005/Atom -> file:/C:/projects/xmlValidate/temp/XmlValidate/schemas/atom.xsd
		 *OK*

	Check: data\kml\earth-google-com-kml-21.kml
		 *OK*

	Check: data\kml\earth-google-com-kml-22.kml
		 *OK*

	Check: data\kml\no-namespace.kml
	INFO: no root namespace
		 *OK*

	Check: data\kml\nonkmlroot.kml
		 *OK*

	Check: data\kml\placemark.kml
		 *OK*

	Check: data\kml\tessellate-orig.kml
	http://www.opengis.net/kml/2.2
	ERROR: SAXParseException org.xml.sax.SAXParseException; lineNumber: 27; columnNumber: 13; cvc-complex-type.2.4.a: Invalid content was found starting with element 'tilt'. One of '{"http://www.opengis.net/kml/2.2":altitudeModeGroup, "http://www.opengis.net/kml/2.2":LookAtSimpleExtensionGroup, "http://www.opengis.net/kml/2.2":LookAtObjectExtensionGroup}' is expected.
	Line: 27, column: 13
	27: <tilt>***62.04855796276328</tilt>

	Check: data\kml\tessellate21.kml
		 *OK*

	Check: data\kml\tessellate22.kml
		 *OK*
	dir: data\kmz

	Check: data\kmz\big.kmz
		 *OK*

	Errors: 1  Warnings: 0  Files: 11  Time: 895 ms
	Valid files 10/11 (91%)


	Notice the tessellate-orig.kml example has the wrong namespace
	and fails to validate against the specified schema namespace.

	==========================================

	
Running
=======
	
Next run XmlValidate on your own XML documents using appropriate
.bat file (xv.bat, kml22.bat, etc.).

Run xv.bat on your favoriate XML files by filename, directory, or URL.

Configuration
=============

The file ns.map provides the namespace and locations of the target
XML schemas intially populated with a set of common schemas
(e.g. KML, GPX). You can add additional XML Schemas to the ns.map
file as needed by URL or absolute file location.

For example if you wanted to validate the web.xml file deployed in a Java web
application WAR file assuming the web.xml file starts like this:

	<?xml version="1.0" encoding="UTF-8"?>
	<web-app version="2.5" xmlns="http://java.sun.com/xml/ns/javaee">
	...
	
Add this entry to your ns.map file to validate such files.
http://java.sun.com/xml/ns/javaee=http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd

If you want to make local copies of the XML Schemas then you can add them to
your XmlValidate/schemas directory as reference them like this:
 http://java.sun.com/xml/ns/javaee=${XV_HOME}/schemas/web-app_2_5.xsd
or add any directory of your choosing and reference with an absolute path:
 http://java.sun.com/xml/ns/javaee=C:/xml/schemas/web-app_2_5.xsd

--
