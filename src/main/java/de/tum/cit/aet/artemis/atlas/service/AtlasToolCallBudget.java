package de.tum.cit.aet.artemis.atlas.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.atlas.dto.ExtractedContentDTO;
import de.tum.cit.aet.artemis.atlas.dto.OrchestrationCompletionDTO;

/**
 * One atomic tool-call quota shared by an autonomous Atlas run and every role worker it spawns.
 * The reservation happens before a callback is entered, so failed callbacks still consume quota and
 * concurrent callbacks cannot overspend the run-level cap.
 */
public final class AtlasToolCallBudget {

    /** Tool-context key carrying the budget object through parent and worker rounds. */
    public static final String CONTEXT_KEY = "atlasToolCallBudget";

    /** Maximum number of autonomous tool callbacks in one top-level run. */
    public static final int LIMIT = 256;

    /** Work stops here; the remaining callbacks are reserved for verification and completion. */
    public static final int WRAP_UP_AT = LIMIT - 32;

    private static final JsonMapper JSON = new JsonMapper();

    private final AtomicBoolean workBlocked = new AtomicBoolean();

    // Admission and completion share this monitor. No provider or tool work runs under the monitor.
    private long mutationVersion;

    private long verifiedVersion = -1;

    private int activeWork;

    @Nullable
    private OrchestrationCompletionDTO completion;

    /** @return the main orchestrator's terminal decision, if recorded */
    @Nullable
    public synchronized OrchestrationCompletionDTO completion() {
        return completion;
    }

    /**
     * Records the existing main-orchestrator completion contract after the final index read.
     *
     * @param verified whether the model verified all requested work
     * @param message  instructor-facing summary
     */
    public synchronized void complete(boolean verified, String message) {
        if (completion != null) {
            throw new IllegalStateException("completeOrchestration was already called.");
        }
        if (activeWork != 0) {
            throw new IllegalStateException("Wait for active work before completing orchestration.");
        }
        if (verified && (verifiedVersion != mutationVersion || workBlocked() || exhausted())) {
            throw new IllegalStateException("verified=true requires a successful index refresh after the latest work, with no budget-blocked work.");
        }
        completion = new OrchestrationCompletionDTO(verified, message);
    }

    private synchronized long begin(boolean work) {
        if (completion != null) {
            throw new IllegalStateException("Orchestration is complete; no further callbacks are allowed.");
        }
        if (work) {
            activeWork++;
            mutationVersion++;
            return mutationVersion;
        }
        return activeWork == 0 ? mutationVersion : -1;
    }

    private synchronized void finish(boolean work, long version, boolean verifiedIndex) {
        if (work) {
            activeWork--;
        }
        if (verifiedIndex && activeWork == 0 && version == mutationVersion) {
            verifiedVersion = version;
        }
    }

    private final Map<String, ExtractedContentDTO> contentSnapshots = new ConcurrentHashMap<>();

    private final List<Map<String, Object>> activity = java.util.Collections.synchronizedList(new ArrayList<>());

    /** @return callback evidence without raw arguments or tool responses */
    public List<Map<String, Object>> activity() {
        synchronized (activity) {
            return List.copyOf(activity);
        }
    }

    /** @return whether verification-only mode rejected requested work */
    public boolean workBlocked() {
        return workBlocked.get();
    }

    /** @return model-visible budget instructions shared by the parent and all workers */
    public String instructions() {
        return "Shared run budget: " + calls() + "/" + LIMIT + " callbacks used. "
                + (calls() >= WRAP_UP_AT
                        ? "WRAP UP NOW. No new writes or delegations. Use remaining reads to verify the requested batch, then complete. Report unresolved work honestly. "
                        : "At " + WRAP_UP_AT + " callbacks, switch to verification and completion. Do not start unrelated work. ")
                + "Reads and failed requests also consume budget. Leave the final callback for completion.";
    }

    private String response(String result) {
        try {
            return JSON.writeValueAsString(Map.of("result", result, "atlasBudget", instructions()));
        }
        catch (JacksonException ex) {
            throw new IllegalStateException("Cannot encode tool budget notice", ex);
        }
    }

