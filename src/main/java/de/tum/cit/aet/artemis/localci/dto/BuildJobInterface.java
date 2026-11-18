package de.tum.cit.aet.artemis.localci.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.dto.TestCaseBase;

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

    /**
     * Gets the skipped tests of the build job (e.g. a disabled test or a failed assumption). Defaults to an empty list
     * for build jobs that do not report skipped tests, including those deserialized from an older build agent.
     *
     * @return list of skipped tests.
     */
    default List<? extends TestCaseBase> skippedTests() {
        return List.of();
    }
}
