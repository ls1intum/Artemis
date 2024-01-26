package de.tum.in.www1.artemis.service.connectors.hades.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;

import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.TestwiseCoverageReportDTO;
import de.tum.in.www1.artemis.service.dto.*;

public class HadesBuildResultNotificationDTO extends AbstractBuildResultNotificationDTO {

    private String jobName;

    @JsonProperty("assignmentRepoBranchName")
    private String assignmentRepoBranchName;

    @JsonProperty("assignmentRepoCommitHash")
    private String assignmentRepoCommitHash;

    @JsonProperty("testsRepoCommitHash")
    private String testsRepoCommitHash;

    @JsonProperty("isBuildSuccessful") // For some reason this annotation is necessary for jackson to work
    private boolean isBuildSuccessful;

    // This is the timestamp when the build was completed
    // Hades sends an RFC3339 formatted date string, e.g. "2024-01-24T14:11:46Z"
    @JsonProperty("buildCompletionTime")
    private ZonedDateTime buildRunDate;

    @JsonProperty("buildJobs") // For some reason this annotation is necessary for jackson to work
    private List<HadesBuildJobResultDTO> buildJobs;

    // empty constructor needed for Jackson
    public HadesBuildResultNotificationDTO() {
    }

    public HadesBuildResultNotificationDTO(String jobName, String assignmentRepoBranchName, String assignmentRepoCommitHash, String testsRepoCommitHash, boolean isBuildSuccessful,
            ZonedDateTime buildRunDate, List<HadesBuildJobResultDTO> buildJobs) {
        this.jobName = jobName;
        this.assignmentRepoBranchName = assignmentRepoBranchName;
        this.assignmentRepoCommitHash = assignmentRepoCommitHash;
        this.testsRepoCommitHash = testsRepoCommitHash;
        this.isBuildSuccessful = isBuildSuccessful;
        this.buildRunDate = buildRunDate;
        this.buildJobs = buildJobs;
    }

    @Override
    public ZonedDateTime getBuildRunDate() {
        return buildRunDate;
    }

    @Override
    public Optional<String> getCommitHashFromAssignmentRepo() {
        return Optional.ofNullable(assignmentRepoCommitHash);
    }

    @Override
    public Optional<String> getCommitHashFromTestsRepo() {
        return Optional.ofNullable(testsRepoCommitHash);
    }

    @Override
    public Optional<String> getBranchNameFromAssignmentRepo() {
        return Optional.ofNullable(assignmentRepoBranchName);
    }

    @Override
    public boolean isBuildSuccessful() {
        return isBuildSuccessful;
    }

    @Override
    public Double getBuildScore() {
        return 0D;
    }

    @Override
    public boolean hasArtifact() {
        return false;
    }

    @Override
    public boolean hasLogs() {
        return false;
    }

    @Override
    public List<BuildLogEntry> extractBuildLogs() {
        return null;
    }

    @Override
    public List<HadesBuildJobResultDTO> getBuildJobs() {
        return ObjectUtils.firstNonNull(buildJobs, List.of());
    }

    @Override
    public List<StaticCodeAnalysisReportDTO> getStaticCodeAnalysisReports() {
        return null;
    }

    @Override
    public List<TestwiseCoverageReportDTO> getTestwiseCoverageReports() {
        return null;
    }

    public String getJobName() {
        return jobName;
    }
}
