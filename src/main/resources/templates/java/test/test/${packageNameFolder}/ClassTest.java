package ${packageName};

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
 * @version 2.1 (02.06.2019)
 *
 * This test evaluates the hierarchy of the class, i.e. if the class is abstract
 * or an interface or an enum and also if the class extends another class and if
 * it implements an interface, based on its definition in the structure oracle.
 */
@RunWith(Parameterized.class)
public class ClassTest extends StructuralTest {

    private static final String JSON_PROPERTY_SUPERCLASS = "superclass";
	private static final String JSON_PROPERTY_INTERFACES = "interfaces";
	private static final String JSON_PROPERTY_CLASS = "class";
	private static final String JSON_PROPERTY_PACKAGE = "package";
	private static final String JSON_PROPERTY_NAME = "name";

	public ClassTest(String expectedClassName, String expectedPackageName, JSONObject expectedClassJSON) {
        super(expectedClassName, expectedPackageName, expectedClassJSON);
    }

    /**
     * This method collects the classes in the structure oracle file for which at least one class property is specified.
     * These classes are packed into a list, which represents the test data.
     * @return A list of arrays containing each class' name, package and the respective JSON object defined in the structure oracle.
     */
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> findClasses() throws IOException {
        List<Object[]> testData = new ArrayList<Object[]>();

        if (structureOracleJSON == null) {
            return testData;
        }

        for (int i = 0; i < structureOracleJSON.length(); i++) {
            JSONObject expectedClassJSON = structureOracleJSON.getJSONObject(i);
            JSONObject expectedClassPropertiesJSON = expectedClassJSON.getJSONObject(JSON_PROPERTY_CLASS);

            // Only test the classes that have additional properties (except name and package) defined in the structure oracle.
            if (expectedClassPropertiesJSON.has(JSON_PROPERTY_NAME) && expectedClassPropertiesJSON.has(JSON_PROPERTY_PACKAGE) && hasAdditionalProperties(expectedClassPropertiesJSON)) {
                String expectedClassName = expectedClassPropertiesJSON.getString(JSON_PROPERTY_NAME);
                String expectedPackageName = expectedClassPropertiesJSON.getString(JSON_PROPERTY_PACKAGE);
                testData.add(new Object[] { expectedClassName, expectedPackageName, expectedClassJSON });
            }
        }
        return testData;
    }
    
    private static boolean hasAdditionalProperties(JSONObject jsonObject) {
    	List<String> keys = new ArrayList<String>(jsonObject.keySet());
    	keys.remove(JSON_PROPERTY_NAME);
    	keys.remove(JSON_PROPERTY_PACKAGE);
    	return keys.size() > 0;
    }

    /**
     * This test loops over the list of the test data generated by the method findClasses(), checks if each class is found
     * at all in the assignment and then proceeds to check its properties.
     */
    @Test(timeout = 1000)
    public void testClass() {
        Class<?> observedClass = findClassForTestType("class");

        JSONObject expectedClassPropertiesJSON = expectedClassJSON.getJSONObject(JSON_PROPERTY_CLASS);

        if (expectedClassPropertiesJSON.has("isAbstract")) {
            assertTrue("Problem: the class '" + expectedClassName + "' is not abstract as it is expected.",
                Modifier.isAbstract(observedClass.getModifiers()));
        }

        if (expectedClassPropertiesJSON.has("isEnum")) {
            assertTrue("Problem: the type '" + expectedClassName + "' is not an enum as it is expected.",
                (observedClass.isEnum()));
        }

        if (expectedClassPropertiesJSON.has("isInterface")) {
            assertTrue("Problem: the type '" + expectedClassName + "' is not an interface as it is expected.",
                Modifier.isInterface(observedClass.getModifiers()));
        }

        if(expectedClassPropertiesJSON.has("isEnum")) {
            assertTrue("Problem: the type '" + expectedClassName + "' is not an enum as it is expected.",
                observedClass.isEnum());
        }

        if(expectedClassPropertiesJSON.has(JSON_PROPERTY_SUPERCLASS)) {
            // Filter out the enums, since there is a separate test for them
            if(!expectedClassPropertiesJSON.getString(JSON_PROPERTY_SUPERCLASS).equals("Enum")) {
                String expectedSuperClassName = expectedClassPropertiesJSON.getString(JSON_PROPERTY_SUPERCLASS);
                String actualSuperClassName = observedClass.getSuperclass().getSimpleName();

                String failMessage = "Problem: the class '" + expectedClassName + "' is not a subclass of the class '"
                    + expectedSuperClassName + "' as expected. Please implement the class inheritance properly.";
                assertTrue(failMessage, expectedSuperClassName.equals(actualSuperClassName));
            }
        }

        if(expectedClassPropertiesJSON.has(JSON_PROPERTY_INTERFACES)) {
            JSONArray expectedInterfaces = expectedClassPropertiesJSON.getJSONArray(JSON_PROPERTY_INTERFACES);
            Class<?>[] observedInterfaces = observedClass.getInterfaces();

            for (int i = 0; i < expectedInterfaces.length(); i++) {
                String expectedInterface = expectedInterfaces.getString(i);
                boolean implementsInterface = false;

                for (Class<?> observedInterface : observedInterfaces) {
                    if(expectedInterface.equals(observedInterface.getSimpleName())) {
                        implementsInterface = true;
                        break;
                    }
                }

                if (!implementsInterface) {
                    fail("Problem: the class '" + expectedClassName + "' does not implement the interface '" + expectedInterface + "' as expected."
                        + " Please implement the interface and its methods.");
                }
            }
        }
    }
}
