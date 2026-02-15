package de.tum.cit.aet.artemis.programming.service.hades.dto;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.dto.BuildJobInterface;
import de.tum.cit.aet.artemis.programming.dto.BuildResultNotification;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;
import de.tum.cit.aet.artemis.programming.service.ci.notification.dto.TestSuiteDTO;

public record HadesTestResultsDTO(@JsonProperty("jobName") String jobName, @JsonProperty("uuid") UUID UUID,
        @JsonProperty("assignmentRepoBranchName") String assignmentRepoBranchName, @JsonProperty("assignmentRepoCommitHash") String assignmentRepoCommitHash,
        @JsonProperty("testsRepoCommitHash") String testsRepoCommitHash, @JsonProperty("results") @JsonSetter(nulls = Nulls.AS_EMPTY) List<TestSuiteDTO> results,
        @JsonProperty("buildCompletionTime") ZonedDateTime buildCompletionTime, @JsonProperty("isBuildSuccessful") boolean isBuildSuccessful,
        @JsonProperty("logs") @JsonSetter(nulls = Nulls.AS_EMPTY) List<HadesLogEntryDTO> logs) implements BuildResultNotification {

    public static HadesTestResultsDTO convert(Object responseBody) {
        return new ObjectMapper().registerModule(new JavaTimeModule()).convertValue(responseBody, HadesTestResultsDTO.class);
    }

    @Override
    public ZonedDateTime buildRunDate() {
        return buildCompletionTime();
    }

    @Override
    public @Nullable String assignmentRepoCommitHash() {
        return assignmentRepoBranchName;
    }

    @Override
    public @Nullable String testsRepoCommitHash() {
        return testsRepoCommitHash;
    }

    @Override
    public @Nullable String assignmentRepoBranchName() {
        return assignmentRepoBranchName;
    }

    @Override
    public boolean isBuildSuccessful() {
        return isBuildSuccessful;
    }

    public int getSum() {
        var sum = 0;
        for (TestSuiteDTO testSuiteDTO : results) {
            sum += testSuiteDTO.failedTests().size() + testSuiteDTO.successfulTests().size();
        }
        return sum;
    }

    public int successful() {
        var sum = 0;
        for (TestSuiteDTO testSuiteDTO : results) {
            sum += testSuiteDTO.successfulTests().size();
        }
        return sum;
    }

    @Override
    public Double buildScore() {
        final var testSum = getSum();
        return testSum == 0 ? 0D : ((double) successful() / testSum) * 100D;
    }

    @Override
    public boolean hasArtifact() {
        // NOTE: this is not available in Hades
        return false;
    }

    @Override
    public boolean hasLogs() {
        return this.logs != null && !this.logs.isEmpty();
    }

    @Override
    public List<BuildLogEntry> extractBuildLogs() {
        return parseBuildLogsFromLogs(logs());
    }

    public static List<BuildLogEntry> parseBuildLogsFromLogs(List<HadesLogEntryDTO> logEntries) {
        final List<BuildLogEntry> buildLogs = new ArrayList<>();

        for (final var logEntry : logEntries) {
            if (logEntry.timestamp() != null && logEntry.message() != null) {
                BuildLogEntry buildLogEntry = new BuildLogEntry(logEntry.timestamp(), logEntry.message().trim());
                buildLogs.add(buildLogEntry);
            }
        }

        return buildLogs;
    }

    @Override
    public List<? extends BuildJobInterface> jobs() {
        return List.of();
    }

    @Override
    public List<StaticCodeAnalysisReportDTO> staticCodeAnalysisReports() {
        return List.of();
    }
}
