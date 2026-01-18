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
     * Gets the class name of the test case, if available.
     * This is useful for identifying which test class an initialization error belongs to.
     *
     * @return the class name of the test case, or null if not available
     */
    @JsonIgnore
    default String classname() {
        return null;
    }

    /**
     * Gets the messages of the test case (typically error messages)
     *
     * @return the messages of the test case
     */
    @JsonIgnore
    List<String> testMessages();
}
