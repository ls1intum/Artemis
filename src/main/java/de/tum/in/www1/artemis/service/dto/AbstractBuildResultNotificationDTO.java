package de.tum.in.www1.artemis.service.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.TestwiseCoverageReportDTO;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class AbstractBuildResultNotificationDTO {

    public abstract ZonedDateTime getBuildRunDate();

    public abstract Optional<String> getCommitHashFromAssignmentRepo();

    public abstract Optional<String> getCommitHashFromTestsRepo();

    public abstract Optional<String> getBranchNameFromAssignmentRepo();

    public abstract boolean isBuildSuccessful();

    public abstract Double getBuildScore();

    /**
     * Get the commit hash from the build result, the commit hash will be different for submission types or null.
     *
     * @param submissionType describes why the build was started.
     * @return if the commit hash for the given submission type was found, otherwise empty.
     */
    public Optional<String> getCommitHash(SubmissionType submissionType) {
        final var isAssignmentSubmission = List.of(SubmissionType.MANUAL, SubmissionType.INSTRUCTOR, SubmissionType.ILLEGAL).contains(submissionType);
        if (isAssignmentSubmission) {
            return getCommitHashFromAssignmentRepo();
        }
        else if (submissionType.equals(SubmissionType.TEST)) {
            return getCommitHashFromTestsRepo();
        }
        else {
            return Optional.empty();
        }
    }

    public abstract boolean hasArtifact();

    public abstract boolean hasLogs();

    public abstract List<BuildLogEntry> extractBuildLogs(ProgrammingLanguage programmingLanguage);

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
    @JsonIgnore
    public abstract List<StaticCodeAnalysisReportDTO> getStaticCodeAnalysisReports();

    /**
     * Gets the testwise coverage reports that are part of the build result.
     *
     * @return list of testwise coverage reports.
     */
    @JsonIgnore
    public abstract List<TestwiseCoverageReportDTO> getTestwiseCoverageReports();
}
