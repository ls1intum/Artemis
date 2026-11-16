package de.tum.cit.aet.artemis.buildagent.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisIssue;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;

/**
 * Unit tests for the bounds on the size of a build result that is processed on a core node.
 */
class BuildResultLimitsTest {

    private static final LocalCITestJobDTO TEST = new LocalCITestJobDTO("test", List.of());

    private static final BuildLogDTO LOG_ENTRY = new BuildLogDTO(ZonedDateTime.now(), "line");

    private static BuildResult result(List<LocalCIJobDTO> jobs, List<BuildLogDTO> logs, List<StaticCodeAnalysisReportDTO> reports) {
        return new BuildResult("main", "a", "b", true, ZonedDateTime.now(), jobs, logs, reports, false, 0);
    }

    @Test
    void aResultWithinTheBoundsIsReturnedUnchanged() {
        BuildResult buildResult = result(List.of(new LocalCIJobDTO(List.of(TEST), List.of(TEST))), List.of(LOG_ENTRY), List.of());

        assertThat(BuildResultLimits.limit(buildResult)).isSameAs(buildResult);
    }

    @Test
    void tooManyTestsAreShortenedAndFailedTestsAreKeptFirst() {
        List<LocalCITestJobDTO> failed = Collections.nCopies(10, TEST);
        List<LocalCITestJobDTO> successful = Collections.nCopies(BuildResultLimits.MAX_TESTS, TEST);
        BuildResult buildResult = result(List.of(new LocalCIJobDTO(failed, successful)), List.of(), List.of());

        BuildResult limited = BuildResultLimits.limit(buildResult);

        LocalCIJobDTO job = limited.jobs().getFirst();
        assertThat(job.failedTests()).hasSize(10);
        assertThat(job.failedTests().size() + job.successfulTests().size()).isEqualTo(BuildResultLimits.MAX_TESTS);
    }

    @Test
    void failedTestsOfALaterJobAreKeptBeforeSuccessfulTestsOfAnEarlierJob() {
        LocalCIJobDTO first = new LocalCIJobDTO(List.of(), Collections.nCopies(BuildResultLimits.MAX_TESTS, TEST));
        LocalCIJobDTO second = new LocalCIJobDTO(Collections.nCopies(5, TEST), Collections.nCopies(10, TEST));

        BuildResult limited = BuildResultLimits.limit(result(List.of(first, second), List.of(), List.of()));

        assertThat(limited.jobs().get(1).failedTests()).as("no failed test is dropped").hasSize(5);
        assertThat(limited.jobs().stream().mapToInt(job -> job.failedTests().size() + job.successfulTests().size()).sum()).isEqualTo(BuildResultLimits.MAX_TESTS);
        assertThat(limited.jobs().get(0).successfulTests()).hasSize(BuildResultLimits.MAX_TESTS - 5);
    }

    @Test
    void tooManyReportsAreShortened() {
        List<StaticCodeAnalysisReportDTO> reports = Collections.nCopies(BuildResultLimits.MAX_STATIC_CODE_ANALYSIS_REPORTS + 1,
                new StaticCodeAnalysisReportDTO(StaticCodeAnalysisTool.CHECKSTYLE, List.of()));

        BuildResult limited = BuildResultLimits.limit(result(List.of(), List.of(), reports));

        assertThat(limited.staticCodeAnalysisReports()).hasSize(BuildResultLimits.MAX_STATIC_CODE_ANALYSIS_REPORTS);
    }

    @Test
    void failuresAcrossJobsStillRespectTheTotalTestLimit() {
        BuildResult limited = BuildResultLimits
                .limit(result(List.of(new LocalCIJobDTO(Collections.nCopies(BuildResultLimits.MAX_TESTS, TEST), List.of(TEST)), new LocalCIJobDTO(List.of(TEST), List.of(TEST))),
                        List.of(), List.of()));

        assertThat(limited.jobs().getFirst().failedTests()).hasSize(BuildResultLimits.MAX_TESTS);
        assertThat(limited.jobs().getFirst().successfulTests()).isEmpty();
        assertThat(limited.jobs().getLast().failedTests()).isEmpty();
        assertThat(limited.jobs().getLast().successfulTests()).isEmpty();
    }

    @Test
    void tooManyIssuesAreShortenedOverAllReports() {
        StaticCodeAnalysisIssue issue = new StaticCodeAnalysisIssue("File.java", 1, 1, 1, 1, "rule", "category", "message", "priority", 1.0);
        List<StaticCodeAnalysisIssue> issues = Collections.nCopies(BuildResultLimits.MAX_STATIC_CODE_ANALYSIS_ISSUES, issue);
        List<StaticCodeAnalysisReportDTO> reports = List.of(new StaticCodeAnalysisReportDTO(StaticCodeAnalysisTool.CHECKSTYLE, issues),
                new StaticCodeAnalysisReportDTO(StaticCodeAnalysisTool.SPOTBUGS, issues));

        BuildResult limited = BuildResultLimits.limit(result(List.of(), List.of(), reports));

        assertThat(limited.staticCodeAnalysisReports().stream().mapToInt(report -> report.issues().size()).sum()).isEqualTo(BuildResultLimits.MAX_STATIC_CODE_ANALYSIS_ISSUES);
    }

    @Test
    void tooManyLogEntriesAreShortened() {
        List<BuildLogDTO> logs = Collections.nCopies(BuildResultLimits.MAX_BUILD_LOG_ENTRIES + 1, LOG_ENTRY);

        BuildResult limited = BuildResultLimits.limit(result(List.of(), logs, List.of()));

        assertThat(limited.buildLogEntries()).hasSize(BuildResultLimits.MAX_BUILD_LOG_ENTRIES);
        assertThat(BuildResultLimits.limitLogs(logs)).hasSize(BuildResultLimits.MAX_BUILD_LOG_ENTRIES);
        assertThat(BuildResultLimits.limitLogs(null)).isNull();
    }
}
