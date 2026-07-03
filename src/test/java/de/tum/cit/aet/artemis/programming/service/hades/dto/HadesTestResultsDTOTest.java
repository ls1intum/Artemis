package de.tum.cit.aet.artemis.programming.service.hades.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.localci.service.ci.notification.dto.TestSuiteDTO;

class HadesTestResultsDTOTest {

    private static final ZonedDateTime BUILD_TIME = ZonedDateTime.parse("2024-06-01T10:00:00Z");

    private HadesTestResultsDTO createResult(boolean successful, int passed, List<TestSuiteDTO> results, List<HadesLogEntryDTO> logs) {
        return new HadesTestResultsDTO("job1", UUID.randomUUID(), "main", "abc123", "def456", results, BUILD_TIME, successful, logs, passed);
    }

    @Test
    void buildRunDate_returnsBuildCompletionTime() {
        var dto = createResult(true, 0, List.of(), List.of());
        assertThat(dto.buildRunDate()).isEqualTo(BUILD_TIME);
    }

    @Test
    void assignmentRepoCommitHash_returnsValue() {
        var dto = createResult(true, 0, List.of(), List.of());
        assertThat(dto.assignmentRepoCommitHash()).isEqualTo("abc123");
    }

    @Test
    void testsRepoCommitHash_returnsValue() {
        var dto = createResult(true, 0, List.of(), List.of());
        assertThat(dto.testsRepoCommitHash()).isEqualTo("def456");
    }

    @Test
    void assignmentRepoBranchName_returnsValue() {
        var dto = createResult(true, 0, List.of(), List.of());
        assertThat(dto.assignmentRepoBranchName()).isEqualTo("main");
    }

    @Test
    void isBuildSuccessful_true() {
        assertThat(createResult(true, 0, List.of(), List.of()).isBuildSuccessful()).isTrue();
    }

    @Test
    void isBuildSuccessful_false() {
        assertThat(createResult(false, 0, List.of(), List.of()).isBuildSuccessful()).isFalse();
    }

    @Test
    void getSum_sumsTestsAcrossAllSuites() {
        var suite1 = new TestSuiteDTO("suite1", 1.0, 0, 0, 0, 5, List.of());
        var suite2 = new TestSuiteDTO("suite2", 1.0, 0, 0, 0, 3, List.of());
        var dto = createResult(true, 4, List.of(suite1, suite2), List.of());

        assertThat(dto.getSum()).isEqualTo(8);
    }

    @Test
    void buildScore_withPassedAndTotal_calculatesPercentage() {
        var suite = new TestSuiteDTO("suite", 1.0, 0, 0, 0, 4, List.of());
        var dto = createResult(true, 2, List.of(suite), List.of());

        assertThat(dto.buildScore()).isEqualTo(50.0);
    }

    @Test
    void buildScore_withZeroTests_returnsZero() {
        var dto = createResult(false, 0, List.of(), List.of());
        assertThat(dto.buildScore()).isEqualTo(0.0);
    }

    @Test
    void buildScore_allPassed_returnsHundred() {
        var suite = new TestSuiteDTO("suite", 1.0, 0, 0, 0, 5, List.of());
        var dto = createResult(true, 5, List.of(suite), List.of());

        assertThat(dto.buildScore()).isEqualTo(100.0);
    }

    @Test
    void hasArtifact_alwaysFalse() {
        assertThat(createResult(true, 0, List.of(), List.of()).hasArtifact()).isFalse();
    }

    @Test
    void hasLogs_withNonEmptyLogs_returnsTrue() {
        var log = new HadesLogEntryDTO(BUILD_TIME, "Build started", "stdout");
        assertThat(createResult(true, 0, List.of(), List.of(log)).hasLogs()).isTrue();
    }

    @Test
    void hasLogs_withEmptyLogs_returnsFalse() {
        assertThat(createResult(true, 0, List.of(), List.of()).hasLogs()).isFalse();
    }

    @Test
    void hasLogs_withNullLogs_returnsFalse() {
        var dto = createResult(true, 0, List.of(), null);
        assertThat(dto.hasLogs()).isFalse();
    }

    @Test
    void extractBuildLogs_returnsEntriesWithTimestampAndMessage() {
        var log1 = new HadesLogEntryDTO(BUILD_TIME, "  hello  ", "stdout");
        var log2 = new HadesLogEntryDTO(null, "no timestamp", "stderr");
        var dto = createResult(true, 0, List.of(), List.of(log1, log2));

        var buildLogs = dto.extractBuildLogs();

        assertThat(buildLogs).hasSize(1);
        assertThat(buildLogs.getFirst().getLog()).isEqualTo("hello");
    }

    @Test
    void parseBuildLogsFromLogs_skipsNullTimestamp() {
        var entry = new HadesLogEntryDTO(null, "some message", "stdout");
        assertThat(HadesTestResultsDTO.parseBuildLogsFromLogs(List.of(entry))).isEmpty();
    }

    @Test
    void parseBuildLogsFromLogs_skipsNullMessage() {
        var entry = new HadesLogEntryDTO(BUILD_TIME, null, "stdout");
        assertThat(HadesTestResultsDTO.parseBuildLogsFromLogs(List.of(entry))).isEmpty();
    }

    @Test
    void parseBuildLogsFromLogs_trimsMessageWhitespace() {
        var entry = new HadesLogEntryDTO(BUILD_TIME, "  trimmed  ", "stdout");
        var logs = HadesTestResultsDTO.parseBuildLogsFromLogs(List.of(entry));

        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getLog()).isEqualTo("trimmed");
    }

    @Test
    void jobs_returnsResults() {
        var suite = new TestSuiteDTO("suite", 1.0, 0, 0, 0, 3, List.of());
        var dto = createResult(true, 3, List.of(suite), List.of());

        assertThat(dto.jobs()).isEqualTo(dto.results());
    }

    @Test
    void staticCodeAnalysisReports_returnsEmptyList() {
        assertThat(createResult(true, 0, List.of(), List.of()).staticCodeAnalysisReports()).isEmpty();
    }

    @Test
    void convert_fromMapWithValidData_returnsDTO() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("isBuildSuccessful", true);
        map.put("passed", 3);

        var dto = HadesTestResultsDTO.convert(map);

        assertThat(dto).isNotNull();
        assertThat(dto.isBuildSuccessful()).isTrue();
        assertThat(dto.passed()).isEqualTo(3);
    }
}
