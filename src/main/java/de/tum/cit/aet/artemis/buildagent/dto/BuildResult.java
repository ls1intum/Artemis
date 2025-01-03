package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.dto.AbstractBuildResultNotificationDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildJobDTOInterface;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;
import de.tum.cit.aet.artemis.programming.dto.TestCaseBaseDTO;

/**
 * Represents all the information returned by the local CI system about a build.
 * Note: due to limitations with inheritance, we cannot declare this as a record, but we can use it in a similar way with final fields.
 */

// NOTE: this data structure is used in shared code between core and build agent nodes. Changing it requires that the shared data structures in Hazelcast (or potentially Redis)
// in the future are migrated or cleared. Changes should be communicated in release notes as potentially breaking changes.
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildResult(String assignmentRepoBranchName, String assignmentRepoCommitHash, String testsRepoCommitHash, boolean isBuildSuccessful, ZonedDateTime buildRunDate,
        List<LocalCIJobDTO> jobs, List<BuildLogDTO> buildLogEntries, List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports, boolean hasLogs)
        implements AbstractBuildResultNotificationDTO, Serializable {

    public BuildResult {
        if (buildRunDate == null) {
            buildRunDate = ZonedDateTime.now();
        }
        if (jobs == null) {
            jobs = new ArrayList<>();
        }
        if (buildLogEntries == null) {
            buildLogEntries = new ArrayList<>();
            hasLogs = false;
        }
        if (staticCodeAnalysisReports == null) {
            staticCodeAnalysisReports = new ArrayList<>();
        }
    }

    public BuildResult(String branch, String assignmentRepoCommitHash, String testsRepoCommitHash, List<BuildLogDTO> buildLogs, boolean isBuildSuccessful) {
        this(branch, assignmentRepoCommitHash, testsRepoCommitHash, isBuildSuccessful, null, null, buildLogs, null, true);
    }

    @Override
    public Double buildScore() {
        // the real score is calculated in the grading service
        return 0D;
    }

    /**
     * NOTE: Local CI does not support checking for artifacts as of now.
     *
     * @return will always return false because LocalCI does not support checking for artifacts.
     */
    @Override
    public boolean hasArtifact() {
        return false;
    }

    @Override
    public List<BuildLogEntry> extractBuildLogs() {
        // convert the buildLogEntry DTOs to BuildLogEntry objects
        return buildLogEntries.stream().map(log -> new BuildLogEntry(log.time(), log.log())).toList();
    }

    @Override
    public List<? extends BuildJobDTOInterface> getBuildJobs() {
        return jobs;
    }

    /**
     * Represents all the information returned by the local CI system about a job.
     * In the current implementation of local CI, there is always one job per build.
     *
     * @param failedTests     list of failed tests.
     * @param successfulTests list of successful tests.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record LocalCIJobDTO(List<LocalCITestJobDTO> failedTests, List<LocalCITestJobDTO> successfulTests) implements BuildJobDTOInterface, Serializable {
    }

    /**
     * Represents the information about one test case, including the test case's name and potential error messages that indicate what went wrong.
     *
     * @param name   name of the test case.
     * @param errors list of error messages.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record LocalCITestJobDTO(String name, List<String> errors) implements TestCaseBaseDTO, Serializable {

        @Override
        public List<String> getTestMessages() {
            return errors;
        }
    }
}
