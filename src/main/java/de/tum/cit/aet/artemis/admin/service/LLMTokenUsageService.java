package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.domain.LLMTokenUsageRequest;
import de.tum.cit.aet.artemis.admin.domain.LLMTokenUsageTrace;
import de.tum.cit.aet.artemis.admin.repository.LLMTokenUsageRequestRepository;
import de.tum.cit.aet.artemis.admin.repository.LLMTokenUsageTraceRepository;
import de.tum.cit.aet.artemis.aiworker.api.LLMResponseCost;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;

/**
 * Service for managing the LLMTokenUsage by all LLMs in Artemis.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class LLMTokenUsageService {

    private static final Logger log = LoggerFactory.getLogger(LLMTokenUsageService.class);

    private final LLMTokenUsageTraceRepository llmTokenUsageTraceRepository;

    private final LLMTokenUsageRequestRepository llmTokenUsageRequestRepository;

    private final LlmProperties llmProperties;

    public LLMTokenUsageService(LLMTokenUsageTraceRepository llmTokenUsageTraceRepository, LLMTokenUsageRequestRepository llmTokenUsageRequestRepository,
            LlmProperties llmProperties) {
        this.llmTokenUsageTraceRepository = llmTokenUsageTraceRepository;
        this.llmTokenUsageRequestRepository = llmTokenUsageRequestRepository;
        this.llmProperties = llmProperties;
    }

    /**
     * Builds accounting from the full response so that the provider's USD amount takes precedence over configured prices.
     *
     * @param response   response for one model call, or accumulated usage
     * @param pipelineId the operation that made the call
     * @return usage with a known USD amount when supplied or estimable; null when token counts are missing
     */
    @Nullable
    public LLMRequest buildLLMRequest(ChatResponse response, String pipelineId) {
        Usage usage = response.getMetadata().getUsage();
        if (usage instanceof org.springframework.ai.chat.metadata.EmptyUsage) {
            return null;
        }
        TokenCounts counts = new TokenCounts(usage);
        Integer input = counts.input();
        Integer output = counts.output();
        if (input == null || output == null) {
            log.warn("Failed to store token usage for pipeline [{}]: usage metadata is incomplete (prompt tokens: {}, completion tokens: {}).", pipelineId, input, output);
            return null;
        }
        String model = Objects.requireNonNullElse(response.getMetadata().getModel(), "");
        Double usd = LLMResponseCost.reportedUsd(usage.getNativeUsage(), usage.getPromptTokens(), usage.getCompletionTokens());
        int cached = safeCacheCount(usage.getCacheReadInputTokens(), input);
        int written = safeCacheCount(usage.getCacheWriteInputTokens(), Math.max(0, input - cached));
        if (usd == null) {
            ModelCost cost = modelCost(LlmClientService.presetOf(response.getMetadata()), model, input, output, pipelineId);
            usd = estimateUsd(cost, input, output, cached, written);
        }
        return new LLMRequest(model, input, output, pipelineId, cached, written, usd);
    }

    /**
     * The fallback price of a call is the price list entry of the preset that made it: the entry on the preset's endpoint for the preset's configured
     * model. A call without a preset (for example from a test double) or a preset without a price stays unpriced.
     */
    private ModelCost modelCost(@Nullable String presetName, String model, int inputTokens, int outputTokens, String pipelineId) {
        LlmProperties.Preset preset = presetName == null ? null : llmProperties.preset(presetName);
        LlmProperties.Price price = preset == null ? null : llmProperties.priceOf(preset);
        if (price == null) {
            if ((long) inputTokens + outputTokens > 0) {
                log.debug("No fallback price for the call of model '{}' on pipeline [{}] (preset {}); recording an unknown cost", model, pipelineId, presetName);
            }
            return ModelCost.UNKNOWN;
        }
        return new ModelCost(price.input(), price.output(), price.cachedInput(), price.cacheWrite());
    }

    private static int safeCacheCount(@Nullable Long count, int input) {
        return count != null && count >= 0 && count <= input ? count.intValue() : 0;
    }

    @Nullable
    private static Double estimateUsd(ModelCost cost, int input, int output, int cached, int written) {
        boolean usesRegularInputRate = requiresRegularInputRate(cost, input, cached, written);
        if (input < 0 || output < 0 || usesRegularInputRate && !validRate(cost.inputUsd()) || output > 0 && !validRate(cost.outputUsd())
                || invalidCacheRate(cached, cost.cachedUsd()) || invalidCacheRate(written, cost.writeUsd())) {
            return null;
        }
        // The shared fallback remains a simple estimate: use the regular input rate where no separate cache rate is configured.
        double inputRate = validRate(cost.inputUsd()) ? cost.inputUsd() : 0;
        double outputRate = validRate(cost.outputUsd()) ? cost.outputUsd() : 0;
        double cachedRate = validRate(cost.cachedUsd()) ? cost.cachedUsd() : inputRate;
        double writeRate = validRate(cost.writeUsd()) ? cost.writeUsd() : inputRate;
        double amount = ((input - cached - written) * inputRate + cached * cachedRate + written * writeRate + output * outputRate) / 1_000_000d;
        return Double.isFinite(amount) && amount >= 0 ? amount : null;
    }

    private static boolean validRate(@Nullable Double rate) {
        return rate != null && Double.isFinite(rate) && rate >= 0;
    }

    private static boolean requiresRegularInputRate(ModelCost cost, int input, int cached, int written) {
        return input > cached + written || cached > 0 && cost.cachedUsd() == null || written > 0 && cost.writeUsd() == null;
    }

    private static boolean invalidCacheRate(int tokens, @Nullable Double rate) {
        return tokens > 0 && rate != null && !validRate(rate);
    }

    // Preserve missing adapter counters even though Spring AI declares its getters non-null.
    private record TokenCounts(@Nullable Integer input, @Nullable Integer output) {

        private TokenCounts(Usage usage) {
            this(usage.getPromptTokens(), usage.getCompletionTokens());
        }
    }

    private record ModelCost(@Nullable Double inputUsd, @Nullable Double outputUsd, @Nullable Double cachedUsd, @Nullable Double writeUsd) {

        static final ModelCost UNKNOWN = new ModelCost(null, null, null, null);
    }

    /**
     * Records one received provider response, retaining an unpriced row when that call omitted usage.
     * Call this before a tool loop accumulates usage, so its native provider amount remains available.
     *
     * @param response        individual model response, never accumulated usage
     * @param serviceType     service that made the call
     * @param pipelineId      pipeline identifier
     * @param builderFunction configures the trace scope
     */
    public void trackModelCallTokenUsage(ChatResponse response, LLMServiceType serviceType, String pipelineId, UnaryOperator<LLMTokenUsageBuilder> builderFunction) {
        try {
            LLMRequest request = buildLLMRequest(response, pipelineId);
            if (request == null) {
                TokenCounts counts = new TokenCounts(response.getMetadata().getUsage());
                int input = counts.input() == null ? 0 : Math.max(0, counts.input());
                int output = counts.output() == null ? 0 : Math.max(0, counts.output());
                request = new LLMRequest(Objects.requireNonNullElse(response.getMetadata().getModel(), ""), input, output, pipelineId);
            }
            saveLLMTokenUsage(List.of(request), serviceType, builderFunction);
        }
        catch (Exception ex) {
            log.warn("Failed to store model-call usage for pipeline [{}]", pipelineId, ex);
        }
    }

    /**
     * Saves the token usage to the database.
     * This method records the usage of tokens by various LLM services in the system.
     *
     * @param llmRequests     List of LLM requests containing details about the token usage.
     * @param serviceType     Type of the LLM service (e.g., IRIS, GPT-3).
     * @param builderFunction A function that takes an LLMTokenUsageBuilder and returns a modified LLMTokenUsageBuilder.
     *                            This function is used to set additional properties on the LLMTokenUsageTrace object, such as
     *                            the course ID, user ID, exercise ID, and Iris message ID.
     *                            Example usage:
     *                            builder -> builder.withCourse(courseId).withUser(userId)
     * @return The saved LLMTokenUsageTrace object, which includes the details of the token usage.
     */
    // TODO: this should ideally be done Async
    public LLMTokenUsageTrace saveLLMTokenUsage(List<LLMRequest> llmRequests, LLMServiceType serviceType, Function<LLMTokenUsageBuilder, LLMTokenUsageBuilder> builderFunction) {
        LLMTokenUsageTrace llmTokenUsageTrace = new LLMTokenUsageTrace();
        llmTokenUsageTrace.setServiceType(serviceType);

        LLMTokenUsageBuilder builder = builderFunction.apply(new LLMTokenUsageBuilder());
        builder.getIrisMessageID().ifPresent(llmTokenUsageTrace::setIrisMessageId);
        builder.getCourseID().ifPresent(llmTokenUsageTrace::setCourseId);
        builder.getExerciseID().ifPresent(llmTokenUsageTrace::setExerciseId);
        builder.getUserID().ifPresent(llmTokenUsageTrace::setUserId);

        llmTokenUsageTrace.setLlmRequests(llmRequests.stream().map(LLMTokenUsageService::convertLLMRequestToLLMTokenUsageRequest)
                .peek(llmTokenUsageRequest -> llmTokenUsageRequest.setTrace(llmTokenUsageTrace)).collect(Collectors.toSet()));

        return llmTokenUsageTraceRepository.save(llmTokenUsageTrace);
    }

    private static LLMTokenUsageRequest convertLLMRequestToLLMTokenUsageRequest(LLMRequest llmRequest) {
        LLMTokenUsageRequest llmTokenUsageRequest = new LLMTokenUsageRequest();
        llmTokenUsageRequest.setModel(llmRequest.model());
        llmTokenUsageRequest.setNumInputTokens(llmRequest.numInputTokens());
        llmTokenUsageRequest.setNumOutputTokens(llmRequest.numOutputTokens());
        llmTokenUsageRequest.setNumCachedInputTokens(llmRequest.numCachedInputTokens());
        llmTokenUsageRequest.setNumCacheWriteInputTokens(llmRequest.numCacheWriteInputTokens());
        llmTokenUsageRequest.setServicePipelineId(llmRequest.pipelineId());
        llmTokenUsageRequest.setTotalCostUsd(llmRequest.totalCostUsd());
        return llmTokenUsageRequest;
    }

    // TODO: this should ideally be done Async
    public void appendRequestsToTrace(List<LLMRequest> requests, LLMTokenUsageTrace trace) {
        var requestSet = requests.stream().map(LLMTokenUsageService::convertLLMRequestToLLMTokenUsageRequest).peek(llmTokenUsageRequest -> llmTokenUsageRequest.setTrace(trace))
                .collect(Collectors.toSet());
        llmTokenUsageRequestRepository.saveAll(requestSet);
    }

    /**
     * Extracts the response text from a {@link ChatResponse}, handling null safety throughout the chain.
     *
     * @param chatResponse the chat response from the AI model, may be null
     * @return the extracted text, or null if any part of the response chain is null
     */
    @Nullable
    public static String extractResponseText(@Nullable ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            return null;
        }
        return chatResponse.getResult().getOutput().getText();
    }

    /**
     * Convenience method to track token usage from a {@link ChatResponse}.
     * Extracts metadata (model, prompt/completion tokens) from the response, builds an {@link LLMRequest},
     * and persists it. Catches all exceptions so that tracking failures never affect the main operation.
     *
     * @param chatResponse    the chat response containing usage metadata, may be null
     * @param serviceType     the LLM service type (e.g. HYPERION, IRIS)
     * @param pipelineId      the pipeline identifier for this request
     * @param builderFunction configures the trace (course, user, exercise, etc.)
     */
    public void trackChatResponseTokenUsage(@Nullable ChatResponse chatResponse, LLMServiceType serviceType, String pipelineId,
            Function<LLMTokenUsageBuilder, LLMTokenUsageBuilder> builderFunction) {
        try {
            if (chatResponse == null) {
                log.warn("Failed to store token usage for pipeline [{}]: chat response is missing.", pipelineId);
                return;
            }
            LLMRequest llmRequest = buildLLMRequest(chatResponse, pipelineId);
            if (llmRequest != null) {
                saveLLMTokenUsage(List.of(llmRequest), serviceType, builderFunction);
            }
        }
        catch (Exception e) {
            log.warn("Failed to store token usage for pipeline [{}]: {}", pipelineId, e.getMessage(), e);
        }
    }

    /**
     * Finds an LLMTokenUsageTrace by its ID.
     *
     * @param id The ID of the LLMTokenUsageTrace to find.
     * @return An Optional containing the LLMTokenUsageTrace if found, or an empty Optional otherwise.
     */
    public Optional<LLMTokenUsageTrace> findLLMTokenUsageTraceById(Long id) {
        return llmTokenUsageTraceRepository.findById(id);
    }

    /**
     * Class LLMTokenUsageBuilder to be used for saveLLMTokenUsage()
     */
    public static class LLMTokenUsageBuilder {

        private Optional<Long> courseID = Optional.empty();

        private Optional<Long> irisMessageID = Optional.empty();

        private Optional<Long> exerciseID = Optional.empty();

        private Optional<Long> userID = Optional.empty();

        public LLMTokenUsageBuilder withCourse(Long courseID) {
            this.courseID = Optional.ofNullable(courseID);
            return this;
        }

        public LLMTokenUsageBuilder withIrisMessageID(Long irisMessageID) {
            this.irisMessageID = Optional.ofNullable(irisMessageID);
            return this;
        }

        public LLMTokenUsageBuilder withExercise(Long exerciseID) {
            this.exerciseID = Optional.ofNullable(exerciseID);
            return this;
        }

        public LLMTokenUsageBuilder withUser(Long userID) {
            this.userID = Optional.ofNullable(userID);
            return this;
        }

        public Optional<Long> getCourseID() {
            return courseID;
        }

        public Optional<Long> getIrisMessageID() {
            return irisMessageID;
        }

        public Optional<Long> getExerciseID() {
            return exerciseID;
        }

        public Optional<Long> getUserID() {
            return userID;
        }
    }
}
