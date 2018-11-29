package ${packageName};

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
public class AttributeTest extends StructuralTest {

	// Each parameter (see below) is placed as an argument here.
	// Every time the test runner triggers, it will pass the arguments
	// from parameters we defined in the static findClasses() method
	public AttributeTest(String expectedClassName, String expectedPackageName, JSONObject expectedClassJson) {
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
				if (expectedClassJson.has("class") && expectedClassJson.has("attributes")) {
					String expectedClassName = expectedClassJson.getString("class");
					String expectedPackageName = expectedClassJson.getString("package");
					testData.add(new Object[] { expectedClassName, expectedPackageName, expectedClassJson });
				}
			}
		}
		return testData;
	}

	@Test(timeout = 1000)
	public void testAttributes() {
		Class<?> actualClass = findClassForTestType("attribute");
		if (expectedClassJson.has("attributes")) {
			JSONObject jsonAttributes = expectedClassJson.getJSONObject("attributes");
			checkAttributes(actualClass, jsonAttributes);
		}
	}

	private void checkAttributes(Class<?> actualClass, JSONObject expectedAttributes) {
		Iterator<?> expectedAttributesIterator = expectedAttributes.keys();

		while (expectedAttributesIterator.hasNext()) {
			String expectedAttributeName = (String) expectedAttributesIterator.next();
			Field actualAttribute = null;
			try {
				actualAttribute = actualClass.getDeclaredField(expectedAttributeName);
			} catch (NoSuchFieldException | SecurityException ex) {
				fail("Problem: the class '" + expectedClassName + "' does NOT define the expected attribute '" + expectedAttributeName + "'.");
			}

			if (actualAttribute == null) {
				fail("Problem: the class '" + expectedClassName + "' does NOT define the expected attribute '" + expectedAttributeName + "'.");
			}

			Object expectedAttribute = expectedAttributes.get(expectedAttributeName);
			//this is the case that the type is specified directly after the name (without modifiers)
			if(expectedAttribute instanceof String) {
				checkType((String)expectedAttribute, actualAttribute);
			}
			else if(expectedAttribute instanceof JSONObject) {
				JSONObject expectedAttributeJson = (JSONObject) expectedAttribute;

				if(expectedAttributeJson.has("modifiers")) {
					JSONArray expectedModifiers = expectedAttributeJson.getJSONArray("modifiers");
					checkModifiers(actualAttribute.getModifiers(), expectedModifiers, "attribute", expectedAttributeName);
				}

				if (expectedAttributeJson.has("type")) {
					String expectedAttributeType = expectedAttributeJson.getString("type");
					checkType(expectedAttributeType, actualAttribute);
				}
			}
		}
	}

	private void checkType(String expectedType, Field actualAttribute) {
		if (expectedType.contains("<")) {
			String expectedMainType = expectedType.split("<")[0];
			String expectedGenericType = expectedType.split("<")[1].replace(">", "");

			assertTrue("Problem: the attribute '" + actualAttribute.getName() + "' in the class '" + expectedClassName + "' does NOT have the expected type.", expectedMainType.equals(actualAttribute.getType().getSimpleName()));

			Type genericType = actualAttribute.getGenericType();
			if (genericType instanceof ParameterizedType) {
				Type actualType = ((ParameterizedType) actualAttribute.getGenericType()).getActualTypeArguments()[0];
				String actualTypeString = actualType.toString().substring(actualType.toString().lastIndexOf(".") + 1);
				assertTrue("Problem: the attribute '" + actualAttribute.getName() + "' in the class '" + expectedClassName + "' does NOT have the expected type.", expectedGenericType.equals(actualTypeString));
			}
		}
		else {
			assertTrue("Problem: the attribute '" + actualAttribute.getName() + "' in the class '" + expectedClassName + "' does NOT have the expected type.", expectedType.equals(actualAttribute.getType().getSimpleName()));
		}
	}
}
