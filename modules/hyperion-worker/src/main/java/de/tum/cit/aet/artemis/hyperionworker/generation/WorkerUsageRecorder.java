package de.tum.cit.aet.artemis.hyperionworker.generation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.ai.chat.model.ChatResponse;

import de.tum.cit.aet.artemis.hyperion.protocol.GenerationOutput.AccountingState;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationUsage;
import de.tum.cit.aet.artemis.hyperion.protocol.ProviderUsageUpdate;
import de.tum.cit.aet.artemis.hyperion.protocol.ProviderUsageUpdate.Kind;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.ProviderUsageSink;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.ProviderUsageTransport;

/** One execution's provider evidence and local spend guard. Prices and persistence belong to core. */
public final class WorkerUsageRecorder implements ProviderUsageSink {

    private final Consumer<ProviderUsageUpdate> events;

    private final long tokenLimit;

    private final double cachedInputWeight;

    private final Set<String> models = new LinkedHashSet<>();

    private final Set<String> requestIds = new LinkedHashSet<>();

    private long modelCalls;

    private long toolCalls;

    private long turns;

    private long attempts;

    private long inputTokens;

    private long outputTokens;

    private long cachedTokens;

    private long billableTokens;

    private boolean cachedTokensComplete = true;

    private boolean requestIdsComplete = true;

    private boolean uncertain;

    public WorkerUsageRecorder(long tokenLimit, double cachedInputWeight, boolean retriesDisabled, Consumer<ProviderUsageUpdate> events) {
        this.tokenLimit = tokenLimit;
        this.cachedInputWeight = cachedInputWeight;
        this.events = events;
        if (!retriesDisabled) {
            markUncertain();
        }
    }

    @Override
    public synchronized void accept(ChatResponse response) {
        ProviderUsageUpdate.Call call;
        try {
            call = ProviderUsageTransport.capture(response);
        }
        catch (IllegalArgumentException missingUsage) {
            markUncertain();
            return;
        }
        modelCalls++;
        inputTokens = Math.addExact(inputTokens, call.inputTokens());
        outputTokens = Math.addExact(outputTokens, call.outputTokens());
        long cached = call.cachedInputTokens() == null ? 0 : call.cachedInputTokens();
        cachedTokens = Math.addExact(cachedTokens, cached);
        cachedTokensComplete &= call.cachedInputTokens() != null;
        billableTokens = Math.addExact(billableTokens, Math.round((call.inputTokens() - cached) + cached * cachedInputWeight) + call.outputTokens());
        if (!call.model().isBlank()) {
            models.add(call.model());
        }
        boolean hasRequestId = call.requestId() != null && !call.requestId().isBlank();
        requestIdsComplete &= hasRequestId;
        if (hasRequestId) {
            requestIds.add(call.requestId());
        }
        emit(new ProviderUsageUpdate(Kind.RESPONSE, 1, call));
    }

    @Override
    public synchronized void recordToolCalls(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("Tool count cannot be negative");
        }
        toolCalls = Math.addExact(toolCalls, count);
        emit(new ProviderUsageUpdate(Kind.TOOL_CALLS, count, null));
    }

    @Override
    public synchronized void recordTurn() {
        turns++;
        emit(new ProviderUsageUpdate(Kind.TURN, 1, null));
    }

    @Override
    public synchronized void recordAttempt() {
        attempts++;
        emit(new ProviderUsageUpdate(Kind.ATTEMPT, 1, null));
    }

    @Override
    public synchronized void markUncertain() {
        if (!uncertain) {
            uncertain = true;
            emit(new ProviderUsageUpdate(Kind.UNCERTAIN, 0, null));
        }
    }

    private void emit(ProviderUsageUpdate event) {
        try {
            events.accept(event);
        }
        catch (RuntimeException deliveryFailure) {
            uncertain = true;
            throw deliveryFailure;
        }
    }

    public synchronized boolean budgetExhausted() {
        return billableTokens >= tokenLimit;
    }

    public synchronized AccountingState accountingState() {
        return uncertain ? AccountingState.INCOMPLETE : AccountingState.COMPLETE;
    }

    public synchronized GenerationUsage snapshot() {
        return new GenerationUsage(modelCalls, toolCalls, turns, attempts, inputTokens, outputTokens, cachedTokens, cachedTokensComplete, 0, modelCalls == 0, List.copyOf(models),
                List.copyOf(requestIds), requestIdsComplete);
    }
}
