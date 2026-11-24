package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The test classes of the tests repository and their test cases, from which instructors assign tests to a security policy.
 *
 * @param testClasses the classes of the test sources, sorted by their fully qualified name: the test classes and the other classes, such as helpers, that a
 *                        policy can declare as test code
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SecurityTestStructureDTO(List<TestClassDTO> testClasses) {

    /**
     * A test class.
     *
     * @param qualifiedName   its fully qualified name
     * @param testCases       the names of its test methods, in their order in the source
     * @param testClass       whether it is a test class whose test cases can be assigned to a policy; false for a helper class without tests
     * @param switchedOff     whether its {@code @Policy} annotation is switched off, so its test cases run without the sandbox and cannot be assigned
     * @param fixedCases      the test cases whose own {@code @Policy} annotation switches the sandbox off or cannot be evaluated, so they cannot be assigned
     * @param overloadedCases the test cases that more than one test method has; they use a policy only together with their whole class
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TestClassDTO(String qualifiedName, List<String> testCases, boolean testClass, boolean switchedOff, List<String> fixedCases, List<String> overloadedCases) {
    }
}
