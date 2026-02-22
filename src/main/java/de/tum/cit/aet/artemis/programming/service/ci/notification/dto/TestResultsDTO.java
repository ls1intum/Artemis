package de.tum.cit.aet.artemis.programming.service.ci.notification.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.dto.BuildJobInterface;
import de.tum.cit.aet.artemis.programming.dto.BuildResultNotification;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;
import de.tum.cit.aet.artemis.programming.service.ci.notification.BuildLogParseUtils;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
// Note: due to limitations with inheritance, we cannot declare this as record, but we can use it in a similar way with final fields
public record TestResultsDTO(@JsonProperty("successful") int successful, @JsonProperty("skipped") int skipped, @JsonProperty("errors") int errors,
        @JsonProperty("failures") int failures, @JsonProperty("fullName") String fullName, @JsonProperty("commits") @JsonSetter(nulls = Nulls.AS_EMPTY) List<CommitDTO> commits,
        @JsonProperty("results") @JsonSetter(nulls = Nulls.AS_EMPTY) List<TestSuiteDTO> results,
        @JsonProperty("staticCodeAnalysisReports") @JsonSetter(nulls = Nulls.AS_EMPTY) List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports,
        @JsonProperty("runDate") ZonedDateTime runDate, @JsonProperty("isCompilationSuccessful") boolean isCompilationSuccessful,
        @JsonProperty("logs") @JsonSetter(nulls = Nulls.AS_EMPTY) List<String> logs) implements BuildResultNotification {

    public static TestResultsDTO convert(Object someResult) {
        return new ObjectMapper().registerModule(new JavaTimeModule()).convertValue(someResult, TestResultsDTO.class);
    }

    @Override
    public ZonedDateTime buildRunDate() {
        return runDate();
    }

    @Override
    public String assignmentRepoCommitHash() {
        final var testRepoNameSuffix = RepositoryType.TESTS.getName();
        final var firstCommit = commits().stream().filter(commit -> !commit.repositorySlug().endsWith(testRepoNameSuffix)).findFirst();
        return firstCommit.map(CommitDTO::hash).orElse(null);
    }

    @Override
    public String testsRepoCommitHash() {
        final var testRepoNameSuffix = RepositoryType.TESTS.getName();
        final var firstCommit = commits().stream().filter(commit -> commit.repositorySlug().endsWith(testRepoNameSuffix)).findFirst();
        return firstCommit.map(CommitDTO::hash).orElse(null);
    }

    @Override
    public String assignmentRepoBranchName() {
        final var testRepoNameSuffix = RepositoryType.TESTS.getName();
        final var firstCommit = commits().stream().filter(commit -> !commit.repositorySlug().endsWith(testRepoNameSuffix)).findFirst();
        return firstCommit.map(CommitDTO::branchName).orElse(null);
    }

    private int getSum() {
        return skipped() + failures() + errors() + successful();
    }

    @Override
    public Double buildScore() {
        final var testSum = getSum();
        return testSum == 0 ? 0D : ((double) successful() / testSum) * 100D;
    }

    @Override
    public boolean hasArtifact() {
        // NOTE: this is not available in Jenkins
        return false;
    }

    @Override
    public boolean hasLogs() {
        return this.logs != null && !this.logs.isEmpty();
    }

    @Override
    public List<BuildLogEntry> extractBuildLogs() {
        var buildLogs = BuildLogParseUtils.parseBuildLogsFromLogs(logs());
        return filterBuildLogs(buildLogs);
    }

    @Override
    public List<? extends BuildJobInterface> jobs() {
        return results();
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
