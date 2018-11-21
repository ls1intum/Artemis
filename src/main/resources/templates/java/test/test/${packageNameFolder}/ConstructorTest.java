package ${packageName};

import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * @author Stephan Krusche (krusche@in.tum.de)
 * @version 1.3 (19.11.2018)
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
@RunWith(Parameterized.class)
public class ConstructorTest extends StructuralTest {

	// Each parameter (see below) is placed as an argument here.
	// Every time the test runner triggers, it will pass the arguments
	// from parameters we defined in the static findClasses() method
	public ConstructorTest(String expectedClassName, String expectedPackageName, JSONObject expectedClassJson) {
		super(expectedClassName, expectedPackageName, expectedClassJson);
	}

	@Parameterized.Parameters(name = "{0}")
	public static Collection<Object[]> findClasses() throws IOException {
		List<Object[]> testData = new ArrayList<Object[]>();
		String jsonString = toString(StructuralTest.class.getResource("test.json"));
		if (jsonString != null) {
			JSONArray classesArray = new JSONArray(jsonString);
			for (int i = 0; i < classesArray.length(); i++) {
				JSONObject expectedClassJson = (JSONObject) classesArray.get(i);
				//only test the hierarchy if the it was specified
				if (expectedClassJson.has("class") && expectedClassJson.has("constructors")) {
					String expectedClassName = expectedClassJson.getString("class");
					String expectedPackageName = expectedClassJson.getString("package");
					testData.add(new Object[] { expectedClassName, expectedPackageName, expectedClassJson });
				}
			}
		}
		return testData;
	}

	@Test(timeout = 1000)
	public void testConstructors() {
		Class<?> actualClass = findClassForTestType("constructor");
		if (expectedClassJson.has("constructors")) {
			JSONArray expectedConstructors = expectedClassJson.getJSONArray("constructors");
			checkConstructors(actualClass, expectedConstructors);
		}
	}


	private void checkConstructors(Class<?> actualClass, JSONArray expectedConstructors) {
		Constructor<?>[] actualConstructors = actualClass.getDeclaredConstructors();
		for (int j = 0; j < expectedConstructors.length(); j++) {
			JSONObject expectedConstructor = (JSONObject) expectedConstructors.get(j);
			JSONArray expectedModifiers = expectedConstructor.getJSONArray("modifiers");
			JSONArray expectedParameters = expectedConstructor.getJSONArray("parameters");

			boolean constructorExists = false;
			for (Constructor<?> actualConstructor : actualConstructors) {

				Class<?>[] actualParameterTypes = actualConstructor.getParameterTypes();
				if (actualParameterTypes.length != expectedParameters.length()) {
					continue;
				}
				//number of constructor parameters is equal, check if all parameters are identical
				for (int k = 0; k < expectedParameters.length(); k++) {
					if(!expectedParameters.get(k).equals(actualParameterTypes[k].getSimpleName())) {
						continue;
					}
				}
				//if we reach this point, we found a matching constructor
				checkModifiers(actualConstructor.getModifiers(), expectedModifiers, "constructor", actualConstructor.getName());
				constructorExists = true;
				break;
			}
			if (!constructorExists) {
				fail("Problem: the class '" + expectedClassName + "' does not include an expected constructor with " + expectedParameters.length() + " parameters: " + expectedParameters + ". Make sure to implement it as it is expected.");
			}
		}
	}
}
