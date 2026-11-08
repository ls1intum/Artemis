package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.aiworker.api.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationBuildTool;
import de.tum.cit.aet.artemis.hyperion.protocol.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.GenerationInput;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.RepositoryRole;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SpecFidelityCritic;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.ContractWitnessOutcome;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.DifferentialVerifier;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SeededStructuralTests;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome.Disposition;

class ExecutableProbeCompilationTest {

    private static final String SPEC = "## Rules\nR1. Return one.\nR2. Preserve every admitted request.";

    private static final String DIAGNOSTIC = "HyperionContractWitnessProbeTest.java:9: error: cannot find symbol variable subject";

    private static final ContractWitness BROKEN = new ContractWitness("R1", "checksOne", "@Test void checksOne() { assertEquals(1, subject.value()); }", "returns two");

    private static final ContractWitness REPAIRED = new ContractWitness("R1", "checksOne",
            "@Test void checksOne() { var subject = new Subject(); assertEquals(1, subject.value()); }", "returns two");

    private static final SemanticMutant MUTANT = new SemanticMutant("R1", "src/Subject.java", "class Subject {}", "class Subject { int value() { return 2; } }", BROKEN);

    private static final SemanticMutant CORRECTED_MUTANT = new SemanticMutant(MUTANT.ruleId(), MUTANT.solutionPath(), MUTANT.originalSolutionSource(), MUTANT.mutantSource(),
            REPAIRED);

    private static final Map<RepositoryRole, Map<String, String>> FILES = Map.of(RepositoryRole.TESTS, Map.of("test/SubjectTest.java", "class SubjectTest {}"),
            RepositoryRole.SOLUTION, Map.of(MUTANT.solutionPath(), MUTANT.originalSolutionSource()));

    private final DifferentialVerifier verifier = mock(DifferentialVerifier.class);

    private final SpecFidelityCritic critic = mock(SpecFidelityCritic.class);

    private final ExecutableEvidenceLedger ledger = new ExecutableEvidenceLedger(verifier, critic, mock(InteractiveSandbox.class), "s",
            new GenerationInput(1, "example", "Return one", false, Set.of(), Set.of(), GenerationBuildTool.GRADLE), ignored -> {
            }, () -> false, ignored -> {
            });

    @BeforeEach
    void proposeBrokenMutant() {
        when(critic.authorSemanticMutants(anyString(), anyMap(), anyList(), anyList(), any(), any())).thenReturn(List.of(MUTANT));
        when(verifier.evaluateSemanticMutants(any(), anyString(), any(), anyMap(), anyMap(), any(), eq(List.of(MUTANT)), any()))
                .thenReturn(List.of(new SemanticMutantOutcome(MUTANT, Disposition.INCONCLUSIVE, DIAGNOSTIC)));
    }

    private SpecFidelityReport review() {
        return ledger.review(SpecFidelityReport.empty(), FILES, FILES, null, SPEC, SeededStructuralTests.EMPTY, () -> {
        });
    }

