package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;
import de.tum.cit.aet.artemis.buildagent.service.parser.TestResultXmlParser;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService.VerificationInfrastructureException;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.CollectedReports;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.SandboxBuildCommandService;
import de.tum.cit.aet.artemis.localci.service.scaparser.ReportParser;
import de.tum.cit.aet.artemis.localci.service.scaparser.exception.UnsupportedToolException;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisIssue;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;

/**
 * The aggregated test outcome of one {@code verify.sh} run, built by parsing the collected report files with the same production parsers LocalCI uses
 * ({@link TestResultXmlParser} for JUnit, {@link ReportParser} for SCA), so the oracle's view is parity-by-construction with grading.
 *
 * @param tests           tests that ran (zero when the build did not reach the runner, e.g. a compile error); excludes {@code <skipped>} cases, as production grades
 * @param testNames       distinct test-case names from the JUnit XML, composed as production does; empty if none collected
 * @param testFailedNames distinct names of cases that failed/errored; used by the strict per-test gate; empty if none collected
 * @param failureEvidence bounded, sanitized names and first useful failure messages for agent feedback
 * @param scaFindings     SCA findings (tool + real derived category from {@link ReportParser}); populated only when the SCA reports were collected; empty otherwise
 */
record BuildSummary(int tests, int failures, int exitCode, boolean timedOut, List<String> testNames, List<String> testFailedNames,
        List<AgentVerifyReport.TestFailureEvidence> failureEvidence, List<ScaPenaltyParity.ScaFinding> scaFindings) {

    private static final Logger log = LoggerFactory.getLogger(BuildSummary.class);

    static BuildSummary fromReports(Map<String, byte[]> reports, int exitCode) {
        List<LocalCITestJobDTO> failed = new ArrayList<>();
        List<LocalCITestJobDTO> successful = new ArrayList<>();
        List<ScaPenaltyParity.ScaFinding> scaFindings = new ArrayList<>();
        for (Map.Entry<String, byte[]> report : reports.entrySet()) {
            String canonical = canonicalToken(report.getKey());
            String content = CollectedReports.asString(report.getValue());
            if (SandboxBuildCommandService.COLLECTED_JUNIT_TOKEN.equals(canonical)) {
                try {
                    TestResultXmlParser.processTestResultFile(content, failed, successful);
                }
                catch (IOException | RuntimeException e) {
                    throw VerificationInfrastructureException.reportRejected("The verifier could not parse JUnit report " + report.getKey(), e);
                }
            }
            else {
                parseScaReport(content, canonical, scaFindings);
            }
        }
        List<String> testNames = new ArrayList<>();
        List<String> failedNames = new ArrayList<>();
        List<AgentVerifyReport.TestFailureEvidence> failureEvidence = new ArrayList<>();
        failed.forEach(job -> {
            testNames.add(job.name());
            failedNames.add(job.name());
            failureEvidence.add(AgentVerifyReport.TestFailureEvidence.from(job.name(), job.testMessages()));
        });
        successful.forEach(job -> testNames.add(job.name()));
        int tests = failed.size() + successful.size();
        return new BuildSummary(tests, failed.size(), exitCode, false, List.copyOf(testNames), List.copyOf(failedNames), List.copyOf(failureEvidence), List.copyOf(scaFindings));
    }

    private static void parseScaReport(String content, String canonicalFileName, List<ScaPenaltyParity.ScaFinding> scaFindings) {
        try {
            StaticCodeAnalysisReportDTO report = ReportParser.getReport(content, canonicalFileName);
            if (report == null || report.issues() == null || report.tool() == null) {
                return;
            }
            String tool = report.tool().name();
            for (StaticCodeAnalysisIssue issue : report.issues()) {
                scaFindings.add(new ScaPenaltyParity.ScaFinding(tool, issue.category()));
            }
        }
        catch (UnsupportedToolException e) {
            log.debug("No SCA parser for collected report {}: {}", canonicalFileName, e.getMessage());
        }
        catch (RuntimeException e) {
            throw VerificationInfrastructureException.reportRejected("The verifier could not parse SCA report " + canonicalFileName, e);
        }
    }

    /** The canonical routing token a collected file name carries (the segment after the {@code <seq>__} prefix): the JUnit token or an SCA tool's canonical report name. */
    private static String canonicalToken(String collectedName) {
        int sep = collectedName.indexOf(SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR);
        return sep < 0 ? collectedName : collectedName.substring(sep + SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR.length());
    }
}
