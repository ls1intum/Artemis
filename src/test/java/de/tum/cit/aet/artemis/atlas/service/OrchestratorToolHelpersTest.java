package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

class OrchestratorToolHelpersTest {

    @Test
    void missingOrWrongContextNeverCreatesWorkerEvidence() {
        for (ToolContext context : java.util.Arrays.asList(null, new ToolContext(Map.of()),
                new ToolContext(Map.of(OrchestratorToolContextKeys.COURSE_ID_KEY, "42", OrchestratorToolContextKeys.APPLIED_ACTIONS_KEY, "not a buffer",
                        OrchestratorToolContextKeys.WORKER_MUTATION_ERROR_KEY, "not a boolean", OrchestratorToolContextKeys.TOOL_SEQUENCE_KEY, 1,
                        OrchestratorToolContextKeys.WORKER_COMPLETION_SEQUENCE_KEY, 1)))) {
            assertThat(OrchestratorToolHelpers.courseIdFromContext(context)).isNull();
            assertThat(OrchestratorToolHelpers.tryReserveDelegationSlot(context)).isFalse();
            assertThat(OrchestratorToolHelpers.tryReserveWriteSlot(context)).isTrue();
            assertThat(OrchestratorToolHelpers.markWorkerToolActivity(context)).isZero();
            OrchestratorToolHelpers.markWorkerRead(context);
            OrchestratorToolHelpers.markWorkerMutationError(context);
            OrchestratorToolHelpers.markWorkerCompletion(context, 1);
            assertThat(OrchestratorToolHelpers.hasWorkerMutationError(context)).isFalse();
            assertThat(OrchestratorToolHelpers.isWorkerCompletionTerminal(context)).isFalse();
        }
    }

    @Test
    void completionMustFollowLastToolAndCannotBeAnInitialZeroMarker() {
        var sequence = new AtomicLong();
        var completion = new AtomicLong();
        var context = new ToolContext(Map.of(OrchestratorToolContextKeys.TOOL_SEQUENCE_KEY, sequence, OrchestratorToolContextKeys.WORKER_COMPLETION_SEQUENCE_KEY, completion));
        assertThat(OrchestratorToolHelpers.isWorkerCompletionTerminal(context)).isFalse();
        long first = OrchestratorToolHelpers.markWorkerToolActivity(context);
        assertThat(first).isEqualTo(1);
        OrchestratorToolHelpers.markWorkerCompletion(context, first);
        assertThat(OrchestratorToolHelpers.isWorkerCompletionTerminal(context)).isTrue();
        assertThat(OrchestratorToolHelpers.markWorkerToolActivity(context)).isEqualTo(2);
        assertThat(OrchestratorToolHelpers.isWorkerCompletionTerminal(context)).isFalse();
        assertThat(completion).hasValue(1);
        var onlySequence = new ToolContext(Map.of(OrchestratorToolContextKeys.TOOL_SEQUENCE_KEY, sequence));
        assertThat(OrchestratorToolHelpers.markWorkerToolActivity(onlySequence)).isZero();
        assertThat(OrchestratorToolHelpers.isWorkerCompletionTerminal(onlySequence)).isFalse();
        var onlyCompletion = new ToolContext(Map.of(OrchestratorToolContextKeys.WORKER_COMPLETION_SEQUENCE_KEY, completion));
        assertThat(OrchestratorToolHelpers.markWorkerToolActivity(onlyCompletion)).isZero();
    }

    @Test
    void readAndMutationOutcomesCarrySeparateSuccessAndFailureEvidence() {
        var success = new AtomicInteger();
        var reads = new AtomicInteger();
        var mutations = new AtomicInteger();
        var error = new AtomicBoolean();
        var context = new ToolContext(Map.of(OrchestratorToolContextKeys.WORKER_READ_COUNT_KEY, success, OrchestratorToolContextKeys.WORKER_READ_OUTCOME_COUNT_KEY, reads,
                OrchestratorToolContextKeys.WORKER_MUTATION_OUTCOME_COUNT_KEY, mutations, OrchestratorToolContextKeys.WORKER_MUTATION_ERROR_KEY, error));
        OrchestratorToolHelpers.markWorkerRead(context);
        assertThat(success).hasValue(1);
        assertThat(reads).hasValue(1);
        assertThat(OrchestratorToolHelpers.readErrorJson(JsonObjectMapper.get(), "missing", context)).contains("missing");
        assertThat(success).hasValue(1);
        assertThat(reads).hasValue(2);
        assertThat(OrchestratorToolHelpers.mutationNoOpJson(JsonObjectMapper.get(), "already linked", context)).contains("noop");
        assertThat(mutations).hasValue(1);
        assertThat(error).isFalse();
        assertThat(OrchestratorToolHelpers.mutationErrorJson(JsonObjectMapper.get(), "invalid weight", context)).contains("invalid weight");
        assertThat(mutations).hasValue(2);
        assertThat(error).isTrue();
    }

    @Test
    void delegationReservationStopsExactlyAtConfiguredLimit() {
        var count = new AtomicInteger();
        var context = new ToolContext(Map.of(OrchestratorToolContextKeys.DELEGATION_COUNT_KEY, count));
        for (int i = 0; i < OrchestratorToolContextKeys.MAX_DELEGATION_CALLS; i++) {
            assertThat(OrchestratorToolHelpers.tryReserveDelegationSlot(context)).isTrue();
        }
        assertThat(OrchestratorToolHelpers.tryReserveDelegationSlot(context)).isFalse();
        assertThat(count).hasValue(OrchestratorToolContextKeys.MAX_DELEGATION_CALLS);
    }

    @Test
    void overlongJustificationIsRejectedWhileBoundaryLengthRemainsValid() {
        var mapper = JsonObjectMapper.get();
        assertThat(OrchestratorToolHelpers.validateJustification(mapper, "x".repeat(501), null)).contains("at most 500");
        assertThat(OrchestratorToolHelpers.validateJustification(mapper, "x".repeat(500), null)).isNull();
    }
}
