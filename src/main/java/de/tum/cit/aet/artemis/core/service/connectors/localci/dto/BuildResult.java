package de.tum.cit.aet.artemis.core.service.connectors.localci.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.util.ObjectUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.service.connectors.ci.notification.dto.TestwiseCoverageReportDTO;
import de.tum.cit.aet.artemis.programming.domain.BuildLogEntry;
import de.tum.cit.aet.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.cit.aet.artemis.service.dto.BuildJobDTOInterface;
import de.tum.cit.aet.artemis.service.dto.StaticCodeAnalysisReportDTO;
import de.tum.cit.aet.artemis.service.dto.TestCaseBaseDTO;

/**
 * Represents all the information returned by the local CI system about a build.
 * Note: due to limitations with inheritance, we cannot declare this as a record, but we can use it in a similar way with final fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BuildResult extends AbstractBuildResultNotificationDTO implements Serializable {

    private final String assignmentRepoBranchName;

    private final String assignmentRepoCommitHash;

    private final String testsRepoCommitHash;

    private final boolean isBuildSuccessful;

    private final ZonedDateTime buildRunDate;

    private final List<LocalCIJobDTO> jobs;

    private List<BuildLogEntry> buildLogEntries = new ArrayList<>();

    private final List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports;

    private boolean hasLogs = false;

    public BuildResult(String assignmentRepoBranchName, String assignmentRepoCommitHash, String testsRepoCommitHash, boolean isBuildSuccessful, ZonedDateTime buildRunDate,
            List<LocalCIJobDTO> jobs, List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports) {
        this.assignmentRepoBranchName = assignmentRepoBranchName;
        this.assignmentRepoCommitHash = assignmentRepoCommitHash;
        this.testsRepoCommitHash = testsRepoCommitHash;
        this.isBuildSuccessful = isBuildSuccessful;
        this.buildRunDate = buildRunDate;
        this.jobs = jobs;
        this.staticCodeAnalysisReports = staticCodeAnalysisReports;
    }

    public BuildResult(String assignmentRepoBranchName, String assignmentRepoCommitHash, String testsRepoCommitHash, boolean isBuildSuccessful) {
        this.assignmentRepoBranchName = assignmentRepoBranchName;
        this.assignmentRepoCommitHash = assignmentRepoCommitHash;
        this.testsRepoCommitHash = testsRepoCommitHash;
        this.isBuildSuccessful = isBuildSuccessful;
        this.buildRunDate = ZonedDateTime.now();
        this.jobs = new ArrayList<>();
        this.staticCodeAnalysisReports = new ArrayList<>();
    }

    @Override
    public ZonedDateTime getBuildRunDate() {
        return buildRunDate;
    }

    @Override
    protected String getCommitHashFromAssignmentRepo() {
        if (ObjectUtils.isEmpty(assignmentRepoCommitHash)) {
            return null;
        }
        return assignmentRepoCommitHash;
    }

    @Override
    protected String getCommitHashFromTestsRepo() {
        if (ObjectUtils.isEmpty(testsRepoCommitHash)) {
            return null;
        }
        return testsRepoCommitHash;
    }

    @Override
    public String getBranchNameFromAssignmentRepo() {
        return assignmentRepoBranchName;
    }

    @Override
    public boolean isBuildSuccessful() {
        return isBuildSuccessful;
    }

    @Override
    public Double getBuildScore() {
        // the real score is calculated in the grading service
        return 0D;
    }

    /**
     * Local CI does not support checking for artifacts as of now.
     * TODO LOCALVC_CI: Figure out in the build process whether an artifact was created, and return true here if yes.
     *
     * @return false
     */
    @Override
    public boolean hasArtifact() {
        return false;
    }

    @Override
    public boolean hasLogs() {
        return hasLogs;
    }

    @Override
    public List<BuildLogEntry> extractBuildLogs() {
        return buildLogEntries;
    }

    /**
     * Setter for the buildLogEntries
     *
     * @param buildLogEntries the buildLogEntries to be set
     */
    public void setBuildLogEntries(List<BuildLogEntry> buildLogEntries) {
        this.buildLogEntries = buildLogEntries;
        hasLogs = true;
    }

    @Override
    public List<? extends BuildJobDTOInterface> getBuildJobs() {
        return jobs;
    }

    @Override
    public List<StaticCodeAnalysisReportDTO> getStaticCodeAnalysisReports() {
        return staticCodeAnalysisReports;
    }

    @Override
    public List<TestwiseCoverageReportDTO> getTestwiseCoverageReports() {
        // TODO LOCALVC_CI: Implement testwise coverage and return the reports here.
        return Collections.emptyList();
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

        @Override
        public List<? extends TestCaseBaseDTO> getFailedTests() {
            return failedTests;
        }

        @Override
        public List<? extends TestCaseBaseDTO> getSuccessfulTests() {
            return successfulTests;
        }
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
        public String getName() {
            return name;
        }

        @Override
        public List<String> getTestMessages() {
            return errors;
        }
    }
}
