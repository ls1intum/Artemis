package ${packageName};

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;

/**
 * @author Stephan Krusche (krusche@in.tum.de)
 * @version 4.0 (27.10.2020)
 * <br><br>
 * This test evaluates if the specified constructors in the structure oracle are correctly implemented with the expected parameter types and annotations,
 * based on its definition in the structure oracle (test.json).
 */
public class ConstructorTest extends StructuralTest {

    /**
     * This method collects the classes in the structure oracle file for which constructors are specified.
     * These classes are then transformed into JUnit 5 dynamic tests.
     * @return A dynamic test container containing the test for each class which is then executed by JUnit.
     */
    @Timeout(10)
    @TestFactory
    public DynamicContainer generateTestsForAllClasses() throws URISyntaxException {
        List<DynamicNode> tests = new ArrayList<>();

        if (structureOracleJSON == null) {
            fail("The ConstructorTest can only run if the structural oracle (test.json) is present. If you do not provide it, delete ConstructorTest.java!");
        }

        for (int i = 0; i < structureOracleJSON.length(); i++) {
            JSONObject expectedClassJSON = structureOracleJSON.getJSONObject(i);

            // Only test the constructors if they are specified in the structure diff
            if (expectedClassJSON.has(JSON_PROPERTY_CLASS) && expectedClassJSON.has(JSON_PROPERTY_CONSTRUCTORS)) {
                JSONObject expectedClassPropertiesJSON = expectedClassJSON.getJSONObject(JSON_PROPERTY_CLASS);
                String expectedClassName = expectedClassPropertiesJSON.getString(JSON_PROPERTY_NAME);
                String expectedPackageName = expectedClassPropertiesJSON.getString(JSON_PROPERTY_PACKAGE);
                ExpectedClassStructure expectedClassStructure = new ExpectedClassStructure(expectedClassName, expectedPackageName, expectedClassJSON);
                tests.add(dynamicTest("testConstructors[" + expectedClassName + "]", () -> testConstructors(expectedClassStructure)));
            }
        }
        if (tests.size() == 0) {
            fail("No tests for constructors available in the structural oracle (test.json). Either provide constructor information or delete ConstructorTest.java!");
        }
        // Using a custom URI here to workaround surefire rendering the JUnit XML without the correct test names.
        return dynamicContainer(getClass().getName(), new URI(getClass().getName()), tests.stream());
    }

    /**
     * This method gets passed the expected class structure generated by the method generateTestsForAllClasses(), checks if the class is found
     * at all in the assignment and then proceeds to check its constructors.
     * @param expectedClassStructure: The class structure that we expect to find and test against.
     */
    public void testConstructors(ExpectedClassStructure expectedClassStructure) {
        String expectedClassName = expectedClassStructure.getExpectedClassName();
        Class<?> actualClass = findClassForTestType(expectedClassStructure, "constructor");

        if (expectedClassStructure.hasProperty(JSON_PROPERTY_CONSTRUCTORS)) {
            JSONArray expectedConstructors = expectedClassStructure.getPropertyAsJsonArray(JSON_PROPERTY_CONSTRUCTORS);
            checkConstructors(expectedClassName, actualClass, expectedConstructors);
        }
    }

    /**
     * This method checks if a observed class' constructors match the expected ones defined in the structure oracle.
     * @param expectedClassName: The simple name of the class, mainly used for error messages.
     * @param observedClass: The class that needs to be checked as a Class object.
     * @param expectedConstructors: The information on the expected constructors contained in a JSON array. This information consists
     * of the parameter types and the visibility modifiers.
     */
    private void checkConstructors(String expectedClassName, Class<?> observedClass, JSONArray expectedConstructors) {
        for (int i = 0; i < expectedConstructors.length(); i++) {
            JSONObject expectedConstructor = expectedConstructors.getJSONObject(i);
            JSONArray expectedParameters = expectedConstructor.has(JSON_PROPERTY_PARAMETERS) ? expectedConstructor.getJSONArray(JSON_PROPERTY_PARAMETERS) : new JSONArray();
            JSONArray expectedModifiers = expectedConstructor.has(JSON_PROPERTY_MODIFIERS) ? expectedConstructor.getJSONArray(JSON_PROPERTY_MODIFIERS) : new JSONArray();
            JSONArray expectedAnnotations = expectedConstructor.has(JSON_PROPERTY_ANNOTATIONS) ? expectedConstructor.getJSONArray(JSON_PROPERTY_ANNOTATIONS) : new JSONArray();

            boolean parametersAreRight = false;
            boolean modifiersAreRight = false;
            boolean annotationsAreRight = false;

            for (Constructor<?> observedConstructor : observedClass.getDeclaredConstructors()) {
                Class<?>[] observedParameters = observedConstructor.getParameterTypes();
                String[] observedModifiers = Modifier.toString(observedConstructor.getModifiers()).split(" ");
                Annotation[] observedAnnotations = observedConstructor.getAnnotations();

                parametersAreRight = checkParameters(observedParameters, expectedParameters);
                modifiersAreRight = checkModifiers(observedModifiers, expectedModifiers);
                annotationsAreRight = checkAnnotations(observedAnnotations, expectedAnnotations);

                // If both are correct, then we found our constructor and we can break the loop
                if (parametersAreRight && modifiersAreRight && annotationsAreRight) {
                    break;
                }
            }

            String expectedConstructorInformation = "the expected constructor of the class '" + expectedClassName + "' with "
                + ((expectedParameters.length() == 0) ? "no parameters" : "the parameters: " + expectedParameters.toString());

            if (!parametersAreRight) {
                fail("The parameters of " + expectedConstructorInformation + " are not implemented as expected.");
            }
            if (!modifiersAreRight) {
                fail("The access modifiers of " + expectedConstructorInformation + " are not implemented as expected.");
            }
            if (!annotationsAreRight) {
                fail("The annotation(s) of " + expectedConstructorInformation + " are not implemented as expected.");
            }
        }
    }
}
