package ${packageName};

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * @author Stephan Krusche (krusche@in.tum.de)
 * @version 1.2 (26.10.2018)
 *
 * This tests evaluates the declared methods including their access modifiers,
 * parameters and return type based on the definitions in test.json. It is only
 * invoked if methods are specified in test.json.
 *
 */
@RunWith(Parameterized.class)
public class MethodTest extends StructuralTest {

	// Each parameter (see below) is placed as an argument here.
	// Every time the test runner triggers, it will pass the arguments
	// from parameters we defined in the static findClasses() method
	public MethodTest(String expectedClassName, String expectedPackageName, JSONObject expectedClassJson) {
		super(expectedClassName, expectedPackageName, expectedClassJson);
	}

	@Parameterized.Parameters(name = "{0}")
	public static Collection<Object[]> findClasses() throws IOException {
		List<Object[]> testData = new ArrayList<Object[]>();
		String jsonString = toString(StructuralTest.class.getResource("test.json"));
		if (jsonString != null) {
			JSONArray expectedClassesArray = new JSONArray(jsonString);
			for (int i = 0; i < expectedClassesArray.length(); i++) {
				JSONObject expectedClassJson = (JSONObject) expectedClassesArray.get(i);
				//only test the hierarchy if the it was specified
				if (expectedClassJson.has("class") && expectedClassJson.has("methods")) {
					String expectedClassName = expectedClassJson.getString("class");
					String expectedPackageName = expectedClassJson.getString("package");
					testData.add(new Object[] { expectedClassName, expectedPackageName, expectedClassJson });
				}
			}
		}
		return testData;
	}

	@Test//(timeout = 1000)
	public void testMethods() {
		Class<?> actualClass = findClassForTestType("method");
		if (expectedClassJson.has("methods")) {
			JSONObject jsonMethods = expectedClassJson.getJSONObject("methods");
			checkMethods(actualClass, jsonMethods);
		}
	}

	private void checkMethods(Class<?> actualClass, JSONObject jsonMethods) {
		Iterator<?> methodsIterator = jsonMethods.keys();
		while (methodsIterator.hasNext()) {
			String expectedMethodName = (String) methodsIterator.next();
			JSONObject expectedMethod = jsonMethods.getJSONObject(expectedMethodName);

			Method actualMethod = null;
			for (Method declaredMethod : actualClass.getDeclaredMethods()) {
				if (declaredMethod.getName().equals(expectedMethodName)) {
					if (actualMethod != null)
					{
						//TODO - Task (low priority): implement support for overloading
						fail("Problem: the method '" + expectedMethodName + "' in the class '" + expectedClassName + "' is overloaded, i.e. implemented multiple times with different parameters. This is NOT supported in this exercise. Please make sure to implement this method only once.");
					}
					actualMethod = declaredMethod;
				}
			}

			if (actualMethod == null) {
				fail("Problem: the class '" + expectedClassName + "' does NOT include the expected method '" + expectedMethodName + "'.");
			}

			String actualMethodName = actualMethod.getName();

			if (expectedMethod.has("modifiers")) {
				JSONArray expectedModifiers = expectedMethod.getJSONArray("modifiers");
				checkModifiers(actualMethod.getModifiers(), expectedModifiers, "method", actualMethod.getName());
			}

			if (expectedMethod.has("parameters")) {
				JSONArray expectedParameters = (JSONArray) expectedMethod.get("parameters");
				Class<?>[] actualParameterTypes = actualMethod.getParameterTypes();

				assertEquals("Problem: the method '" + actualMethodName + "' in the class '" + expectedClassName + "' does NOT have the expected number of parameters.", expectedParameters.length(), actualParameterTypes.length);

				for (int j = 0; j < actualParameterTypes.length; j++) {
					String expectedParameterType = expectedParameters.getString(j);
					String actualParameterType = actualParameterTypes[j].getSimpleName();
					assertEquals("Problem: the " + j + ". parameter type '" + actualParameterType + "' of the method '" + actualMethodName + "' in the class '" + expectedClassName + "' is NOT defined as expected.", expectedParameterType, actualParameterType);
				}
				String expectedReturnType = expectedMethod.getString("returnType");
				String actualReturnType = actualMethod.getReturnType().getSimpleName();
				assertEquals("Problem: the return type '" + actualReturnType + "' of the method '" + actualMethodName + "' in the class '" + expectedClassName + "' is NOT defined as expected.", expectedReturnType, actualReturnType);
			}
		}
	}
}