    private static boolean readOnly(String name) {
        return name.equals("getCompetencyDetails") || name.equals("getExerciseContent") || name.equals("listCompetencyIndex") || name.equals("searchLectureContent");
    }

    private static boolean terminal(String name) {
        return name.equals("completeOrchestration") || name.equals("completeWorkerTask");
    }

    /**
     * Reuses extracted learning content only for the lifetime of this autonomous invocation.
     *
     * @param context current tool context, if any
     * @param key     content kind and entity identifier
     * @param extract extraction to perform on the first read
     * @return extracted learning content
     */
    public static ExtractedContentDTO content(ToolContext context, String key, Supplier<ExtractedContentDTO> extract) {
        AtlasToolCallBudget budget = context == null ? null : existingBudget(context.getContext());
        return budget == null ? extract.get() : budget.contentSnapshots.computeIfAbsent(key, ignored -> extract.get());
    }

    private static String argumentHash(String arguments) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(arguments.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Rejects a success claim after requested work was blocked, retaining the model's wrap-up.
     *
     * @param response final model response
     * @param context  shared invocation context
     */
    public static void checkResponse(@Nullable ChatResponse response, Map<String, Object> context) {
        AtlasToolCallBudget budget = existingBudget(context);
        if (budget != null && (budget.exhausted() || budget.workBlocked())) {
            String summary = budget.completion() != null ? budget.completion().message()
                    : response != null && response.getResult() != null ? response.getResult().getOutput().getText() : null;
            throw new LimitReachedException(summary);
        }
        checkResponse(response);
    }

    private final AtomicInteger calls = new AtomicInteger();

    private final AtomicBoolean exhausted = new AtomicBoolean();

    /**
     * Seeds the top-level context before dispatch. Worker contexts inherit this same object.
     *
     * @param context mutable context owned by the calling round
     * @return the budget shared by the entire autonomous invocation
     */
    public static AtlasToolCallBudget budgetForContext(@NonNull Map<String, Object> context) {
        AtlasToolCallBudget budget = existingBudget(context);
        if (budget == null) {
            budget = new AtlasToolCallBudget();
            context.put(CONTEXT_KEY, budget);
        }
        return budget;
    }

    /**
     * Returns the existing context budget without creating a replacement. This is the fail-closed
     * lookup used when constructing a worker context.
     *
     * @param context mutable tool context map
     * @return the stored budget, or {@code null} when the context is not budgeted
     */
    @Nullable
    public static AtlasToolCallBudget existingBudget(@NonNull Map<String, Object> context) {
        Object value = context.get(CONTEXT_KEY);
        if (value == null) {
            return null;
        }
        if (value instanceof AtlasToolCallBudget budget) {
            return budget;
        }
        throw new IllegalStateException("Atlas tool context contains an invalid tool-call budget.");
    }

    /** @return number of attempted callbacks reserved so far, including failed callbacks */
    public int calls() {
        return calls.get();
    }

    /** @return whether a callback attempted to run after the shared cap was consumed */
    public boolean exhausted() {
        return exhausted.get();
    }

    /**
     * Wraps every callback exposed by a provider while forwarding its definition and metadata
     * unchanged. The wrapper also converts exhaustion caused by a nested worker into a native
     * Spring AI limit exception after the delegated callback returns or throws.
     *
     * @param provider callback provider exposed to one autonomous round
     * @param budget   shared run budget
     * @return provider with callbacks guarded by {@code budget}
     */
    public static ToolCallbackProvider decorate(@NonNull ToolCallbackProvider provider, @NonNull AtlasToolCallBudget budget) {
        ToolCallback[] callbacks = provider.getToolCallbacks();
        return ToolCallbackProvider.from(Arrays.stream(callbacks).map(callback -> new BudgetedToolCallback(callback, budget)).toList());
    }

    /**
     * Throws a stable application exception when a native advisor returned the limit finish reason.
     * Usage is tracked by the caller before invoking this method.
     *
     * @param response response returned by Spring AI's tool-calling advisor
     * @throws LimitReachedException when the response stopped at the shared tool-call limit
     */
    public static void checkResponse(@Nullable ChatResponse response) {
        if (response == null || response.getResults() == null) {
            return;
        }
        boolean reached = response.getResults().stream().anyMatch(generation -> generation != null && generation.getMetadata() != null
                && ToolCallLimitExceededException.FINISH_REASON.equals(generation.getMetadata().getFinishReason()));
        if (reached) {
            throw new LimitReachedException();
        }
    }

    // Let the native advisor synthesize its terminal response with accumulated usage. No further
    // model round is sent, so the partial conversation is not replayed to a provider.
    private static ToolCallLimitExceededException limitException(String toolName) {
        ToolExecutionResult partialResult = ToolExecutionResult.builder().conversationHistory(List.<Message>of()).build();
        return new ToolCallLimitExceededException(toolName, LIMIT, partialResult);
    }

    /** Stable application error used to stop the parent orchestration after usage accounting. */
    public static class LimitReachedException extends RuntimeException {

        private final String summary;

        public LimitReachedException() {
            this(null);
        }

        public LimitReachedException(@Nullable String summary) {
            super("TOOL_CALL_LIMIT_EXCEEDED");
            this.summary = summary == null || summary.isBlank()
                    ? "Atlas reached its tool budget. Applied changes are retained; remaining work or verification is incomplete. No automatic replay was started."
                    : "Atlas finished with incomplete work because its tool budget was reached. " + summary;
        }

        /** @return model wrap-up, or a deterministic fallback when no final model turn remained */
        public String summary() {
            return summary;
        }
    }

    private static final class BudgetedToolCallback implements ToolCallback {

        private final ToolCallback delegate;

        private final AtlasToolCallBudget budget;

        private BudgetedToolCallback(@NonNull ToolCallback delegate, @NonNull AtlasToolCallBudget budget) {
            this.delegate = delegate;
            this.budget = budget;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String arguments) {
            return invoke(arguments, null);
        }

        @Override
        public String call(String arguments, @Nullable ToolContext context) {
            return invoke(arguments, context);
        }

        private String invoke(String arguments, @Nullable ToolContext context) {
            String toolName = delegate.getToolDefinition().name();
            boolean completion = terminal(toolName);
            if (!budget.reserve(toolName.equals("completeOrchestration"))) {
                throw limitException(toolName);
            }
            String outcome = "completed";
            boolean work = !readOnly(toolName) && !completion;
            long version = budget.begin(work);
            boolean verifiedIndex = false;
            try {
                if (budget.calls() > WRAP_UP_AT && !readOnly(toolName) && !completion) {
                    budget.workBlocked.set(true);
                    outcome = "blocked_during_wrap_up";
                    return budget.response("NOT EXECUTED: work budget reached. Finish the current worker, refresh relevant state, and complete with unresolved work reported.");
                }
                String result = delegate.call(arguments, context);
                if (toolName.equals("listCompetencyIndex") && result != null) {
                    var index = JSON.readTree(result);
                    verifiedIndex = index.isObject() && !index.has("error");
                }
                if (budget.exhausted()) {
                    throw limitException(toolName);
                }
                return budget.response(result);
            }
            catch (RuntimeException exception) {
                outcome = "exception";
                if (budget.exhausted() && !(exception instanceof ToolCallLimitExceededException)) {
                    throw limitException(toolName);
                }
                if (toolName.equals("completeOrchestration")) {
                    // returnDirect ends this round even when verification was rejected. The caller
                    // records the accumulated usage and reports missing completion without replay.
                    return budget.response("COMPLETION REJECTED: " + exception.getMessage());
                }
                throw exception;
            }
            finally {
                budget.finish(work, version, verifiedIndex);
                budget.activity.add(Map.of("tool", toolName, "argumentsSha256", argumentHash(arguments), "outcome", outcome, "role",
                        context != null && Boolean.TRUE.equals(context.getContext().get("atlasBudgetWorker")) ? "worker" : "orchestrator"));
            }
        }
    }

    private boolean reserve(boolean completion) {
        while (true) {
            int current = calls.get();
            if (current >= LIMIT || (current == LIMIT - 1 && !completion)) {
                exhausted.set(true);
                return false;
            }
            if (calls.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }
}
