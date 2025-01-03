package de.tum.cit.aet.artemis.programming.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Interface for DTOs that represent a build job.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public interface BuildJobInterface {

    /**
     * Gets the failed tests of the build job.
     *
     * @return list of failed tests.
     */
    List<? extends TestCaseBase> failedTests();

    /**
     * Gets the successful tests of the build job.
     *
     * @return list of successful tests.
     */
    List<? extends TestCaseBase> successfulTests();
}
