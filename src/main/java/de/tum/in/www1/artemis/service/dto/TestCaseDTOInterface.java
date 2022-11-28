package de.tum.in.www1.artemis.service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Interface for DTOs that represent a test case.
 */
public interface TestCaseDTOInterface {

    /**
     * Gets the name of the test case
     *
     * @return the name of the test case
     */
    String getName();

    /**
     * Gets the message of the test case
     *
     * @return the message of the test case
     */
    @JsonIgnore
    List<String> getMessage();
}
