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
 * Runs a DECORRELATED correctness cross-check: takes an independently-authored shadow test suite (from a live examiner agent, or a checked-in fixture) and runs it against the REAL
 * solution and template through the exact same production build+parse path the differential oracle uses, so the shadow suite is judged with parity-by-construction.
 * <p>
 * This is the runner half of Design F (deterministically shippable now). It closes the {@code realistic-pasted-lru} false-accept: a solution that FAILS a test derived only from
 * its
 * own stated contract is contradicting its own statement — a correctness defect the same-author differential oracle is structurally blind to (its co-authored tests encode the same
 * wrong model). The runner is exercise-agnostic: it flags any solution-side failure that is not a build/compile gate, from ANY shadow suite, and never decides acceptance itself.
 * <p>
 * It NEVER loosens the oracle's verdict: on doubt it fails OPEN ({@link CorrectnessCrossCheck.Status#INCONCLUSIVE}), so it can only ever ADD an advisory (or, behind a config flag,
 * a
 * hard block on top of an already-accepted exercise).
 */
@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class CorrectnessCrossCheckService {

    private static final Logger log = LoggerFactory.getLogger(CorrectnessCrossCheckService.class);

    private final SandboxBuildCommandService sandboxBuildCommandService;

    private final AuthoritativeVerificationService verifier;

    public CorrectnessCrossCheckService(SandboxBuildCommandService sandboxBuildCommandService, AuthoritativeVerificationService verifier) {
        this.sandboxBuildCommandService = sandboxBuildCommandService;
        this.verifier = verifier;
    }

    /**
     * Runs the given shadow suite against the real solution and template and decides whether the solution contradicts its own stated contract.
     * <p>
     * The shadow suite is seeded into a SEPARATE workspace directory ({@code shadow-tests/}) — it never overwrites the agent's own {@code tests/} — and each build re-wipes and
     * re-collects its verifier-owned reports dir, so running this after the main differential in the same session is collision-free.
     *
     * @param sandbox         the open sandbox session (the same one the differential ran in)
     * @param sessionId       the sandbox session id
     * @param exercise        the exercise being cross-checked (drives the per-language {@code verify.sh})
     * @param shadowTestFiles the independently-authored suite, repository-relative path to content (the harness manifests plus the examiner's test sources); empty skips the check
     * @return the cross-check report (never {@code null})
     */
    public CorrectnessCrossCheck runAgainstShadowSuite(InteractiveSandbox sandbox, String sessionId, ProgrammingExercise exercise, Map<String, String> shadowTestFiles) {
        if (shadowTestFiles == null || shadowTestFiles.isEmpty()) {
            return CorrectnessCrossCheck.skipped("No independently-authored shadow suite was produced.");
        }
        try {
            seedShadowSuite(sandbox, sessionId, shadowTestFiles);
            AuthoritativeVerificationService.BuildSummary solution = verifier.runReportedBuild(sandbox, sessionId, exercise,
                    sandboxBuildCommandService.crosscheckSolutionBuildCommand(), GenerationWorkspaceService.directoryFor(RepositoryType.SOLUTION));
            AuthoritativeVerificationService.BuildSummary template = verifier.runReportedBuild(sandbox, sessionId, exercise,
                    sandboxBuildCommandService.crosscheckTemplateBuildCommand(), GenerationWorkspaceService.directoryFor(RepositoryType.TEMPLATE));
            return decide(solution, template);
        }
        catch (RuntimeException e) {
            // The cross-check is advisory-by-default and must never perturb a run; a build/copyOut error is treated as INCONCLUSIVE (fail-open), never a reject.
            log.warn("Correctness cross-check could not run; treating as inconclusive: {}", e.getMessage());
            return CorrectnessCrossCheck.inconclusive("The cross-check build could not run: " + e.getMessage());
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
     * Decides the verdict from the two parsed build summaries. Fail-open on any doubt (never a reject); a non-build-gate solution failure is a contradiction; otherwise consistent.
     */
    private static CorrectnessCrossCheck decide(AuthoritativeVerificationService.BuildSummary solution, AuthoritativeVerificationService.BuildSummary template) {
        if (solution.timedOut() || solution.tests() == 0) {
            // The shadow suite did not compile/run against the real solution (template = solution-with-stubs shares the public API, so this is rare); fail OPEN, never a reject.
            return CorrectnessCrossCheck.inconclusive("The shadow suite did not run against the reference solution (it did not compile, ran no tests, or timed out).");
        }
        List<String> contradicted = gradableFailures(solution);
        int shadowTestsFailingTemplate = gradableFailures(template).size();
        if (!contradicted.isEmpty()) {
            String detail = "The reference solution fails " + contradicted.size() + " independently-authored contract test(s) of " + solution.tests() + ".";
            return new CorrectnessCrossCheck(CorrectnessCrossCheck.Status.CONTRADICTION, contradicted, List.copyOf(solution.testNames()), solution.tests(),
                    shadowTestsFailingTemplate, detail);
        }
        return new CorrectnessCrossCheck(CorrectnessCrossCheck.Status.CONSISTENT, List.of(), List.copyOf(solution.testNames()), solution.tests(), shadowTestsFailingTemplate,
                "The reference solution passes every independently-authored contract test.");
    }

    /** The build's failed/errored test names EXCLUDING build/compile/configure gates, deduplicated — the same exemption the acceptance gate applies. */
    private static List<String> gradableFailures(AuthoritativeVerificationService.BuildSummary build) {
        return build.testFailedNames().stream().filter(name -> !BuildGateTestNames.isBuildGate(name)).distinct().toList();
    }
}
