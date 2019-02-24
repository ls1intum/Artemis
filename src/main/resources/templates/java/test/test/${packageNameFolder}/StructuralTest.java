package ${packageName};

import static org.junit.Assert.*;
import de.tum.in.www1.testutils.*;
import java.io.*;
import java.net.URL;
import java.util.*;
import org.json.*;

/**
 * @author Stephan Krusche (krusche@in.tum.de)
 * @version 1.5 (25.01.2019)
 *
 * This test evaluates if the following specified elements of a given class in the structure oracle are
 * correctly implemented (in case they are specified):
 *
 * 1) The hierarchy, i.e. if a specified superclass is extended and if specified interfaces are implemented,
 * 2) The declared constructors their including access modifiers and parameters,
 * 3) The declared methods including their access modifiers, parameters and return type,
 * 4) The declared attributes including their access modifiers and types,
 * 5) The declared enum values of an enum.
 */
public class StructuralTest {

    protected String expectedClassName;
    protected String expectedPackageName;
    protected JSONObject expectedClassJSON;

    protected static JSONArray structureOracleJSON = retrieveStructureOracleJSON("test.json");

    public StructuralTest(String expectedClassName, String expectedPackageName, JSONObject expectedClassJSON) {
        this.expectedClassName = expectedClassName;
        this.expectedPackageName = expectedPackageName;
        this.expectedClassJSON = expectedClassJSON;
    }

    /**
     * Scans the project and returns the class, if it's found. If not, return the message of the NamesScanner.
     * @param typeOfTest: The name of the test type that currently called the NamesScanner. The name is displayed in the feedback, if it's negative.
     * @return The current class that undergoes the tests.
     */
    protected Class<?> findClassForTestType(String typeOfTest) {
        ClassNameScanner classNameScanner = new ClassNameScanner(expectedClassName, expectedPackageName, structureOracleJSON);
        ScanResultType scanResultEnum = classNameScanner.getScanResult().getResult();
        String classNameScanMessage = classNameScanner.getScanResult().getMessage();

        assertTrue(classNameScanMessage, scanResultEnum.equals(ScanResultType.CORRECTNAME_CORRECTPLACE));

        try {
            return Class.forName(expectedPackageName + "." + expectedClassName);
        } catch (ClassNotFoundException e) {
            fail("Problem during " + typeOfTest + " test: " + classNameScanMessage);
            return null;
        }
    }

    /**
     * This method checks if the visibility modifiers of the observed structural element match the ones in the
     * expected structural element.
     * @param observedModifiers: The observed modifiers as a string array.
     * @param expectedModifiers: The expected modifiers as a JSONArray.
     * @return True if they match, false otherwise.
     */
    protected boolean checkModifiers(String[] observedModifiers, JSONArray expectedModifiers) {

        // If both the observed and expected elements have no modifiers, then they match.
        // A note: for technical reasons, we get in case of no observed modifiers, a string array with an empty string.
        if(observedModifiers.equals(new String[]{""}) && expectedModifiers.length() == 0) {
            return true;
        }

        // If the number of the modifiers does not match, then the modifiers per se do not match either.
        if(observedModifiers.length != expectedModifiers.length()) {
            return false;
        }

        // Otherwise check if each expected modifier is contained in the array of the observed ones.
        // If at least one isn't, then the modifiers don't match.
        boolean allModifiersAreImplemented = true;
        for(Object expectedModifier : expectedModifiers) {
            allModifiersAreImplemented &= Arrays.asList(observedModifiers).contains((String) expectedModifier);
        }

        return allModifiersAreImplemented;
    }

    /**
     * This method checks if the parameters of the actual structural element (in this case method or constructor)
     * match the ones in the expected structural element.
     * @param observedParameters: The actual parameter types as a classes array.
     * @param expectedParameters: The expected parameter type names as a JSONArray.
     * @return True if they match, false otherwise.
     */
    protected boolean checkParameters(Class<?>[] observedParameters, JSONArray expectedParameters) {

        // If both the observed and expected elements have no parameters, then they match.
        if(observedParameters.length == 0 && expectedParameters.length() == 0) {
            return true;
        }

        // If the number of parameters do not match, then the parameters per se do not match either.
        if(observedParameters.length != expectedParameters.length()) {
            return false;
        }

        // Create hash tables to store how often a parameter type occurs.
        // Checking the occurrences of a certain parameter type is enough, since the parameter names
        // or their order are not relevant to us.
        String[] expectedParameterTypeNames = new String[expectedParameters.length()];
        for(int i = 0; i < expectedParameters.length(); i++) {
            expectedParameterTypeNames[i] = expectedParameters.getString(i);
        }
        Map<String, Integer> expectedParametersHashtable = createParametersHashMap(expectedParameterTypeNames);

        String[] observedParameterTypeNames = new String[observedParameters.length];
        for(int i = 0; i < observedParameters.length; i++) {
            observedParameterTypeNames[i] = observedParameters[i].getSimpleName();
        }
        Map<String, Integer> observedParametersHashtable = createParametersHashMap(observedParameterTypeNames);

        return expectedParametersHashtable.equals(observedParametersHashtable);
    }

    /**
     * This method creates a hash table where the name of a parameter type is hashed to the number of the occurrences in the passed string collection.
     * @param parameterTypeNames
     * @return
     */
    private Map<String, Integer> createParametersHashMap(String... parameterTypeNames) {
        Map<String, Integer> parametersHashTable = new HashMap<String, Integer>();

        for(String parameterTypeName : parameterTypeNames) {
            if(!parametersHashTable.containsKey(parameterTypeName)) {
                parametersHashTable.put(parameterTypeName, 1);
            } else {
                Integer currentParameterCount = parametersHashTable.get(parameterTypeName);
                parametersHashTable.replace(parameterTypeName, currentParameterCount, currentParameterCount++);
            }
        }

        return parametersHashTable;
    }

    /**
     * This method retrieves the JSON array in the structure oracle.
     * @param structureOracleFileName: The file name of the structure oracle file, which is placed in the same folder as StructuralTest.
     * @return The JSONArray representation of the structure oracle.
     */
    private static JSONArray retrieveStructureOracleJSON(String structureOracleFileName) {
        URL url = StructuralTest.class.getResource(structureOracleFileName);

        BufferedReader bufferedReader = null;
        StringBuilder result = new StringBuilder();

        try {
            bufferedReader = new BufferedReader(new InputStreamReader(url.openStream()));
            char[] buffer = new char[8192];
            int length;

            while ((length = bufferedReader.read(buffer, 0, buffer.length)) != -1) {
                result.append(buffer, 0, length);
            }

        } catch (IOException e) {
            System.err.println("Could not open stream from URL: " + url.toString());
            e.printStackTrace();
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    System.err.println("Could not close BufferedReader.");
                    e.printStackTrace();
                }
            }
        }

        return new JSONArray(result.toString());
    }
}
