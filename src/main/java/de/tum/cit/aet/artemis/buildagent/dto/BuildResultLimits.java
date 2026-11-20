package de.tum.cit.aet.artemis.buildagent.dto;

import java.util.ArrayList;
import java.util.List;

import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisIssue;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;

/**
 * Upper bounds for the size of the result of a build that a core node processes. The result is created on a build agent and read from the queue, so the number of its elements is
 * bounded before it is processed. The values are far above those of any realistic exercise, which has some hundreds of tests, a few reports and at most
 * {@code artemis.continuous-integration.build-logs.max-lines-per-job} (10000 by default) log lines. A result above a bound is shortened, it is not refused.
 * <p>
 * The records that carry the result are serialized in the queue, so the bounds are applied by this class and not in their constructors.
 */
public final class BuildResultLimits {

    /** The most test results (failed and successful together) that are processed. */
    public static final int MAX_TESTS = 50_000;

    /** The most static code analysis reports that are processed. There is one report per tool, so this is far more than the tools that exist. */
    public static final int MAX_STATIC_CODE_ANALYSIS_REPORTS = 100;

    /** The most static code analysis issues, over all reports, that are processed. */
    public static final int MAX_STATIC_CODE_ANALYSIS_ISSUES = 100_000;

    /** The most build log entries that are processed. */
    public static final int MAX_BUILD_LOG_ENTRIES = 100_000;

    private BuildResultLimits() {
        // utility class
    }

    /**
     * Shortens the lists of a build result that are above the bounds.
     *
     * @param buildResult the result as it was read from the queue
     * @return the same result if it is within the bounds, otherwise a copy with shortened lists
     */
    public static BuildResult limit(BuildResult buildResult) {
        List<LocalCIJobDTO> originalJobs = buildResult.jobs();
        List<StaticCodeAnalysisReportDTO> originalReports = buildResult.staticCodeAnalysisReports();
        List<BuildLogDTO> originalLogEntries = buildResult.buildLogEntries();
        List<LocalCIJobDTO> jobs = limitJobs(originalJobs);
        List<StaticCodeAnalysisReportDTO> reports = limitReports(originalReports);
        List<BuildLogDTO> logEntries = limitLogs(originalLogEntries);
        if (jobs == originalJobs && reports == originalReports && logEntries == originalLogEntries) {
            return buildResult;
        }
        return new BuildResult(buildResult.assignmentRepoBranchName(), buildResult.assignmentRepoCommitHash(), buildResult.testsRepoCommitHash(), buildResult.isBuildSuccessful(),
                buildResult.buildRunDate(), jobs, logEntries, reports, buildResult.hasLogs(), buildResult.buildScriptExitCode());
    }

    /**
     * Shortens a list of log entries that is above the bound.
     *
     * @param logEntries the log entries
     * @return the same list if it is within the bound, otherwise a copy with the first entries
     */
    public static List<BuildLogDTO> limitLogs(List<BuildLogDTO> logEntries) {
        if (logEntries == null || logEntries.size() <= MAX_BUILD_LOG_ENTRIES) {
            return logEntries;
        }
        return new ArrayList<>(logEntries.subList(0, MAX_BUILD_LOG_ENTRIES));
    }

    private static List<LocalCIJobDTO> limitJobs(List<LocalCIJobDTO> jobs) {
        int total = 0;
        for (LocalCIJobDTO job : jobs) {
            total += job.failedTests().size() + job.successfulTests().size() + job.skippedTests().size();
        }
        if (total <= MAX_TESTS) {
            return jobs;
        }
        // Graded results are kept first - failed, then successful - because dropping a successful result would make it
        // count as not passed and lower the score. Skipped tests carry a reason but no points, so they only use the
        // capacity that is left after the graded results.
        int remaining = MAX_TESTS;
        List<List<LocalCITestJobDTO>> keptFailed = new ArrayList<>();
        for (LocalCIJobDTO job : jobs) {
            List<LocalCITestJobDTO> failed = job.failedTests().subList(0, Math.min(remaining, job.failedTests().size()));
            remaining -= failed.size();
            keptFailed.add(failed);
        }
        List<List<LocalCITestJobDTO>> keptSuccessful = new ArrayList<>();
        for (LocalCIJobDTO job : jobs) {
            List<LocalCITestJobDTO> successful = job.successfulTests().subList(0, Math.min(remaining, job.successfulTests().size()));
            remaining -= successful.size();
            keptSuccessful.add(successful);
        }
        List<LocalCIJobDTO> limited = new ArrayList<>();
        for (int i = 0; i < jobs.size(); i++) {
            List<LocalCITestJobDTO> skipped = jobs.get(i).skippedTests().subList(0, Math.min(remaining, jobs.get(i).skippedTests().size()));
            remaining -= skipped.size();
            limited.add(new LocalCIJobDTO(new ArrayList<>(keptFailed.get(i)), new ArrayList<>(keptSuccessful.get(i)), new ArrayList<>(skipped)));
        }
        return limited;
    }

    private static List<StaticCodeAnalysisReportDTO> limitReports(List<StaticCodeAnalysisReportDTO> reports) {
        int issues = 0;
        for (StaticCodeAnalysisReportDTO report : reports) {
            issues += report.issues() == null ? 0 : report.issues().size();
        }
        if (reports.size() <= MAX_STATIC_CODE_ANALYSIS_REPORTS && issues <= MAX_STATIC_CODE_ANALYSIS_ISSUES) {
            return reports;
        }
        int remaining = MAX_STATIC_CODE_ANALYSIS_ISSUES;
        List<StaticCodeAnalysisReportDTO> limited = new ArrayList<>();
        for (StaticCodeAnalysisReportDTO report : reports.subList(0, Math.min(MAX_STATIC_CODE_ANALYSIS_REPORTS, reports.size()))) {
            List<StaticCodeAnalysisIssue> reportIssues = report.issues() == null ? new ArrayList<>() : report.issues();
            List<StaticCodeAnalysisIssue> kept = new ArrayList<>(reportIssues.subList(0, Math.min(remaining, reportIssues.size())));
            remaining -= kept.size();
            limited.add(new StaticCodeAnalysisReportDTO(report.tool(), kept));
        }
        return limited;
    }
}
