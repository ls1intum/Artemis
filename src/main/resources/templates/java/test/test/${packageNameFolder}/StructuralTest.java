package ${packageName};

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @author Stephan Krusche (krusche@in.tum.de)
 * @version 1.2 (26.10.2018)
 *
 * This StructuralTest evaluates if 4 specified elements of a given class in the test.json file are correctly implemented (in case they are specified):
 *
 * 1) The hierarchy, i.e. if a specified superclass is extended and if specified interfaces are implemented
 * 2) The declared constructors their including access modifiers and parameters
 * 3) The declared methods including their access modifiers, parameters and return type
 * 4) The declared attributes including their access modifiers and types
 *
 * The StructuralTest generates 4 test cases per specified class in the test.json file
 */
public class StructuralTest {

	protected String expectedClassName;
	protected String expectedPackageName;
	protected JSONObject expectedClassJson;

	public StructuralTest(String expectedClassName, String expectedPackageName, JSONObject expectedClassJson) {
		this.expectedClassName = expectedClassName;
		this.expectedPackageName = expectedPackageName;
		this.expectedClassJson = expectedClassJson;
	}

	protected Class<?> findClassForTestType(String typeOfTest) {
		try {
			return Class.forName(this.expectedPackageName + "." + expectedClassName);
		} catch (ClassNotFoundException e) {
			fail("Problem during " + typeOfTest + " check: the class '" + expectedClassName + "' was not found. Please double check that you have implemented it in the project.");
		}
		return null;
	}

	protected static String toString(URL url) throws IOException {
		BufferedReader in = null;
		try {
			in = new BufferedReader(new InputStreamReader(url.openStream()));
			char[] buffer = new char[8192];
			int len;
			StringBuilder result = new StringBuilder();
			while ((len = in.read(buffer, 0, buffer.length)) != -1) {
				result.append(buffer, 0, len);
			}
			return result.toString();
		} finally {
			if (in != null) {
				in.close();
			}
		}
	}

	protected void checkModifiers(int actualModifiers, JSONArray expectedModifiers, String type, String name) {
		String actualModifiersString = Modifier.toString(actualModifiers);
		for (int i = 0; i < expectedModifiers.length(); i++) {
			String expectedModifier = (String) expectedModifiers.get(i);
			String[] modifiers = actualModifiersString.split(" ");
			boolean modifierIsSet = false;
			for (String modifier : modifiers) {
				if (expectedModifier.equals(modifier)) {
					modifierIsSet = true;
				}
			}
			assertTrue("Problem: The access modifier '" + actualModifiersString + "' of the " + type + " '" + name + "' is not specified as expected.", modifierIsSet);
		}
	}
}
