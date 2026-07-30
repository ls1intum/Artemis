package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.SeededStructuralTests;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class SandboxAgentToolsCheckpointStateTest {

    private static final Set<String> CHECKPOINTED_MUTABLE_FIELDS = Set.of("bashSequence", "sandboxSessionTerminated", "currentStage", "repairWritableRoots",
            "seededStructuralTests", "submitVetoed", "dirtySinceLastPassingCheck", "cachedPassingCheckStage", "cachedPassingCheck", "lastTestsReport");

    @Test
    void everyMutableFieldHasAnExplicitCheckpointDecision() {
        Set<String> mutableFields = java.util.Arrays.stream(SandboxAgentTools.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())).map(Field::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(mutableFields).isEqualTo(java.util.stream.Stream.concat(CHECKPOINTED_MUTABLE_FIELDS.stream(), java.util.stream.Stream.of("structuralOracleRefresh"))
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void snapshotAndRestorePreserveContinuationAuthority() throws ReflectiveOperationException {
        SandboxAgentTools source = new SandboxAgentTools(org.mockito.Mockito.mock(InteractiveSandbox.class), "source");
        set(source, "bashSequence", 7);
        set(source, "currentStage", GenerationStage.STATEMENT);
        set(source, "repairWritableRoots", Set.of("tests"));
        set(source, "seededStructuralTests",
                new SeededStructuralTests(Set.of("StructuralContractTest"), java.util.Map.of("test/example/StructuralContractTest.java", "class StructuralContractTest {}")));
        set(source, "submitVetoed", true);
        set(source, "dirtySinceLastPassingCheck", false);
        set(source, "cachedPassingCheckStage", GenerationStage.TESTS);
        set(source, "cachedPassingCheck", StageCheckResult.passed("cached"));

        SandboxAgentTools restored = new SandboxAgentTools(org.mockito.Mockito.mock(InteractiveSandbox.class), "restored");
        restored.restoreCheckpointState(source.checkpointState());

        assertThat(restored.checkpointState()).isEqualTo(source.checkpointState());
        assertThat(restored.seededStructuralTests()).isEqualTo(source.seededStructuralTests());
        assertThat(restored.consumeSubmitVeto()).isTrue();
        assertThat(restored.consumeSubmitVeto()).isFalse();
    }

    @Test
    void restoredStatementGateReceivesTheExactTrustedStructuralBundle() {
        InteractiveSandbox sandbox = org.mockito.Mockito.mock(InteractiveSandbox.class);
        DifferentialVerificationService verifier = org.mockito.Mockito.mock(DifferentialVerificationService.class);
        ProgrammingExercise exercise = org.mockito.Mockito.mock(ProgrammingExercise.class);
        StageCheckService stageChecks = org.mockito.Mockito.mock(StageCheckService.class);
        SeededStructuralTests authority = new SeededStructuralTests(Set.of("testClass[Strategy]"),
                Map.of("test/example/StrategyStructureTest.java", "class StrategyStructureTest {}"));
        org.mockito.Mockito.when(stageChecks.check(org.mockito.ArgumentMatchers.eq(GenerationStage.STATEMENT), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.same(exercise), org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any())).thenReturn(StageCheckResult.passed("statement accepted"));

        SandboxAgentTools source = new SandboxAgentTools(sandbox, "session", verifier, exercise, Map.of(), false, stageChecks);
        source.configureStructuralOracleRefresh(() -> authority);
        source.refreshStructuralOracle();
        source.enterStage(GenerationStage.STATEMENT);

        SandboxAgentTools restored = new SandboxAgentTools(sandbox, "session", verifier, exercise, Map.of(), false, stageChecks);
        restored.configureStructuralOracleRefresh(() -> SeededStructuralTests.EMPTY);
        restored.restoreCheckpointState(source.checkpointState());
        assertThat(restored.verify()).contains("MECHANICAL PRECHECK: PASS");

        ArgumentCaptor<SeededStructuralTests> capturedAuthority = ArgumentCaptor.forClass(SeededStructuralTests.class);
        org.mockito.Mockito.verify(stageChecks).check(org.mockito.ArgumentMatchers.eq(GenerationStage.STATEMENT), org.mockito.ArgumentMatchers.same(sandbox),
                org.mockito.ArgumentMatchers.eq("session"), org.mockito.ArgumentMatchers.same(exercise), org.mockito.ArgumentMatchers.eq(Map.of()),
                org.mockito.ArgumentMatchers.isNull(), capturedAuthority.capture());
        assertThat(capturedAuthority.getValue()).isEqualTo(authority);
    }

    private static void set(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
