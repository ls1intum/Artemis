package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckResult;

class SandboxAgentToolsCheckpointStateTest {

    private static final Set<String> CHECKPOINTED_MUTABLE_FIELDS = Set.of("bashSequence", "sandboxSessionTerminated", "currentStage", "repairWritableRoots",
            "seededStructuralTestNames", "submitVetoed", "dirtySinceLastPassingCheck", "cachedPassingCheckStage", "cachedPassingCheck", "lastTestsReport");

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
        set(source, "seededStructuralTestNames", Set.of("StructuralContractTest"));
        set(source, "submitVetoed", true);
        set(source, "dirtySinceLastPassingCheck", false);
        set(source, "cachedPassingCheckStage", GenerationStage.TESTS);
        set(source, "cachedPassingCheck", StageCheckResult.passed("cached"));

        SandboxAgentTools restored = new SandboxAgentTools(org.mockito.Mockito.mock(InteractiveSandbox.class), "restored");
        restored.restoreCheckpointState(source.checkpointState());

        assertThat(restored.checkpointState()).isEqualTo(source.checkpointState());
        assertThat(restored.consumeSubmitVeto()).isTrue();
        assertThat(restored.consumeSubmitVeto()).isFalse();
    }

    private static void set(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
