package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;

class AtlasOrchestratorTerminalToolServiceTest {

    private final Map<String, Object> context = new HashMap<>();

    private final AtlasToolCallBudget budget = AtlasToolCallBudget.budgetForContext(context);

    private final AtlasOrchestratorTerminalToolService terminal = new AtlasOrchestratorTerminalToolService();

    @Test
    void verifiedCompletionRequiresFreshIndexAndPreservesSummary() {
        assertThatThrownBy(() -> complete(true, "Done")).hasMessageContaining("index refresh");
        index();
        tool("editCompetency", () -> "{}").call("{}");
        assertThatThrownBy(() -> complete(true, "Done")).hasMessageContaining("index refresh");
        index();
        assertThat(complete(true, "Updated the competency and verified its links.")).isEqualTo("Updated the competency and verified its links.");
        assertThat(budget.completion().verified()).isTrue();
        assertThat(budget.completion().message()).isEqualTo("Updated the competency and verified its links.");
    }

    @Test
    void completionPreventsLaterWritesAndDuplicateCompletion() {
        index();
        complete(true, "Already correct.");
        AtomicInteger writes = new AtomicInteger();
        assertThatThrownBy(() -> tool("editCompetency", () -> Integer.toString(writes.incrementAndGet())).call("{}")).hasMessageContaining("complete");
        assertThat(writes).hasValue(0);
        assertThatThrownBy(() -> complete(false, "Different result")).hasMessageContaining("already called");
    }

    @Test
    void incompleteCompletionNeedsNoSuccessClaimOrFreshRead() {
        assertThat(complete(false, "Two mappings remain unverified.")).contains("unverified");
        assertThat(budget.completion().verified()).isFalse();
    }

    @Test
    void malformedSummaryOrMissingContextCannotComplete() {
        assertThatThrownBy(() -> complete(false, "  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> terminal.completeOrchestration(false, "Incomplete", new ToolContext(Map.of()))).hasMessageContaining("context is missing");
        assertThat(budget.completion()).isNull();
    }

    @Test
    void errorIndexCannotVerify() {
        tool("listCompetencyIndex", () -> "{\"error\":\"course missing\"}").call("{}");
        assertThatThrownBy(() -> complete(true, "Done")).hasMessageContaining("index refresh");
    }

    @Test
    void blockedWorkForcesIncompleteCompletion() {
        for (int i = 0; i < 224; i++) {
            tool("getExerciseContent", () -> "{}").call("{}");
        }
        assertThat(tool("editCompetency", () -> "{}").call("{}")).contains("NOT EXECUTED");
        index();
        assertThatThrownBy(() -> complete(true, "Done")).hasMessageContaining("budget-blocked");
        complete(false, "The remaining mapping needs instructor review.");
        assertThatThrownBy(() -> AtlasToolCallBudget.checkResponse(null, context)).isInstanceOfSatisfying(AtlasToolCallBudget.LimitReachedException.class,
                ex -> assertThat(ex.summary()).contains("instructor review"));
    }

    @Test
    void indexReadOverlappingAWriteDoesNotVerifyTheFinalState() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var read = executor.submit(() -> tool("listCompetencyIndex", () -> {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Test read timed out");
                    }
                }
                catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return "{}";
            }).call("{}"));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                tool("editCompetency", () -> "{}").call("{}");
            }
            finally {
                release.countDown();
            }
            read.get(5, TimeUnit.SECONDS);
        }
        assertThatThrownBy(() -> complete(true, "Done")).hasMessageContaining("index refresh");
        index();
        complete(true, "Verified after the concurrent write.");
    }

    @Test
    void rejectedTerminalCallbackReturnsDirectlyWithoutRecordingSuccess() {
        var provider = org.springframework.ai.tool.method.MethodToolCallbackProvider.builder().toolObjects(terminal).build();
        var callback = AtlasToolCallBudget.decorate(provider, budget).getToolCallbacks()[0];
        assertThat(callback.getToolMetadata().returnDirect()).isTrue();
        assertThat(callback.call("{\"verified\":true,\"message\":\"Done\"}", new ToolContext(context))).contains("COMPLETION REJECTED", "index refresh");
        assertThat(budget.completion()).isNull();
        assertThat(budget.calls()).isEqualTo(1);
    }

    private String complete(boolean verified, String message) {
        return terminal.completeOrchestration(verified, message, new ToolContext(context));
    }

    private void index() {
        tool("listCompetencyIndex", () -> "{}").call("{}");
    }

    private ToolCallback tool(String name, Supplier<String> result) {
        ToolCallback callback = FunctionToolCallback.<Map<String, Object>, String>builder(name, input -> result.get()).inputType(Map.class).build();
        return AtlasToolCallBudget.decorate(ToolCallbackProvider.from(callback), budget).getToolCallbacks()[0];
    }
}
