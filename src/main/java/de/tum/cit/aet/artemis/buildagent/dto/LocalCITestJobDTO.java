package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.dto.TestCaseBase;

/**
 * Represents the information about one test case, including the test case's name and potential error messages that indicate what went wrong.
 *
 * @param name         name of the test case.
 * @param testMessages list of error messages.
 * @param testClass    binary name of the class declaring the test, or null if the result file does not report it. Null is also what entries from a build agent
 *                         that predates this component deserialize to.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LocalCITestJobDTO(String name, List<String> testMessages, String testClass) implements TestCaseBase, Serializable {

    /**
     * Creates a test result without class information.
     *
     * @param name         name of the test case.
     * @param testMessages list of error messages.
     */
    public LocalCITestJobDTO(String name, List<String> testMessages) {
        this(name, testMessages, null);
    }
}
