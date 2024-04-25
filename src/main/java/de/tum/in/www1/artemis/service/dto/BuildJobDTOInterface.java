package de.tum.in.www1.artemis.service.dto;

import java.util.List;

/**
 * Interface for DTOs that represent a build job.
 */
public interface BuildJobDTOInterface {

    /**
     * Gets the failed tests of the build job.
     *
     * @return list of failed tests.
     */
    List<? extends TestCaseDTOInterface> getFailedTests();

    /**
     * Gets the successful tests of the build job.
     *
     * @return list of successful tests.
     */
    List<? extends TestCaseDTOInterface> getSuccessfulTests();
}
