package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.TestwiseCoverageReportDTO;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.dto.BuildJobDTOInterface;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;
import de.tum.in.www1.artemis.service.dto.TestCaseDTOInterface;

/**
 * Represents all the information returned by the local CI system about a build.
 * Note: due to limitations with inheritance, we cannot declare this as a record, but we can use it in a similar way with final fields.
 */
public class LocalCIBuildResult extends AbstractBuildResultNotificationDTO {

    private final String assignmentRepoBranchName;

    private final String assignmentRepoCommitHash;

    private final String testsRepoCommitHash;

    private final boolean isBuildSuccessful;

    private final ZonedDateTime buildRunDate;

    private final List<LocalCIJobDTO> jobs;

    public LocalCIBuildResult(String assignmentRepoBranchName, String assignmentRepoCommitHash, String testsRepoCommitHash, boolean isBuildSuccessful, ZonedDateTime buildRunDate,
            List<LocalCIJobDTO> jobs) {
        this.assignmentRepoBranchName = assignmentRepoBranchName;
        this.assignmentRepoCommitHash = assignmentRepoCommitHash;
        this.testsRepoCommitHash = testsRepoCommitHash;
        this.isBuildSuccessful = isBuildSuccessful;
        this.buildRunDate = buildRunDate;
        this.jobs = jobs;
    }

    @Override
    public ZonedDateTime getBuildRunDate() {
        return buildRunDate;
    }

    @Override
    public Optional<String> getCommitHashFromAssignmentRepo() {
        if (assignmentRepoCommitHash.length() == 0) {
            return Optional.empty();
        }
        return Optional.of(assignmentRepoCommitHash);
    }

    @Override
    public Optional<String> getCommitHashFromTestsRepo() {
        if (testsRepoCommitHash.length() == 0) {
            return Optional.empty();
        }
        return Optional.of(testsRepoCommitHash);
    }

    @Override
    public Optional<String> getBranchNameFromAssignmentRepo() {
        return Optional.of(assignmentRepoBranchName);
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
        // TODO LOCALVC_CI: Implement the retrieval of build logs and return true here if there are any.
        return false;
    }

    @Override
    public List<BuildLogEntry> extractBuildLogs(ProgrammingLanguage programmingLanguage) {
        // TODO LOCALVC_CI: Implement the retrieval of build logs.
        return Collections.emptyList();
    }

    @Override
    public List<? extends BuildJobDTOInterface> getBuildJobs() {
        return jobs;
    }

    @Override
    public List<StaticCodeAnalysisReportDTO> getStaticCodeAnalysisReports() {
        // TODO LOCALVC_CI: Implement static code analysis and return the reports here.
        return Collections.emptyList();
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
    public record LocalCIJobDTO(List<LocalCITestJobDTO> failedTests, List<LocalCITestJobDTO> successfulTests) implements BuildJobDTOInterface {

        @Override
        public List<? extends TestCaseDTOInterface> getFailedTests() {
            return failedTests;
        }

        @Override
        public List<? extends TestCaseDTOInterface> getSuccessfulTests() {
            return successfulTests;
        }
    }

    /**
     * Represents the information about one test case, including the test case's name and potential error messages that indicate what went wrong.
     *
     * @param name   name of the test case.
     * @param errors list of error messages.
     */
    public record LocalCITestJobDTO(String name, List<String> errors) implements TestCaseDTOInterface {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<String> getMessage() {
            return errors;
        }
    }
}
