package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.localci.service.BuildPhasesTemplateService;
import de.tum.cit.aet.artemis.localci.service.BuildScriptProviderService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Deterministic proof that the DECORRELATED correctness cross-check CATCHES the {@code realistic-pasted-lru} false-accept without a live model or Docker.
 * <p>
 * The mechanism: an independently-authored shadow suite (here, the hand-authored correct LRU suite embedded below — including the decorrelating
 * {@code evictsLeastRecentlyUsedInsertionOrder}
 * test the co-authored suite omitted) is run against the real solution/template through the SAME production build+parse path the differential uses. A {@link ScriptedShadowSandbox}
 * serves the JUnit reports that running that suite would produce: against the BUGGY LRU solution the insertion-order eviction test FAILS (the solution keeps key 1 and evicts key
 * 2),
 * so the cross-check reports {@link CorrectnessCrossCheck.Status#CONTRADICTION} — the false-accept the same-author differential oracle accepted. Against a CORRECT solution the
 * same
 * suite passes ({@link CorrectnessCrossCheck.Status#CONSISTENT}), proving no false reject. The scripted reports fully determine the expected outcome, so the test is hermetic.
 */
class CorrectnessCrossCheckServiceTest {

    /**
     * The hand-authored CORRECT shadow suite, derived ONLY from the LRU problem statement's stated contract. Its decorrelating test — {@code evictsLeastRecentlyUsedInsertionOrder}
     * (put,put,put with NO intervening access) — is the one the co-authored suite omitted (every co-authored eviction test does a {@code get(1)} first, rescuing key 1), so it is
     * what
     * exposes the buggy solution that inserts new entries at the LRU end. Embedded (not read from a build artifact) so the test is committed and self-contained.
     */
    private static final Map<String, String> SHADOW_SUITE = Map.of("pom.xml", "<project/>", "test/de/test/LRUCacheTest.java", """
            package de.test;
            import static org.junit.jupiter.api.Assertions.*;
            import org.junit.jupiter.api.Test;
            class LRUCacheTest {
                @Test void evictsLeastRecentlyUsedInsertionOrder() {
                    LRUCache c = new LRUCache(2);
                    c.put(1, 1); c.put(2, 2); c.put(3, 3); // no access between puts: key 1 is LRU, must be evicted
                    assertEquals(-1, c.get(1), "least-recently-used key 1 must be evicted");
                    assertEquals(3, c.get(3), "key 3 must be present");
                    assertEquals(2, c.get(2), "key 2 must be present");
                }
                @Test void getNonExistingReturnsMinusOne() {
                    assertEquals(-1, new LRUCache(2).get(42), "get on a missing key must return -1");
                }
                @Test void putThenGet() {
                    LRUCache c = new LRUCache(2); c.put(1, 10);
                    assertEquals(10, c.get(1), "put then get must return the stored value");
                }
            }
            """);

    private static final List<String> SHADOW_NAMES = List.of("evictsLeastRecentlyUsedInsertionOrder", "getNonExistingReturnsMinusOne", "putThenGet");

    private static CorrectnessCrossCheckService newService() {
        BuildPhasesTemplateService phases = mock(BuildPhasesTemplateService.class);
        when(phases.getDefaultBuildPlanPhasesFor(any())).thenReturn(List.of());
        SandboxBuildCommandService buildCommandService = new SandboxBuildCommandService(Optional.of(phases), Optional.of(new BuildScriptProviderService()));
        AuthoritativeVerificationService verifier = new AuthoritativeVerificationService(buildCommandService);
        return new CorrectnessCrossCheckService(buildCommandService, verifier);
    }

    private static CorrectnessCrossCheck run(ScriptedShadowSandbox sandbox) {
        return newService().runAgainstShadowSuite(sandbox, "s", new ProgrammingExercise(), SHADOW_SUITE);
    }

    @Test
    void buggySolution_contradicts() {
        // Against the buggy LRU solution the insertion-order eviction test FAILS while the rest pass; the template fails all.
        ScriptedShadowSandbox sandbox = new ScriptedShadowSandbox(SHADOW_NAMES, List.of("evictsLeastRecentlyUsedInsertionOrder"), 1, false, SHADOW_NAMES, SHADOW_NAMES, 1, false);
        CorrectnessCrossCheck result = run(sandbox);
        assertThat(result.status()).isEqualTo(CorrectnessCrossCheck.Status.CONTRADICTION);
        assertThat(result.contradictedTests()).containsExactly("evictsLeastRecentlyUsedInsertionOrder");
        assertThat(result.shadowTestsAgainstSolution()).isEqualTo(3);
    }

    @Test
    void correctSolution_consistent() {
        // The SAME shadow suite passes entirely against a correct solution -> no false reject.
        ScriptedShadowSandbox sandbox = new ScriptedShadowSandbox(SHADOW_NAMES, List.of(), 0, false, SHADOW_NAMES, SHADOW_NAMES, 1, false);
        CorrectnessCrossCheck result = run(sandbox);
        assertThat(result.status()).isEqualTo(CorrectnessCrossCheck.Status.CONSISTENT);
        assertThat(result.contradictedTests()).isEmpty();
    }

    @Test
    void shadowDoesNotCompileVsSolution_inconclusive() {
        // The shadow suite did not compile/run against the solution (0 tests) -> fail-open, never a reject.
        ScriptedShadowSandbox sandbox = new ScriptedShadowSandbox(List.of(), List.of(), 1, false, List.of(), List.of(), 1, false);
        CorrectnessCrossCheck result = run(sandbox);
        assertThat(result.status()).isEqualTo(CorrectnessCrossCheck.Status.INCONCLUSIVE);
        assertThat(result.contradictedTests()).isEmpty();
    }

    @Test
    void solutionTimeout_inconclusive() {
        ScriptedShadowSandbox sandbox = new ScriptedShadowSandbox(List.of(), List.of(), 124, true, SHADOW_NAMES, SHADOW_NAMES, 1, false);
        assertThat(run(sandbox).status()).isEqualTo(CorrectnessCrossCheck.Status.INCONCLUSIVE);
    }

    @Test
    void buildGateFailureIgnored_consistent() {
        // Only a build/compile gate fails on the solution (never a behavioural test) -> excluded, parity with the acceptance gate -> CONSISTENT.
        List<String> withGate = List.of("TestConfigure", "putThenGet");
        ScriptedShadowSandbox sandbox = new ScriptedShadowSandbox(withGate, List.of("TestConfigure"), 1, false, withGate, withGate, 1, false);
        CorrectnessCrossCheck result = run(sandbox);
        assertThat(result.status()).isEqualTo(CorrectnessCrossCheck.Status.CONSISTENT);
        assertThat(result.contradictedTests()).isEmpty();
    }

    @Test
    void emptyShadowSuite_skipped() {
        assertThat(newService().runAgainstShadowSuite(new ScriptedShadowSandbox(List.of(), List.of(), 0, false, List.of(), List.of(), 0, false), "s", new ProgrammingExercise(),
                Map.of())).extracting(CorrectnessCrossCheck::status).isEqualTo(CorrectnessCrossCheck.Status.SKIPPED);
    }

    @Test
    void contradiction_rendersThroughTheAdvisoryPlumbing() {
        ScriptedShadowSandbox sandbox = new ScriptedShadowSandbox(SHADOW_NAMES, List.of("evictsLeastRecentlyUsedInsertionOrder"), 1, false, SHADOW_NAMES, SHADOW_NAMES, 1, false);
        CorrectnessCrossCheck result = run(sandbox);
        SpecFidelityReport.Finding advisory = result.toAdvisoryFinding();
        assertThat(advisory.kind()).isEqualTo(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION);
        assertThat(advisory.requirement()).contains("evictsLeastRecentlyUsedInsertionOrder");
        assertThat(result.renderForRetryPrompt()).contains("evictsLeastRecentlyUsedInsertionOrder").contains("contradicts its own stated behaviour");
    }

    /**
     * Serves the shadow-suite build reports on {@code copyOut} (routed by the reports-dir path ending {@code /solution} or {@code /template}) and the build exit code/timeout on
     * {@code exec}, so the cross-check runs its real seed+build+copyOut+parse path against scripted reports — no Docker.
     */
    private static final class ScriptedShadowSandbox implements InteractiveSandbox {

        private final List<String> solutionNames;

        private final List<String> solutionFailed;

        private final int solutionExit;

        private final boolean solutionTimedOut;

        private final List<String> templateNames;

        private final List<String> templateFailed;

        private final int templateExit;

        private final boolean templateTimedOut;

        private ScriptedShadowSandbox(List<String> solutionNames, List<String> solutionFailed, int solutionExit, boolean solutionTimedOut, List<String> templateNames,
                List<String> templateFailed, int templateExit, boolean templateTimedOut) {
            this.solutionNames = solutionNames;
            this.solutionFailed = solutionFailed;
            this.solutionExit = solutionExit;
            this.solutionTimedOut = solutionTimedOut;
            this.templateNames = templateNames;
            this.templateFailed = templateFailed;
            this.templateExit = templateExit;
            this.templateTimedOut = templateTimedOut;
        }

        @Override
        public SandboxExecResult exec(String sessionId, Duration timeout, String... command) {
            String joined = String.join(" ", command);
            if (!joined.contains("verify.sh")) {
                // mkdir for the pristine script dir, cat, etc. -> succeed.
                return new SandboxExecResult(0, "", "", false);
            }
            boolean solution = joined.contains(" solution ") || joined.endsWith(" solution");
            return solution ? new SandboxExecResult(solutionExit, "ran", "", solutionTimedOut) : new SandboxExecResult(templateExit, "ran", "", templateTimedOut);
        }

        @Override
        public String createSession(SandboxSessionSpec spec) {
            return "s";
        }

        @Override
        public void copyIn(String sessionId, String destinationPath, InputStream tarArchive) {
        }

        @Override
        public TarArchiveInputStream copyOut(String sessionId, String path) {
            if (path.endsWith("/solution")) {
                return ReportTarFixtures.junitReports("solution", solutionNames, solutionFailed);
            }
            if (path.endsWith("/template")) {
                return ReportTarFixtures.junitReports("template", templateNames, templateFailed);
            }
            return null;
        }

        @Override
        public void destroySession(String sessionId) {
        }
    }
}
