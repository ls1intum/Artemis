package ${packageName};

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Modifier;
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
 * This test evaluates the hierarchy of the class, i.e. if a specified superclass is
 * extended and if specified interfaces are implemented, based on the definitions
 * in test.json. It is only invoked if methods are specified in test.json.
 */
@RunWith(Parameterized.class)
public class ClassTest extends StructuralTest {

	// Each parameter (see below) is placed as an argument here.
	// Every time the test runner triggers, it will pass the arguments
	// from parameters we defined in the static findClasses() method
	public ClassTest(String expectedClassName, String expectedPackageName, JSONObject expectedClassJson) {
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
				if (expectedClassJson.has("class") && (expectedClassJson.has("superclass") || expectedClassJson.has("interfaces") || expectedClassJson.has("isAbstract") || expectedClassJson.has("isInterface"))) {
					String expectedClassName = expectedClassJson.getString("class");
					String expectedPackageName = expectedClassJson.getString("package");
					testData.add(new Object[] { expectedClassName, expectedPackageName, expectedClassJson });
				}
			}
		}
		return testData;
	}

	@Test(timeout = 1000)
	public void testClass() {
		Class<?> actualClass = findClassForTestType("hierarchy");

		if (expectedClassJson.has("isAbstract")) {
			assertTrue("Problem: the class '" + expectedClassName + "' is NOT abstract as it is expected.", Modifier.isAbstract(actualClass.getModifiers()));
		}

		if (expectedClassJson.has("isInterface")) {
			assertTrue("Problem: the type '" + expectedClassName + "' is NOT an interface as it is expected.", Modifier.isInterface(actualClass.getModifiers()));
		}


		if (expectedClassJson.has("superclass")) {
			String expectedSuperClassName = expectedClassJson.getString("superclass");
			String actualSuperClassName = actualClass.getSuperclass().getSimpleName();
			assertEquals("Problem: the class '" + expectedClassName + "' is NOT a subclass of the class '" + expectedSuperClassName + "' as it is expected.", expectedSuperClassName, actualSuperClassName);
		}

		if(expectedClassJson.has("interfaces")) {
			JSONArray expectedInterfaces = expectedClassJson.getJSONArray("interfaces");
			Class<?>[] actualInterfaces = actualClass.getInterfaces();

			for (int i = 0; i < expectedInterfaces.length(); i++) {
				String expectedInterface = (String) expectedInterfaces.get(i);
				boolean implementsInterface = false;
				for (Class<?> actualInterface : actualInterfaces) {
					if (expectedInterface.equals(actualInterface.getSimpleName())) {
						implementsInterface = true;
						break;	//we found our interface and can stop searching
					}
				}
				if (!implementsInterface) {
					fail("Problem: the class '" + expectedClassName + "' does not implement the interface '" + expectedInterface + "' as it is expected.");
				}
			}
		}
	}
}
