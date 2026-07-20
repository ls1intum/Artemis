package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.List;

/**
 * One assignment's ({@code solution} or {@code template}) single pristine build, projected for callers outside this package that need only "did it build, and which tests failed"
 * — currently {@link StageCheckService}'s per-stage compile gates — without the full solution/template differential {@link DifferentialVerificationService#verify} and
 * {@link DifferentialVerificationService#selfCheck} compute.
 * <p>
 * Note the definition of "compiled" this projection is built around: {@code verify.sh} exits non-zero both for a genuine compile failure AND for failing tests, so a caller must
 * check {@code testsRun > 0 || exitCode == 0} rather than {@code exitCode == 0} alone — otherwise a template that correctly fails its behavioural tests (the whole point of a
 * template) is misreported as "does not compile".
 *
 * @param exitCode        the build process's exit code
 * @param testsRun        tests that ran (parser form, {@code <skipped>} excluded); zero when the build never reached the test runner
 * @param failures        failing/erroring tests among {@code testsRun}
 * @param failedTestNames parser-form names of the failing tests (empty when none failed)
 * @param boundedLog      the build's combined stdout/stderr, credential-redacted and bounded (see {@link DifferentialVerificationService#boundedReadinessDiagnostic})
 */
public record SingleBuildResult(int exitCode, int testsRun, int failures, List<String> failedTestNames, String boundedLog) {

    /** Whether the build compiled: it exited cleanly, or it ran at least one test (a non-zero exit past that point means failing tests, not a compile error). */
    public boolean compiled() {
        return testsRun > 0 || exitCode == 0;
    }
}