    @Test
    void correctedCounterexampleMustExecuteBeforeItCanProveASurvivor() {
        when(critic.repairProbeCompilation(anyString(), anyString(), anyList(), any(), any())).thenReturn(List.of(REPAIRED));
        when(verifier.evaluateSemanticMutants(any(), anyString(), any(), anyMap(), anyMap(), any(), eq(List.of(CORRECTED_MUTANT)), any()))
                .thenReturn(List.of(new SemanticMutantOutcome(CORRECTED_MUTANT, Disposition.SURVIVED_GRADED_SUITE)));

        var report = review();

        assertThat(report.findings()).anySatisfy(finding -> assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.EXECUTABLE_WEAK_TEST_ORACLE));
        assertThat(report.findings()).noneMatch(finding -> finding.kind() == SpecFidelityReport.Kind.EXECUTABLE_EVIDENCE_UNAVAILABLE);
        assertThat(ledger.takeInconclusiveMutantProbes()).isZero();
        assertThat(FILES.get(RepositoryRole.SOLUTION)).containsEntry(MUTANT.solutionPath(), MUTANT.originalSolutionSource());
    }

    @Test
    void aCompilerFailureWithoutAnExecutableReplacementCannotLookLikeCleanQuality() {
        var report = review();

        assertThat(report.hasBlockingFindings()).isTrue();
        assertThat(report.findings()).anySatisfy(finding -> assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.EXECUTABLE_EVIDENCE_UNAVAILABLE));
        assertThat(ledger.takeInconclusiveMutantProbes()).isEqualTo(1);
    }

    @Test
    void aReferenceFailureIsNeverRewrittenAsACompilationRepair() {
        when(verifier.evaluateSemanticMutants(any(), anyString(), any(), anyMap(), anyMap(), any(), eq(List.of(MUTANT)), any()))
                .thenReturn(List.of(new SemanticMutantOutcome(MUTANT, Disposition.REFERENCE_TEST_FAILED, DIAGNOSTIC)));

        var report = review();

        assertThat(report.hasBlockingFindings()).isTrue();
        verify(critic, never()).repairProbeCompilation(anyString(), anyString(), anyList(), any(), any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = { false, true })
    void interruptedCompilerFeedbackRetainsOtherExecutedReferenceFailures(boolean buildFailure) {
        var witness = new ContractWitness("R2", "keepsRequests", "@Test void keepsRequests() { fail(); }", "drops a request");
        var referenceFailure = new SemanticMutant("R2", MUTANT.solutionPath(), MUTANT.originalSolutionSource(), MUTANT.mutantSource(), witness);
        when(critic.authorSemanticMutants(anyString(), anyMap(), anyList(), anyList(), any(), any())).thenReturn(List.of(MUTANT, referenceFailure));
        when(verifier.evaluateSemanticMutants(any(), anyString(), any(), anyMap(), anyMap(), any(), eq(List.of(MUTANT, referenceFailure)), any()))
                .thenReturn(List.of(new SemanticMutantOutcome(MUTANT, Disposition.INCONCLUSIVE, DIAGNOSTIC),
                        new SemanticMutantOutcome(referenceFailure, Disposition.REFERENCE_TEST_FAILED, "request missing")));
        if (buildFailure) {
            when(critic.repairProbeCompilation(anyString(), anyString(), anyList(), any(), any())).thenReturn(List.of(REPAIRED));
            when(verifier.evaluateSemanticMutants(any(), anyString(), any(), anyMap(), anyMap(), any(), eq(List.of(CORRECTED_MUTANT)), any()))
                    .thenThrow(new IllegalStateException("Corrected probe lost its session"));
        }
        else {
            when(critic.repairProbeCompilation(anyString(), anyString(), anyList(), any(), any())).thenThrow(new IllegalStateException("Provider unavailable"));
        }

        assertThatThrownBy(this::review).isInstanceOf(IllegalStateException.class);
        var retained = ledger.withoutExecutableReview(SpecFidelityReport.empty());

        assertThat(retained.hasBlockingFindings()).isTrue();
        assertThat(retained.findings()).anySatisfy(finding -> assertThat(finding.kind()).isEqualTo(SpecFidelityReport.Kind.EXECUTABLE_EVIDENCE_UNAVAILABLE));
    }

    @Test
    void anEarlierReferenceFailureCannotHaveItsWitnessRewrittenDuringRecheck() {
        when(critic.authorSemanticMutants(anyString(), anyMap(), anyList(), anyList(), any(), any())).thenReturn(List.of());
        when(critic.authorContractWitnesses(anyString(), anyString(), anyString(), any(), any())).thenReturn(List.of(BROKEN));
        when(verifier.evaluateContractWitnesses(any(), anyString(), any(), anyMap(), any(), eq(List.of(BROKEN)), any())).thenReturn(
                List.of(new ContractWitnessOutcome(BROKEN, ContractWitnessOutcome.Disposition.REFERENCE_TEST_FAILED, "reference returned two")),
                List.of(new ContractWitnessOutcome(BROKEN, ContractWitnessOutcome.Disposition.INCONCLUSIVE, DIAGNOSTIC)));
        assertThat(review().hasBlockingFindings()).isTrue();

        var rechecked = review();

        assertThat(rechecked.hasBlockingFindings()).isTrue();
        assertThat(ledger.withoutExecutableReview(SpecFidelityReport.empty()).hasBlockingFindings()).isTrue();
        verify(critic, never()).repairProbeCompilation(anyString(), anyString(), anyList(), any(), any());
    }

    @Test
    void anInterruptedCorrectedWitnessBuildRetainsOtherExecutedReferenceFailures() {
        var failedReference = new ContractWitness("R2", "keepsRequests", "@Test void keepsRequests() { fail(); }", "drops a request");
        when(critic.authorSemanticMutants(anyString(), anyMap(), anyList(), anyList(), any(), any())).thenReturn(List.of());
        when(critic.authorContractWitnesses(anyString(), anyString(), anyString(), any(), any())).thenReturn(List.of(BROKEN, failedReference));
        when(verifier.evaluateContractWitnesses(any(), anyString(), any(), anyMap(), any(), eq(List.of(BROKEN, failedReference)), any()))
                .thenReturn(List.of(new ContractWitnessOutcome(BROKEN, ContractWitnessOutcome.Disposition.INCONCLUSIVE, DIAGNOSTIC),
                        new ContractWitnessOutcome(failedReference, ContractWitnessOutcome.Disposition.REFERENCE_TEST_FAILED, "request missing")));
        when(critic.repairProbeCompilation(anyString(), anyString(), anyList(), any(), any())).thenReturn(List.of(REPAIRED));
        when(verifier.evaluateContractWitnesses(any(), anyString(), any(), anyMap(), any(), eq(List.of(REPAIRED)), any()))
                .thenThrow(new IllegalStateException("Corrected probe lost its session"));

        assertThatThrownBy(this::review).isInstanceOf(IllegalStateException.class);
        assertThat(ledger.withoutExecutableReview(SpecFidelityReport.empty()).hasBlockingFindings()).isTrue();
    }

    @Test
    void aFreshWitnessUsesTheSameCompilerFeedbackAndExecutionPath() {
        when(critic.authorSemanticMutants(anyString(), anyMap(), anyList(), anyList(), any(), any())).thenReturn(List.of());
        when(critic.authorContractWitnesses(anyString(), anyString(), anyString(), any(), any())).thenReturn(List.of(BROKEN));
        when(verifier.evaluateContractWitnesses(any(), anyString(), any(), anyMap(), any(), eq(List.of(BROKEN)), any()))
                .thenReturn(List.of(new ContractWitnessOutcome(BROKEN, ContractWitnessOutcome.Disposition.INCONCLUSIVE, DIAGNOSTIC)));
        when(critic.repairProbeCompilation(anyString(), anyString(), anyList(), any(), any())).thenReturn(List.of(REPAIRED));
        when(verifier.evaluateContractWitnesses(any(), anyString(), any(), anyMap(), any(), eq(List.of(REPAIRED)), any()))
                .thenReturn(List.of(new ContractWitnessOutcome(REPAIRED, ContractWitnessOutcome.Disposition.REFERENCE_TEST_FAILED, "reference returned two")));

        var report = review();

        assertThat(report.hasBlockingFindings()).isTrue();
        assertThat(ledger.withoutExecutableReview(SpecFidelityReport.empty()).hasBlockingFindings()).isTrue();
    }
}
