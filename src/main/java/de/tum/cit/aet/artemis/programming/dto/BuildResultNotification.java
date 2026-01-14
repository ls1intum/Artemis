package de.tum.cit.aet.artemis.programming.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public interface BuildResultNotification {

    ZonedDateTime buildRunDate();

    @Nullable
    String assignmentRepoCommitHash();

    @Nullable
    String testsRepoCommitHash();

    @Nullable
    String assignmentRepoBranchName();

    boolean isBuildSuccessful();

    Double buildScore();

    /**
     * Get the commit hash from the build result, the commit hash will be different for submission types or null.
     *
     * @param submissionType describes why the build was started.
     * @return if the commit hash for the given submission type was found, otherwise empty.
     */
    @Nullable
    default String commitHash(SubmissionType submissionType) {
        final var isAssignmentSubmission = List.of(SubmissionType.MANUAL, SubmissionType.INSTRUCTOR).contains(submissionType);
        if (isAssignmentSubmission) {
            return assignmentRepoCommitHash();
        }
        else if (submissionType.equals(SubmissionType.TEST)) {
            return testsRepoCommitHash();
        }
        return null;
    }

    boolean hasArtifact();

    boolean hasLogs();

    List<BuildLogEntry> extractBuildLogs();

    /**
     * Gets the build jobs that are part of the build result.
     *
     * @return list of build jobs.
     */
    @JsonIgnore
    List<? extends BuildJobInterface> jobs();

    /**
     * Gets the static code analysis reports that are part of the build result.
     *
     * @return list of static code analysis reports.
     */
    List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports();
}
