package org.mitre.xml.validate;

public interface ErrorStatus {

	void addWarning(String s);

	void addError(String s);

	int getWarnings();

	int getErrors();

}
