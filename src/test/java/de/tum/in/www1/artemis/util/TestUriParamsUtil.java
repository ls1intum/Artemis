package de.tum.in.www1.artemis.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.http.NameValuePair;

public class TestUriParamsUtil {

    /**
     * Asserts if a list of URI parameters contains an entry pair with the given name and value
     * @param uriParams the list of URI parameters
     * @param name the name of the URI parameter
     * @param value the value of the URI parameter
     */
    public static void assertUriParamsContain(List<NameValuePair> uriParams, String name, String value) {
        assertUriParamsContain(uriParams, name, value, false);
    }

    /**
     * Asserts if a list of URI parameters contains an entry pair with the given name and value. If specified the
     * assert does not fail if the given value is a substring of the actual URI parameter value.
     * @param uriParams the list of URI parameters
     * @param name the name of the URI parameter
     * @param value the value of the URI parameter
     * @param allowValueSubString whether or not to allow that the given value is a sub string of the actual value
     */
    public static void assertUriParamsContain(List<NameValuePair> uriParams, String name, String value, boolean allowValueSubString) {
        List<NameValuePair> matching = uriParams.stream().filter(param -> param.getName().equals(name)).collect(Collectors.toList());
        if (matching.isEmpty()) {
            fail("No URI param with name '" + name + "'");
        }
        if (matching.size() > 1) {
            fail("Multiple URI param with name '" + name + "'");
        }
        NameValuePair param = matching.get(0);

        if (allowValueSubString) {
            assertTrue(param.getValue().contains(value),
                    "Invalid value for URI param with name '" + name + "': Actual value '" + param.getValue() + "' does not contain expected '" + value + "'");
            return;
        }
        assertEquals(value, param.getValue(), "Invalid value for URI param with name '" + name + "'");
    }

    /**
     * Asserts if a list of URI parameters contains an entry pair with the given name regardless of its value
     * @param uriParams the list of URI parameters
     * @param name the name of the URI parameter
     */
    public static void assertUriParamsContain(List<NameValuePair> uriParams, String name) {
        assertEquals(1L, uriParams.stream().filter(param -> param.getName().equals(name)).count(), "No URI param with name '" + name + "'");
    }

}
