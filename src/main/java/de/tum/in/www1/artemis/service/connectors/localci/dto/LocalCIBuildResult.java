package de.tum.in.www1.artemis.service.connectors.localci.dto;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.text.StringEscapeUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

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
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LocalCIBuildResult extends AbstractBuildResultNotificationDTO {

    private final String assignmentRepoBranchName;

    private final String assignmentRepoCommitHash;

    private final String testsRepoCommitHash;

    private final boolean isBuildSuccessful;

    private final ZonedDateTime buildRunDate;

    private final List<LocalCIJobDTO> jobs;

    private final String description;

    public LocalCIBuildResult(String assignmentRepoBranchName, String assignmentRepoCommitHash, String testsRepoCommitHash, boolean isBuildSuccessful, ZonedDateTime buildRunDate,
            List<LocalCIJobDTO> jobs, String description) {
        this.assignmentRepoBranchName = assignmentRepoBranchName;
        this.assignmentRepoCommitHash = assignmentRepoCommitHash;
        this.testsRepoCommitHash = testsRepoCommitHash;
        this.isBuildSuccessful = isBuildSuccessful;
        this.buildRunDate = buildRunDate;
        this.jobs = jobs;
        this.description = description;
    }

    @Override
    public ZonedDateTime getBuildRunDate() {
        return buildRunDate;
    }

    @Override
    public Optional<String> getCommitHashFromAssignmentRepo() {
        return Optional.of(assignmentRepoCommitHash);
    }

    @Override
    public Optional<String> getCommitHashFromTestsRepo() {
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
     * Local CI does not support artifacts as of now.
     *
     * @return false
     */
    @Override
    public boolean hasArtifact() {
        return false;
    }

    @Override
    public boolean hasLogs() {
        return jobs.stream().anyMatch(job -> !job.logs().isEmpty());
    }

    @Override
    public List<BuildLogEntry> extractBuildLogs(ProgrammingLanguage programmingLanguage) {
        List<BuildLogEntry> buildLogEntries = new ArrayList<>();

        // Store logs into database. Append logs of multiple jobs.
        for (var job : jobs) {
            for (var localCILog : job.logs()) {
                // We have to unescape the HTML as otherwise symbols like '<' are not displayed correctly
                buildLogEntries.add(new BuildLogEntry(localCILog.date(), StringEscapeUtils.unescapeHtml4(localCILog.log())));
            }
        }

        return buildLogEntries;
    }

    @JsonIgnore
    @Override
    public List<? extends BuildJobDTOInterface> getBuildJobs() {
        return jobs;
    }

    @JsonIgnore
    @Override
    public List<StaticCodeAnalysisReportDTO> getStaticCodeAnalysisReports() {
        return jobs.stream().flatMap(job -> job.staticCodeAnalysisReports().stream()).toList();
    }

    @JsonIgnore
    @Override
    public List<TestwiseCoverageReportDTO> getTestwiseCoverageReports() {
        return jobs.stream().flatMap(job -> job.testwiseCoverageReport().stream()).toList();
    }

    @JsonIgnore
    @Override
    public String getDescription() {
        return description;
    }

    /**
     * Represents all the information returned by the local CI system about a job.
     * In the current implementation of local CI, there is always one job per build.
     *
     * @param failedTests               list of failed tests.
     * @param successfulTests           list of successful tests.
     * @param staticCodeAnalysisReports list of static code analysis reports.
     * @param testwiseCoverageReport    list of testwise coverage reports.
     * @param logs                      list of logs.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record LocalCIJobDTO(List<LocalCITestJobDTO> failedTests, List<LocalCITestJobDTO> successfulTests, List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports,
            List<TestwiseCoverageReportDTO> testwiseCoverageReport, List<LocalCIBuildLogDTO> logs) implements BuildJobDTOInterface {

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
     * @param name
     * @param errors
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
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
