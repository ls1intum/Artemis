package de.tum.in.www1.artemis.service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Interface for DTOs that represent a build job.
 */
public interface BuildJobDTOInterface {

    /**
     * Gets the failed tests of the build job.
     *
     * @return list of failed tests.
     */
    @JsonIgnore
    List<? extends TestCaseDTOInterface> getFailedTests();

    /**
     * Gets the successful tests of the build job.
     *
     * @return list of successful tests.
     */
    @JsonIgnore
    List<? extends TestCaseDTOInterface> getSuccessfulTests();
}
