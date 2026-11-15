package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The test classes of the tests repository and their test cases, from which instructors assign tests to a security policy.
 *
 * @param testClasses the test classes, sorted by their fully qualified name
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SecurityTestStructureDTO(List<TestClassDTO> testClasses) {

    /**
     * A test class.
     *
     * @param qualifiedName its fully qualified name
     * @param testCases     the names of its test methods, in their order in the source
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TestClassDTO(String qualifiedName, List<String> testCases) {
    }
}
