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
 * @version 2.0 (24.02.2019)
 *
 * This test evaluates the hierarchy of the class, i.e. if the class is abstract
 * or an interface or an enum and also if the class extends another class and if
 * it implements an interface, based on its definition in the structure oracle.
 */
@RunWith(Parameterized.class)
public class ClassTest extends StructuralTest {

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

        for (int i = 0; i < structureOracleJSON.length(); i++) {
            JSONObject expectedClassJSON = structureOracleJSON.getJSONObject(i);
            JSONObject expectedClassPropertiesJSON = expectedClassJSON.getJSONObject("class");

            // Only test the classes that have properties defined in the structure oracle.
            // TODO: there are cases where we define the class, but we don't want it to be tested. Should we just remove the name then?
            // Or should we better declare this explicitly with another attribute in json?
            if (expectedClassPropertiesJSON.has("name")) {
                String expectedClassName = expectedClassPropertiesJSON.getString("name");
                String expectedPackageName = expectedClassPropertiesJSON.getString("package");
                testData.add(new Object[] { expectedClassName, expectedPackageName, expectedClassJSON });
            }
        }
        return testData;
    }

    /**
     * This test loops over the list of the test data generated by the method findClasses(), checks if each class is found
     * at all in the assignment and then proceeds to check its properties.
     */
    @Test(timeout = 1000)
    public void testClass() {
        Class<?> observedClass = findClassForTestType("hierarchy");

        JSONObject expectedClassPropertiesJSON = expectedClassJSON.getJSONObject("class");

        if (expectedClassPropertiesJSON.has("isAbstract")) {
            assertTrue("Problem: the class '" + expectedClassName + "' is not abstract as it is expected.",
                Modifier.isAbstract(observedClass.getModifiers()));
        }

        if (expectedClassPropertiesJSON.has("isEnum")) {
            assertTrue("Problem: the type '" + expectedClassName + "' is not an enum as it is expected.",
                (observedClass.isEnum()));
        }

        if (expectedClassPropertiesJSON.has("isInterfaceDifferent")) {
            assertTrue("Problem: the type '" + expectedClassName + "' is not an interface as it is expected.",
                Modifier.isInterface(observedClass.getModifiers()));
        }

        if(expectedClassPropertiesJSON.has("isEnum")) {
            assertTrue("Problem: the type '" + expectedClassName + "' is not an enum as it is expected.",
                observedClass.isEnum());
        }

        if(expectedClassPropertiesJSON.has("superclass")) {
            // Filter out the enums, since there is a separate test for them
            if(!expectedClassPropertiesJSON.getString("superclass").equals("Enum")) {
                String expectedSuperClassName = expectedClassPropertiesJSON.getString("superclass");
                String actualSuperClassName = observedClass.getSuperclass().getSimpleName();

                String failMessage = "Problem: the class '" + expectedClassName + "' is not a subclass of the class '"
                    + expectedSuperClassName + "' as expected. Please implement the class inheritance properly.";
                assertTrue(failMessage, expectedSuperClassName.equals(actualSuperClassName));
            }
        }

        if(expectedClassPropertiesJSON.has("interfaces")) {
            JSONArray expectedInterfaces = expectedClassPropertiesJSON.getJSONArray("interfaces");
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
