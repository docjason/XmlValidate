Note the files in this folder are intentionally bad to test XML validation.

They all fail validation in one of three ways:
 1) XML file is not well-formed, or
 2) XML is not valid with respect to the declared XML Schema(s), or
 3) in case of KMZ files, the KMZ packaging for the KML is not valid
    or root KML file is not present.
 
bad-too-large.kmz
-- Zip file entry has invalid file length making this fail to parse.

bad.xml
-- XML not well-formed. Mismatched start and end tags.

badColor.kml
-- color value omits alpha component.

nokml.kmz
-- KMZ file with no root KML file.

notKmz.kmz
-- File has .kmz file extension but is not a KMZ but a KML file.
   XML Validate will identify this error and retry file as KML.
   
reallyHtml.kmz
-- File has .kmz extension but is really a HTML file.   

reallyKmz.kml
-- File has .kml extension but is really a KMZ file.

zero.kml
-- zero length file. Not a XML or KML file.
   Zero length files are skipped but indication is logged if in verbose mode.
