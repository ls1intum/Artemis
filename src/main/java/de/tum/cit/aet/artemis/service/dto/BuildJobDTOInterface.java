package de.tum.cit.aet.artemis.service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Interface for DTOs that represent a build job.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public interface BuildJobDTOInterface {

    /**
     * Gets the failed tests of the build job.
     *
     * @return list of failed tests.
     */
    List<? extends TestCaseBaseDTO> getFailedTests();

    /**
     * Gets the successful tests of the build job.
     *
     * @return list of successful tests.
     */
    List<? extends TestCaseBaseDTO> getSuccessfulTests();
}
