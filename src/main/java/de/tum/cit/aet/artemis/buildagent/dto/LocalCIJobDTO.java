package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.localci.dto.BuildJobInterface;

/**
 * Represents all the information returned by the local CI system about a job.
 * In the current implementation of local CI, there is always one job per build.
 *
 * @param failedTests     list of failed tests.
 * @param successfulTests list of successful tests.
 * @param skippedTests    list of skipped tests (e.g. a disabled test or a failed assumption). Empty for results from a build agent that predates this component.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LocalCIJobDTO(List<LocalCITestJobDTO> failedTests, List<LocalCITestJobDTO> successfulTests, List<LocalCITestJobDTO> skippedTests)
        implements BuildJobInterface, Serializable {

    public LocalCIJobDTO {
        failedTests = Objects.requireNonNullElse(failedTests, new ArrayList<>());
        successfulTests = Objects.requireNonNullElse(successfulTests, new ArrayList<>());
        skippedTests = Objects.requireNonNullElse(skippedTests, new ArrayList<>());
    }

    /**
     * Creates a job without skipped tests.
     *
     * @param failedTests     list of failed tests.
     * @param successfulTests list of successful tests.
     */
    public LocalCIJobDTO(List<LocalCITestJobDTO> failedTests, List<LocalCITestJobDTO> successfulTests) {
        this(failedTests, successfulTests, new ArrayList<>());
    }
}
