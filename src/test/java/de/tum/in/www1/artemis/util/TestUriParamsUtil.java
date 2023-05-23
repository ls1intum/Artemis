package de.tum.in.www1.artemis.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.http.NameValuePair;

public class TestUriParamsUtil {

    /**
     * Asserts if a list of URI parameters contains an entry pair with the given name and value
     *
     * @param uriParams the list of URI parameters
     * @param name      the name of the URI parameter
     * @param value     the value of the URI parameter
     */
    public static void assertUriParamsContain(List<NameValuePair> uriParams, String name, String value) {
        assertUriParamsContain(uriParams, name, value, false);
    }

    /**
     * Asserts if a list of URI parameters contains an entry pair with the given name and value. If specified, the
     * assert does not fail if the given value is a substring of the actual URI parameter value.
     *
     * @param uriParams           the list of URI parameters
     * @param name                the name of the URI parameter
     * @param value               the value of the URI parameter
     * @param allowValueSubString whether to allow that the given value is a sub string of the actual value
     */
    public static void assertUriParamsContain(List<NameValuePair> uriParams, String name, String value, boolean allowValueSubString) {
        List<NameValuePair> matching = uriParams.stream().filter(param -> param.getName().equals(name)).toList();

        assertThat(matching).as("No URI param with name '" + name + "'").isNotEmpty();
        assertThat(matching).as("Multiple URI param with name '" + name + "'").hasSizeLessThanOrEqualTo(1);

        NameValuePair param = matching.get(0);

        if (allowValueSubString) {
            assertThat(param.getValue())
                    .as("Invalid value for URI param with name '" + name + "': Actual value '" + param.getValue() + "' does not contain expected '" + value + "'").contains(value);
            return;
        }
        assertThat(param.getValue()).as("Invalid value for URI param with name '" + name + "'").isEqualTo(value);
    }

    /**
     * Asserts if a list of URI parameters contains an entry pair with the given name regardless of its value
     *
     * @param uriParams the list of URI parameters
     * @param name      the name of the URI parameter
     */
    public static void assertUriParamsContain(List<NameValuePair> uriParams, String name) {
        assertThat(uriParams).filteredOn(param -> param.getName().equals(name)).as("No URI param with name '" + name + "'").hasSize(1);
    }

}
