package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.atlas.dto.AppliedActionDTO;
import de.tum.cit.aet.artemis.atlas.dto.WorkerCompletionDTO;

class AtlasTerminalToolServicesTest {

    private AtlasWorkerTerminalToolService workerTerminal;

    @BeforeEach
    void setUp() {
        JsonMapper objectMapper = new JsonMapper();
        workerTerminal = new AtlasWorkerTerminalToolService(objectMapper);
    }

    @Test
    void completeWorkerTask_requiresEvidenceAndIsOneShot() {
        Map<String, Object> context = workerContext();
        ToolContext toolContext = new ToolContext(context);

        assertThat(workerTerminal.completeWorkerTask(true, "Done", toolContext)).contains("Inspect course state or receive a mutation outcome");
        assertThat(workerHolder(context)).hasValue(null);

        ((AtomicInteger) context.get(OrchestratorToolContextKeys.WORKER_READ_COUNT_KEY)).incrementAndGet();
        assertThat(workerTerminal.completeWorkerTask(true, "Created requested competency", toolContext)).contains("\"success\":true");
        assertThat(OrchestratorToolHelpers.isWorkerCompletionTerminal(toolContext)).isTrue();
        assertThat(workerTerminal.completeWorkerTask(false, "Changed mind", toolContext)).contains("already completed");
        assertThat(workerHolder(context)).hasValue(new WorkerCompletionDTO(true, "Created requested competency"));
        assertThat(OrchestratorToolHelpers.isWorkerCompletionTerminal(toolContext)).isFalse();
    }

    @Test
    void completeWorkerTask_acceptsAppliedActionAsEvidence() {
        Map<String, Object> context = workerContext();
        OrchestratorToolContextKeys.AppliedActionsBuffer buffer = (OrchestratorToolContextKeys.AppliedActionsBuffer) context.get(OrchestratorToolContextKeys.APPLIED_ACTIONS_KEY);
        buffer.actions().add(AppliedActionDTO.create(1L, "Loops", "Created competency", "Exercise requires loops"));

        String result = workerTerminal.completeWorkerTask(true, "Created competency", new ToolContext(context));

        assertThat(result).contains("\"completed\":true");
    }

    @Test
    void attemptedWriteCapIsTwoHundredFiftySix() {
        assertThat(OrchestratorToolContextKeys.MAX_WRITE_CALLS).isEqualTo(256);
        OrchestratorToolContextKeys.AppliedActionsBuffer buffer = new OrchestratorToolContextKeys.AppliedActionsBuffer(Collections.synchronizedList(new ArrayList<>()));
        for (int i = 0; i < 256; i++) {
            assertThat(buffer.tryReserveSlot(OrchestratorToolContextKeys.MAX_WRITE_CALLS)).isTrue();
        }
        assertThat(buffer.tryReserveSlot(OrchestratorToolContextKeys.MAX_WRITE_CALLS)).isFalse();
    }

    private static Map<String, Object> workerContext() {
        Map<String, Object> context = new HashMap<>();
        context.put(OrchestratorToolContextKeys.WORKER_COMPLETION_KEY, new AtomicReference<WorkerCompletionDTO>());
        context.put(OrchestratorToolContextKeys.TOOL_SEQUENCE_KEY, new AtomicLong());
        context.put(OrchestratorToolContextKeys.WORKER_COMPLETION_SEQUENCE_KEY, new AtomicLong());
        context.put(OrchestratorToolContextKeys.WORKER_READ_COUNT_KEY, new AtomicInteger());
        context.put(OrchestratorToolContextKeys.WORKER_ACTION_START_KEY, 0);
        context.put(OrchestratorToolContextKeys.APPLIED_ACTIONS_KEY, new OrchestratorToolContextKeys.AppliedActionsBuffer(Collections.synchronizedList(new ArrayList<>())));
        return context;
    }

    @SuppressWarnings("unchecked")
    private static AtomicReference<WorkerCompletionDTO> workerHolder(Map<String, Object> context) {
        return (AtomicReference<WorkerCompletionDTO>) context.get(OrchestratorToolContextKeys.WORKER_COMPLETION_KEY);
    }
}
