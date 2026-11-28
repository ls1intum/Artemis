package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Interface for DTOs that represent a test case.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public interface TestCaseBase {

    /**
     * Gets the name of the test case
     *
     * @return the name of the test case
     */
    String name();

    /**
     * Gets the messages of the test case (typically error messages)
     *
     * @return the messages of the test case
     */
    @JsonIgnore
    List<String> testMessages();

    /**
     * Gets the binary name of the test class that declares the test case, e.g. {@code de.tum.Foo$Bar} for a nested class.
     * For a failure that happened outside of any single test (such as in {@code @BeforeAll}), this is the class that failed.
     *
     * @return the class name, or null if the build does not report it
     */
    default String testClass() {
        return null;
    }
}
