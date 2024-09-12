package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Interface for DTOs that represent a test case.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public interface TestCaseBaseDTO {

    /**
     * Gets the name of the test case
     *
     * @return the name of the test case
     */
    String getName();

    /**
     * Gets the messages of the test case (typically error messages)
     *
     * @return the messages of the test case
     */
    @JsonIgnore
    List<String> getTestMessages();
}
