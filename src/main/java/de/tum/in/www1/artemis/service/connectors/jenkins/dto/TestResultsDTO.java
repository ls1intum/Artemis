package de.tum.in.www1.artemis.service.connectors.jenkins.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.TestwiseCoverageReportDTO;
import de.tum.in.www1.artemis.service.connectors.jenkins.JenkinsBuildLogParseUtils;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
// Note: due to limitations with inheritance, we cannot declare this as record, but we can use it in a similar way with final fields
public class TestResultsDTO extends AbstractBuildResultNotificationDTO {

    @JsonProperty("successful")
    private final int successful;

    @JsonProperty("skipped")
    private final int skipped;

    @JsonProperty("errors")
    private final int errors;

    @JsonProperty("failures")
    private final int failures;

    @JsonProperty("fullName")
    private final String fullName;

    @JsonProperty("commits")
    private final List<CommitDTO> commits;

    @JsonProperty("results")
    private final List<TestSuiteDTO> results;

    @JsonProperty("staticCodeAnalysisReports")
    private final List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports;

    // For an unknown reason, the deserialization only works with this annotation
    @JsonProperty("testwiseCoverageReport")
    private final List<TestwiseCoverageReportDTO> testwiseCoverageReport;

    @JsonProperty("runDate")
    private final ZonedDateTime runDate;

    @JsonProperty("isBuildSuccessful")
    private final boolean isBuildSuccessful;

    @JsonProperty("logs")
    private final List<String> logs;

    public TestResultsDTO(int successful, int skipped, int errors, int failures, String fullName, List<CommitDTO> commits, List<TestSuiteDTO> results,
            List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports, List<TestwiseCoverageReportDTO> testwiseCoverageReport, ZonedDateTime runDate, boolean isBuildSuccessful,
            List<String> logs) {
        this.successful = successful;
        this.skipped = skipped;
        this.errors = errors;
        this.failures = failures;
        this.fullName = fullName;
        this.commits = commits;
        this.results = results;
        this.staticCodeAnalysisReports = staticCodeAnalysisReports;
        this.testwiseCoverageReport = testwiseCoverageReport;
        this.runDate = runDate;
        this.isBuildSuccessful = isBuildSuccessful;
        this.logs = logs;
    }

    public static TestResultsDTO convert(Object someResult) {
        return new ObjectMapper().registerModule(new JavaTimeModule()).convertValue(someResult, TestResultsDTO.class);
    }

    public int getSuccessful() {
        return successful;
    }

    public int getSkipped() {
        return skipped;
    }

    public int getErrors() {
        return errors;
    }

    public int getFailures() {
        return failures;
    }

    public String getFullName() {
        return fullName;
    }

    public ZonedDateTime getRunDate() {
        return runDate;
    }

    public List<String> getLogs() {
        return this.logs;
    }

    @Override
    public ZonedDateTime getBuildRunDate() {
        return getRunDate();
    }

    @Override
    public Optional<String> getCommitHashFromAssignmentRepo() {
        final var testRepoNameSuffix = RepositoryType.TESTS.getName();
        final var firstCommit = getCommits().stream().filter(commit -> !commit.repositorySlug().endsWith(testRepoNameSuffix)).findFirst();
        return firstCommit.map(CommitDTO::hash);
    }

    @Override
    public Optional<String> getCommitHashFromTestsRepo() {
        final var testRepoNameSuffix = RepositoryType.TESTS.getName();
        final var firstCommit = getCommits().stream().filter(commit -> commit.repositorySlug().endsWith(testRepoNameSuffix)).findFirst();
        return firstCommit.map(CommitDTO::hash);
    }

    @Override
    public Optional<String> getBranchNameFromAssignmentRepo() {
        final var testRepoNameSuffix = RepositoryType.TESTS.getName();
        final var firstCommit = getCommits().stream().filter(commit -> !commit.repositorySlug().endsWith(testRepoNameSuffix)).findFirst();
        return firstCommit.map(CommitDTO::branchName);
    }

    private int getSum() {
        return getSkipped() + getFailures() + getErrors() + getSuccessful();
    }

    @Override
    public boolean isBuildSuccessful() {
        return isBuildSuccessful;
    }

    @Override
    public Double getBuildScore() {
        final var testSum = getSum();
        return testSum == 0 ? 0D : ((double) getSuccessful() / testSum) * 100D;
    }

    public List<CommitDTO> getCommits() {
        return commits;
    }

    public List<TestSuiteDTO> getResults() {
        return results;
    }

    public List<StaticCodeAnalysisReportDTO> getStaticCodeAnalysisReports() {
        return staticCodeAnalysisReports;
    }

    public List<TestwiseCoverageReportDTO> getTestwiseCoverageReport() {
        return testwiseCoverageReport;
    }

    @Override
    public boolean hasArtifact() {
        // TODO: this is not available in Jenkins yet
        return false;
    }

    @Override
    public boolean hasLogs() {
        return this.logs != null && !this.logs.isEmpty();
    }

    @Override
    public List<BuildLogEntry> extractBuildLogs(ProgrammingLanguage programmingLanguage) {
        var buildLogs = JenkinsBuildLogParseUtils.parseBuildLogsFromJenkinsLogs(getLogs());
        return filterBuildLogs(buildLogs);
    }

    /**
     * Removes the build logs that are not relevant to the student.
     *
     * @param buildLogEntries unfiltered build logs
     * @return filtered build logs
     */
    public static List<BuildLogEntry> filterBuildLogs(List<BuildLogEntry> buildLogEntries) {
        // There are color codes in the logs that need to be filtered out.
        // This is needed for old programming exercises
        // For example:[[1;34mINFO[m] is changed to [INFO]
        Stream<BuildLogEntry> filteredBuildLogs = buildLogEntries.stream().peek(buildLog -> {
            String log = buildLog.getLog();
            log = log.replace("\u001B[1;34m", "");
            log = log.replace("\u001B[m", "");
            log = log.replace("\u001B[1;31m", "");
            buildLog.setLog(log);
        });

        // Jenkins outputs each executed shell command with '+ <shell command>'
        filteredBuildLogs = filteredBuildLogs.filter(buildLog -> !buildLog.getLog().startsWith("+"));
        return filteredBuildLogs.toList();
    }
}
