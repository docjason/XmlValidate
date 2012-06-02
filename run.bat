@echo off
setlocal

echo This test batch file shows several typical ways
echo to use XmlValidate with the sample XML data
echo located in data directory, schemas in schemas/,
echo and wrapper .bat files in /bin.
echo.
echo ==========================================
echo.

REM Source XML files are loaded into memory so may need
REM to increase memory limits to validate very large XML.
set OPTS=-Xmx64m

echo Validate CoT XML document with non-namespace schema
call bin\xv -schema=schemas\Event.xsd -v data/xml/cot.xml
echo.
echo ==========================================

echo.
echo Validate all KML/KMZ documents as KML 2.1 Schema
call bin\kml21 -kmz data
echo.
echo ==========================================

echo.
echo Validate all GPX/KML/KMZ documents
call bin\xv -v -kmz -x=gpx data
echo.
echo Notice the tessellate-orig.kml example has the wrong namespace
echo and fails to validate against the specified schema namespace.
echo ==========================================
