package de.tum.cit.aet.artemis.hyperionworker.generation.verification;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.hyperion.runtime.verification.JUnitReportParser;
import de.tum.cit.aet.artemis.hyperionworker.generation.verification.DifferentialVerificationService.VerificationInfrastructureException;
import de.tum.cit.aet.artemis.hyperionworker.generation.workspace.CollectedReports;
import de.tum.cit.aet.artemis.hyperionworker.generation.workspace.SandboxBuildCommandService;

/**
 * Aggregates one verifier build with the production JUnit parser shared by ordinary LocalCI grading.
 *
 * @param tests           tests that ran (zero when the build never reached the runner); excludes {@code <skipped>} cases, exactly as production grades
 * @param testNames       distinct test-case names from the JUnit XML, composed as production does; empty if none collected
 * @param testFailedNames distinct names of cases that failed or errored; empty if none collected
 * @param failureEvidence bounded, sanitized names and first useful failure messages for agent feedback
 * @param buildDiagnostic bounded, credential-redacted process output, used only when the build ran no tests
 */
record BuildSummary(int tests, int failures, int exitCode, boolean timedOut, List<String> testNames, List<String> testFailedNames,
        List<AgentVerifyReport.TestFailureEvidence> failureEvidence, String buildDiagnostic) {

    private static final JUnitReportParser PARSER = new JUnitReportParser(20_000);

    static BuildSummary fromReports(Map<String, byte[]> reports, int exitCode) {
        return fromReports(reports, exitCode, "");
    }

    static BuildSummary fromReports(Map<String, byte[]> reports, int exitCode, String buildDiagnostic) {
        List<JUnitReportParser.TestCaseResult> failed = new ArrayList<>();
        List<JUnitReportParser.TestCaseResult> successful = new ArrayList<>();
        for (Map.Entry<String, byte[]> report : reports.entrySet()) {
            String canonical = canonicalToken(report.getKey());
            String content = CollectedReports.asString(report.getValue());
            if (SandboxBuildCommandService.COLLECTED_JUNIT_TOKEN.equals(canonical)) {
                try {
                    PARSER.processTestResultFile(content, failed, successful);
                }
                catch (IOException | RuntimeException e) {
                    throw VerificationInfrastructureException.reportRejected("The verifier could not parse JUnit report " + report.getKey(), e);
                }
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
        return new BuildSummary(tests, failed.size(), exitCode, false, List.copyOf(testNames), List.copyOf(failedNames), List.copyOf(failureEvidence), buildDiagnostic);
    }

    private static String canonicalToken(String collectedName) {
        int sep = collectedName.indexOf(SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR);
        return sep < 0 ? collectedName : collectedName.substring(sep + SandboxBuildCommandService.COLLECTED_NAME_SEPARATOR.length());
    }
}
