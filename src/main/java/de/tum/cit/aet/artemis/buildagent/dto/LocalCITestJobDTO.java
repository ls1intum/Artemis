package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.dto.TestCaseBase;

/**
 * Represents the information about one test case, including the test case's name and potential error messages that indicate what went wrong.
 *
 * @param name         name of the test case.
 * @param classname    the fully qualified class name of the test class (e.g., "de.tum.cit.BehaviorTest").
 *                         This is particularly useful for identifying which test class had an initialization error.
 * @param testMessages list of error messages.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LocalCITestJobDTO(String name, String classname, List<String> testMessages) implements TestCaseBase, Serializable {
}
