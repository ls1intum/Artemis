package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Runs an independently-authored shadow test suite against the solution through the same production build+parse path the differential oracle uses. Exercise-agnostic: it flags any
 * solution-side failure that is not a build/compile gate and never decides acceptance itself. On doubt it fails open ({@link CrossCheckVerdict.Status#INCONCLUSIVE}), so it can
 * only
 * add an advisory (or, behind a config flag, a hard block on top of an already-accepted exercise). See {@link CrossCheckVerdict} for why the decorrelation matters.
 */
@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class CrossCheckService {

    private static final Logger log = LoggerFactory.getLogger(CrossCheckService.class);

    private final SandboxBuildCommandService sandboxBuildCommandService;

    private final DifferentialVerificationService verifier;

    public CrossCheckService(SandboxBuildCommandService sandboxBuildCommandService, DifferentialVerificationService verifier) {
        this.sandboxBuildCommandService = sandboxBuildCommandService;
        this.verifier = verifier;
    }

    /**
     * Runs the given shadow suite against the real solution and decides whether the solution contradicts its own stated contract.
     * <p>
     * The shadow suite is seeded into a separate workspace directory ({@code shadow-tests/}) — it never overwrites the agent's own {@code tests/} — and the build re-wipes and
     * re-collects its verifier-owned reports dir, so running this after the main differential in the same session is collision-free.
     *
     * @param sandbox         the open sandbox session (the same one the differential ran in)
     * @param sessionId       the sandbox session id
     * @param exercise        the exercise being cross-checked (drives the per-language {@code verify.sh})
     * @param shadowTestFiles the independently-authored suite, repository-relative path to content (the harness manifests plus the examiner's test sources); empty skips the check
     * @return the cross-check report (never {@code null})
     */
    public CrossCheckVerdict runAgainstShadowSuite(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> shadowTestFiles) {
        if (shadowTestFiles == null || shadowTestFiles.isEmpty()) {
            return CrossCheckVerdict.skipped("No independently-authored shadow suite was produced.");
        }
        try {
            seedShadowSuite(sandbox, sessionId, shadowTestFiles);
            DifferentialVerificationService.BuildSummary solution = verifier.runReportedBuild(sandbox, sessionId, exercise,
                    sandboxBuildCommandService.crosscheckSolutionBuildCommand(), GenerationWorkspaceService.directoryFor(RepositoryType.SOLUTION));
            return decide(solution);
        }
        catch (RuntimeException e) {
            // Fail open: a build/copyOut error tells us nothing about the solution.
            log.warn("Cross-check could not run; treating as inconclusive: {}", e.getMessage());
            return CrossCheckVerdict.inconclusive("The cross-check build could not run: " + e.getMessage());
        }
    }

    /** Seeds the shadow suite under {@code /workspace/shadow-tests/} in one tar, never touching the agent's {@code tests/}. */
    private void seedShadowSuite(InteractiveSandbox sandbox, String sessionId, Map<String, String> shadowTestFiles) {
        Map<String, String> prefixed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : shadowTestFiles.entrySet()) {
            prefixed.put(SandboxBuildCommandService.CROSSCHECK_TESTS_DIR + "/" + entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        sandbox.copyIn(sessionId, GenerationWorkspaceService.WORKSPACE, WorkspaceArchive.buildWorkspaceTarStream(prefixed, Map.of()));
    }

    /**
     * Decides the verdict from the parsed solution build summary. Fail-open on any doubt (never a reject); a non-build-gate solution failure is a contradiction; otherwise
     * consistent.
     */
    private static CrossCheckVerdict decide(DifferentialVerificationService.BuildSummary solution) {
        // tests() == 0 also covers a timed-out build (BuildSummary.timedOut reports zero tests).
        if (solution.tests() == 0) {
            // Fail open: a shadow suite that did not run tells us nothing about the solution.
            return CrossCheckVerdict.inconclusive("The shadow suite did not run against the reference solution (it did not compile, ran no tests, or timed out).");
        }
        List<String> contradicted = gradableFailures(solution);
        if (!contradicted.isEmpty()) {
            String detail = "The reference solution fails " + contradicted.size() + " independently-authored contract test(s) of " + solution.tests() + ".";
            return new CrossCheckVerdict(CrossCheckVerdict.Status.CONTRADICTION, contradicted, detail);
        }
        return new CrossCheckVerdict(CrossCheckVerdict.Status.CONSISTENT, List.of(), "The reference solution passes every independently-authored contract test.");
    }

    /** The build's failed/errored test names excluding build/compile/configure gates, deduplicated — the same exemption the acceptance gate applies. */
    private static List<String> gradableFailures(DifferentialVerificationService.BuildSummary build) {
        return build.testFailedNames().stream().filter(name -> !BuildGateTestNames.isBuildGate(name)).distinct().toList();
    }
}
