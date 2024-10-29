package de.tum.cit.aet.artemis.programming.dto;

import java.time.ZonedDateTime;
import java.util.List;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.service.ci.notification.dto.TestwiseCoverageReportDTO;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
// TODO: convert subclasses to records
public abstract class AbstractBuildResultNotificationDTO {

    public abstract ZonedDateTime getBuildRunDate();

    @Nullable
    protected abstract String getCommitHashFromAssignmentRepo();

    @Nullable
    protected abstract String getCommitHashFromTestsRepo();

    @Nullable
    public abstract String getBranchNameFromAssignmentRepo();

    public abstract boolean isBuildSuccessful();

    public abstract Double getBuildScore();

    /**
     * Get the commit hash from the build result, the commit hash will be different for submission types or null.
     *
     * @param submissionType describes why the build was started.
     * @return if the commit hash for the given submission type was found, otherwise empty.
     */
    @Nullable
    public String getCommitHash(SubmissionType submissionType) {
        final var isAssignmentSubmission = List.of(SubmissionType.MANUAL, SubmissionType.INSTRUCTOR, SubmissionType.ILLEGAL).contains(submissionType);
        if (isAssignmentSubmission) {
            return getCommitHashFromAssignmentRepo();
        }
        else if (submissionType.equals(SubmissionType.TEST)) {
            return getCommitHashFromTestsRepo();
        }
        return null;
    }

    public abstract boolean hasArtifact();

    public abstract boolean hasLogs();

    public abstract List<BuildLogEntry> extractBuildLogs();

    /**
     * Gets the build jobs that are part of the build result.
     *
     * @return list of build jobs.
     */
    @JsonIgnore
    public abstract List<? extends BuildJobDTOInterface> getBuildJobs();

    /**
     * Gets the static code analysis reports that are part of the build result.
     *
     * @return list of static code analysis reports.
     */
    public abstract List<StaticCodeAnalysisReportDTO> getStaticCodeAnalysisReports();

    /**
     * Gets the test-wise coverage reports that are part of the build result.
     *
     * @return list of test-wise coverage reports.
     */
    public abstract List<TestwiseCoverageReportDTO> getTestwiseCoverageReports();
}
