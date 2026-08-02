package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
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
import de.tum.cit.aet.artemis.core.config.LLMModelCostConfiguration;

/**
 * Service for managing the LLMTokenUsage by all LLMs in Artemis.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class LLMTokenUsageService {

    private static final Logger log = LoggerFactory.getLogger(LLMTokenUsageService.class);

    private static final Pattern DATE_SUFFIX_PATTERN = Pattern.compile("-?\\d{4}-\\d{2}-\\d{2}$");

    private final LLMTokenUsageTraceRepository llmTokenUsageTraceRepository;

    private final LLMTokenUsageRequestRepository llmTokenUsageRequestRepository;

    private final Map<String, ModelCost> costs;

    private final Map<String, ModelCost> costsByStrippedKey;

    public LLMTokenUsageService(LLMTokenUsageTraceRepository llmTokenUsageTraceRepository, LLMTokenUsageRequestRepository llmTokenUsageRequestRepository,
            LLMModelCostConfiguration costConfiguration) {
        this.llmTokenUsageTraceRepository = llmTokenUsageTraceRepository;
        this.llmTokenUsageRequestRepository = llmTokenUsageRequestRepository;
        this.costs = costConfiguration.getModelCosts().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e -> new ModelCost(e.getValue().getInputCostPerMillionEur(), e.getValue().getOutputCostPerMillionEur(), e.getValue().getCachedInputCostPerMillionEur())));
        this.costsByStrippedKey = costConfiguration.getModelCosts().entrySet().stream()
                .collect(Collectors.toMap(entry -> LLMModelCostConfiguration.stripToAlphanumeric(entry.getKey()),
                        entry -> new StrippedModelCost(entry.getKey(), LLMModelCostConfiguration.stripToAlphanumeric(entry.getKey()),
                                new ModelCost(entry.getValue().getInputCostPerMillionEur(), entry.getValue().getOutputCostPerMillionEur(),
                                        entry.getValue().getCachedInputCostPerMillionEur())),
                        LLMTokenUsageService::throwOnStrippedCostCollision))
                .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().cost()));
    }

    /**
     * Build an LLMRequest with automatic cost lookup from configuration.
     *
     * @param model        model identifier
     * @param inputTokens  number of input tokens
     * @param outputTokens number of output tokens
     * @param pipelineId   pipeline identifier
     * @return LLMRequest with costs from configuration
     */
    public LLMRequest buildLLMRequest(String model, int inputTokens, int outputTokens, String pipelineId) {
        return buildLLMRequest(model, inputTokens, outputTokens, pipelineId, null, null);
    }

    /**
     * Builds an LLM request with provider correlation and cache usage kept in memory while preserving the existing persistence schema.
     *
     * @param model             model identifier
     * @param inputTokens       number of input tokens
     * @param outputTokens      number of output tokens
     * @param pipelineId        pipeline identifier
     * @param providerRequestId provider response identifier, when available
     * @param cachedInputTokens cache-read input tokens, when reported
     * @return request with configured costs and transient provider metadata
     */
    public LLMRequest buildLLMRequest(String model, int inputTokens, int outputTokens, String pipelineId, @Nullable String providerRequestId, @Nullable Long cachedInputTokens) {
        String normalized = model != null ? DATE_SUFFIX_PATTERN.matcher(model).replaceAll("") : "";
        String stripped = LLMModelCostConfiguration.stripToAlphanumeric(normalized);
        ModelCost cost = costs.getOrDefault(normalized, costsByStrippedKey.getOrDefault(stripped, ModelCost.UNKNOWN));
        if (cost.equals(ModelCost.UNKNOWN) && inputTokens + outputTokens > 0) {
            log.warn("No LLM cost configured for model '{}' (normalized '{}', stripped '{}') on pipeline [{}]; recording an incomplete zero estimate. Known cost keys: {}", model,
                    normalized, stripped, pipelineId, costs.keySet());
        }
        boolean zeroTokenRequest = inputTokens + outputTokens == 0;
        boolean cachePriceResolved = cachedInputTokens != null ? cachedInputTokens == 0 || cost.cachedInput() != null
                : cost.input() != null && cost.cachedInput() != null && cost.input().equals(cost.cachedInput());
        boolean complete = zeroTokenRequest || (inputTokens == 0 || cost.input() != null && cachePriceResolved) && (outputTokens == 0 || cost.output() != null);
        return new LLMRequest(model, inputTokens, cost.inputOrZero(), outputTokens, cost.outputOrZero(), pipelineId, providerRequestId, cachedInputTokens, cost.cachedInputOrZero(),
                complete);
    }

    private record ModelCost(@Nullable Float input, @Nullable Float output, @Nullable Float cachedInput) {

        static final ModelCost UNKNOWN = new ModelCost(null, null, null);

        float inputOrZero() {
            return input == null ? 0f : input;
        }

        float outputOrZero() {
            return output == null ? 0f : output;
        }

        float cachedInputOrZero() {
            return cachedInput == null ? 0f : cachedInput;
        }
    }

    private record StrippedModelCost(String originalKey, String strippedKey, ModelCost cost) {
    }

    private static StrippedModelCost throwOnStrippedCostCollision(StrippedModelCost existing, StrippedModelCost replacement) {
        String message = new StringBuilder("Conflicting LLM model cost keys '").append(existing.originalKey()).append("' and '").append(replacement.originalKey())
                .append("' normalize to identical stripped key '").append(existing.strippedKey()).append("'").toString();
        throw new IllegalStateException(message);
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
        llmTokenUsageRequest.setCostPerMillionInputTokens(effectiveInputCost(llmRequest));
        llmTokenUsageRequest.setCostPerMillionOutputTokens(llmRequest.costPerMillionOutputToken());
        llmTokenUsageRequest.setServicePipelineId(llmRequest.pipelineId());
        return llmTokenUsageRequest;
    }

    /** Stores the exact blended input cost in the existing schema without adding cache-specific database columns. */
    private static float effectiveInputCost(LLMRequest request) {
        if (request.numCachedInputTokens() == null || request.numInputTokens() == 0) {
            return request.costPerMillionInputToken();
        }
        long cachedTokens = Math.min(request.numInputTokens(), request.numCachedInputTokens());
        long uncachedTokens = request.numInputTokens() - cachedTokens;
        return (uncachedTokens * request.costPerMillionInputToken() + cachedTokens * request.costPerMillionCachedInputToken()) / request.numInputTokens();
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
     * and persists it. Catches all exceptions and reports whether exact usage was recorded.
     *
     * @param chatResponse    the chat response containing usage metadata, may be null
     * @param serviceType     the LLM service type (e.g. HYPERION, IRIS)
     * @param pipelineId      the pipeline identifier for this request
     * @param builderFunction configures the trace (course, user, exercise, etc.)
     * @return {@code true} only when complete usage metadata was persisted
     */
    public boolean trackChatResponseTokenUsage(@Nullable ChatResponse chatResponse, LLMServiceType serviceType, String pipelineId,
            Function<LLMTokenUsageBuilder, LLMTokenUsageBuilder> builderFunction) {
        return trackChatResponseTokenUsage(chatResponse, serviceType, pipelineId, builderFunction, ignored -> {
        });
    }

    /**
     * Persists standard usage and exposes the richer in-memory record to transient observers such as the generation status replay.
     *
     * @param chatResponse      chat response containing usage metadata, may be null
     * @param serviceType       LLM service type
     * @param pipelineId        pipeline identifier
     * @param builderFunction   configures the persisted trace
     * @param recordedUsageSink receives the complete in-memory record after persistence
     * @return {@code true} only when complete usage metadata was persisted and observed
     */
    public boolean trackChatResponseTokenUsage(@Nullable ChatResponse chatResponse, LLMServiceType serviceType, String pipelineId,
            Function<LLMTokenUsageBuilder, LLMTokenUsageBuilder> builderFunction, Consumer<LLMRequest> recordedUsageSink) {
        try {
            if (chatResponse == null || chatResponse.getMetadata() == null || chatResponse.getMetadata().getUsage() == null) {
                return false;
            }
            ChatResponseMetadata metadata = chatResponse.getMetadata();
            Usage usage = metadata.getUsage();
            Integer promptTokens = usage.getPromptTokens();
            Integer completionTokens = usage.getCompletionTokens();
            Long cachedInputTokens = usage.getCacheReadInputTokens();
            if (promptTokens == null || completionTokens == null || promptTokens < 0 || completionTokens < 0
                    || cachedInputTokens != null && (cachedInputTokens < 0 || cachedInputTokens > promptTokens)) {
                return false;
            }
            String model = metadata.getModel() != null ? metadata.getModel() : "";
            LLMRequest llmRequest = buildLLMRequest(model, promptTokens, completionTokens, pipelineId, metadata.getId(), cachedInputTokens);
            saveLLMTokenUsage(List.of(llmRequest), serviceType, builderFunction);
            recordedUsageSink.accept(llmRequest);
            return true;
        }
        catch (Exception e) {
            log.warn("Failed to store token usage for pipeline [{}]: {}", pipelineId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * @param chatResponse the chat response containing provider usage metadata, may be null
     * @return prompt plus completion tokens, or zero when usage metadata is unavailable
     */
    public static long totalTokens(@Nullable ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null || chatResponse.getMetadata().getUsage() == null) {
            return 0;
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        Number promptTokens = usage.getPromptTokens();
        Number completionTokens = usage.getCompletionTokens();
        return (promptTokens == null ? 0 : promptTokens.longValue()) + (completionTokens == null ? 0 : completionTokens.longValue());
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
