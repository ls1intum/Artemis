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
 * Deterministic proof that the DECORRELATED cross-check CATCHES the {@code realistic-pasted-lru} false-accept without a live model or Docker.
 * <p>
 * The mechanism: an independently-authored shadow suite (here, the hand-authored correct LRU suite embedded below — including the decorrelating
 * {@code evictsLeastRecentlyUsedInsertionOrder}
 * test the co-authored suite omitted) is run against the real solution/template through the SAME production build+parse path the differential uses. A {@link ScriptedShadowSandbox}
 * serves the JUnit reports that running that suite would produce: against the BUGGY LRU solution the insertion-order eviction test FAILS (the solution keeps key 1 and evicts key
 * 2),
 * so the cross-check reports {@link CrossCheckVerdict.Status#CONTRADICTION} — the false-accept the same-author differential oracle accepted. Against a CORRECT solution the
 * same
 * suite passes ({@link CrossCheckVerdict.Status#CONSISTENT}), proving no false reject. The scripted reports fully determine the expected outcome, so the test is hermetic.
 */
class CrossCheckServiceTest {

    /**
     * The hand-authored CORRECT shadow suite, embedded (not read from a build artifact) so the test is committed and self-contained; its decorrelating test is discussed on the
     * class javadoc.
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

    private static CrossCheckService newService() {
        BuildPhasesTemplateService phases = mock(BuildPhasesTemplateService.class);
        when(phases.getDefaultBuildPlanPhasesFor(any())).thenReturn(List.of());
        SandboxBuildCommandService buildCommandService = new SandboxBuildCommandService(Optional.of(phases), Optional.of(new BuildScriptProviderService()));
        DifferentialVerificationService verifier = new DifferentialVerificationService(buildCommandService);
        return new CrossCheckService(buildCommandService, verifier);
    }

    private static CrossCheckVerdict run(ScriptedShadowSandbox sandbox) {
        return newService().runAgainstShadowSuite(sandbox, "s", new ProgrammingExercise(), SHADOW_SUITE);
    }

    @Test
    void buggySolution_contradicts() {
        // Against the buggy LRU solution the insertion-order eviction test FAILS while the rest pass.
        ScriptedShadowSandbox sandbox = new ScriptedShadowSandbox(SHADOW_NAMES, List.of("evictsLeastRecentlyUsedInsertionOrder"), 1, false);
        CrossCheckVerdict result = run(sandbox);
        assertThat(result.status()).isEqualTo(CrossCheckVerdict.Status.CONTRADICTION);
        assertThat(result.contradictedTests()).containsExactly("evictsLeastRecentlyUsedInsertionOrder");
    }

    @Test
    void correctSolution_consistent() {
        // The SAME shadow suite passes entirely against a correct solution -> no false reject.
        ScriptedShadowSandbox sandbox = new ScriptedShadowSandbox(SHADOW_NAMES, List.of(), 0, false);
        CrossCheckVerdict result = run(sandbox);
        assertThat(result.status()).isEqualTo(CrossCheckVerdict.Status.CONSISTENT);
        assertThat(result.contradictedTests()).isEmpty();
    }

    @Test
    void shadowDoesNotCompileVsSolution_inconclusive() {
        // The shadow suite did not compile/run against the solution (0 tests parsed) -> fail-open, never a reject.
        ScriptedShadowSandbox sandbox = new ScriptedShadowSandbox(List.of(), List.of(), 1, false);
        CrossCheckVerdict result = run(sandbox);
        assertThat(result.status()).isEqualTo(CrossCheckVerdict.Status.INCONCLUSIVE);
        assertThat(result.contradictedTests()).isEmpty();
    }

    @Test
    void solutionTimeout_inconclusive() {
        // Distinct fail-open branch: the solution build TIMED OUT (production runPristineBuild short-circuits to a zero-test summary before any parse) -> INCONCLUSIVE, never a
        // reject.
        ScriptedShadowSandbox sandbox = new ScriptedShadowSandbox(List.of(), List.of(), 124, true);
        assertThat(run(sandbox).status()).isEqualTo(CrossCheckVerdict.Status.INCONCLUSIVE);
    }

    @Test
    void buildGateFailureIgnored_consistent() {
        // Only a build/compile gate fails on the solution (never a behavioural test) -> excluded, parity with the acceptance gate -> CONSISTENT.
        List<String> withGate = List.of("TestConfigure", "putThenGet");
        ScriptedShadowSandbox sandbox = new ScriptedShadowSandbox(withGate, List.of("TestConfigure"), 1, false);
        CrossCheckVerdict result = run(sandbox);
        assertThat(result.status()).isEqualTo(CrossCheckVerdict.Status.CONSISTENT);
        assertThat(result.contradictedTests()).isEmpty();
    }

    @Test
    void buildError_inconclusive() {
        // The outer fail-open net: a build/copyOut RuntimeException must fail open (never a reject), not propagate out of the cross-check.
        CrossCheckVerdict result = newService().runAgainstShadowSuite(new ThrowingSandbox(), "s", new ProgrammingExercise(), SHADOW_SUITE);
        assertThat(result.status()).isEqualTo(CrossCheckVerdict.Status.INCONCLUSIVE);
        assertThat(result.contradictedTests()).isEmpty();
    }

    @Test
    void emptyShadowSuite_skipped() {
        assertThat(newService().runAgainstShadowSuite(new ScriptedShadowSandbox(List.of(), List.of(), 0, false), "s", new ProgrammingExercise(), Map.of()))
                .extracting(CrossCheckVerdict::status).isEqualTo(CrossCheckVerdict.Status.SKIPPED);
    }

    @Test
    void contradiction_rendersThroughTheAdvisoryPlumbing() {
        ScriptedShadowSandbox sandbox = new ScriptedShadowSandbox(SHADOW_NAMES, List.of("evictsLeastRecentlyUsedInsertionOrder"), 1, false);
        CrossCheckVerdict result = run(sandbox);
        SpecFidelityReport.Finding advisory = result.toAdvisoryFinding();
        assertThat(advisory.kind()).isEqualTo(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION);
        assertThat(advisory.requirement()).contains("evictsLeastRecentlyUsedInsertionOrder");
    }

    /**
     * Serves the shadow-suite build reports on {@code copyOut} (routed by the reports-dir path ending {@code /solution}) and the build exit code/timeout on {@code exec}, so the
     * cross-check runs its real seed+build+copyOut+parse path against scripted reports — no Docker. The cross-check only ever builds the solution, so only that assignment is
     * scripted.
     */
    /** A sandbox whose every operation throws, to drive the outer {@code catch (RuntimeException)} fail-open path. */
    private static final class ThrowingSandbox implements InteractiveSandbox {

        @Override
        public SandboxExecResult exec(String sessionId, Duration timeout, String... command) {
            throw new RuntimeException("boom");
        }

        @Override
        public String createSession(SandboxSessionSpec spec) {
            return "s";
        }

        @Override
        public void copyIn(String sessionId, String destinationPath, InputStream tarArchive) {
            throw new RuntimeException("boom");
        }

        @Override
        public TarArchiveInputStream copyOut(String sessionId, String path) {
            throw new RuntimeException("boom");
        }

        @Override
        public void destroySession(String sessionId) {
        }
    }

    private static final class ScriptedShadowSandbox implements InteractiveSandbox {

        private final List<String> names;

        private final List<String> failed;

        private final int exit;

        private final boolean timedOut;

        private ScriptedShadowSandbox(List<String> names, List<String> failed, int exit, boolean timedOut) {
            this.names = names;
            this.failed = failed;
            this.exit = exit;
            this.timedOut = timedOut;
        }

        @Override
        public SandboxExecResult exec(String sessionId, Duration timeout, String... command) {
            if (!String.join(" ", command).contains("verify.sh")) {
                // mkdir for the pristine script dir, cat, etc. -> succeed.
                return new SandboxExecResult(0, "", "", false);
            }
            return new SandboxExecResult(exit, "ran", "", timedOut);
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
            return path.endsWith("/solution") ? ReportTarFixtures.junitReports("solution", names, failed) : null;
        }

        @Override
        public void destroySession(String sessionId) {
        }
    }
}
