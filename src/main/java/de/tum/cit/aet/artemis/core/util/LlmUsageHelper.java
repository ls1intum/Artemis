package de.tum.cit.aet.artemis.core.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.LlmModelCostConfiguration;
import de.tum.cit.aet.artemis.core.config.SpringAIConfiguration;
import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Helper encapsulating model pricing, normalization and token usage persistence for LLM calls.
 * Shared by all Spring AI-based services (Hyperion, Atlas, etc.).
 */
@Component
@Lazy
@Conditional(SpringAIConfiguration.SpringAIEnabled.class)
public class LlmUsageHelper {

    private static final Logger log = LoggerFactory.getLogger(LlmUsageHelper.class);

    /**
     * Pattern to match date suffixes like "-2024-01-15" or "-2025-08-07" at the end of model names.
     */
    private static final Pattern DATE_SUFFIX_PATTERN = Pattern.compile("-\\d{4}-\\d{2}-\\d{2}$");

    private static final ModelCost ZERO_COST = new ModelCost(0f, 0f);

    private final LLMTokenUsageService llmTokenUsageService;

    private final UserRepository userRepository;

    private final Map<String, ModelCost> costs;

    public LlmUsageHelper(LLMTokenUsageService llmTokenUsageService, UserRepository userRepository, LlmModelCostConfiguration costConfiguration) {
        this.llmTokenUsageService = llmTokenUsageService;
        this.userRepository = userRepository;

        var modelCosts = costConfiguration.getModelCosts();
        this.costs = modelCosts.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> new ModelCost(e.getValue().getInputCostPerMillionEur(), e.getValue().getOutputCostPerMillionEur())));
    }

    /**
     * Build an LLMRequest from the chat response metadata, including model normalization and cost lookup.
     *
     * @param chatResponse response from Spring AI
     * @param checkType    label for logging context
     * @param pipelineId   pipeline identifier for the usage record
     * @return LLMRequest or null when usage metadata is absent
     */
    public LLMRequest buildLlmRequest(ChatResponse chatResponse, String checkType, String pipelineId) {
        if (chatResponse == null || chatResponse.getMetadata() == null || chatResponse.getMetadata().getUsage() == null) {
            log.info("LLM {} usage not available for this provider/response", checkType);
            return null;
        }

        Usage usage = chatResponse.getMetadata().getUsage();
        log.info("LLM {} usage: prompt={}, completion={}, total={}", checkType, usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());

        int promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        int completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
        String model = chatResponse.getMetadata().getModel();
        String normalizedModel = normalizeModelName(model);
        ModelCost modelCost = costs.getOrDefault(normalizedModel, ZERO_COST);

        double estimatedCost = (promptTokens * modelCost.costPerMillionInput() / 1_000_000.0) + (completionTokens * modelCost.costPerMillionOutput() / 1_000_000.0);
        log.info("LLM {} estimated cost for model {}: {} EUR (input {} @ {}/M, output {} @ {}/M)", checkType, normalizedModel, String.format("%.4f", estimatedCost), promptTokens,
                modelCost.costPerMillionInput(), completionTokens, modelCost.costPerMillionOutput());

        return new LLMRequest(model, promptTokens, modelCost.costPerMillionInput(), completionTokens, modelCost.costPerMillionOutput(), pipelineId);
    }

    /**
     * Persist token usage using exercise context.
     *
     * @param serviceType the LLM service type (HYPERION, ATLAS, etc.)
     * @param exercise    the exercise context
     * @param llmRequests one or more optional LLM requests to store
     */
    public void storeTokenUsage(LLMServiceType serviceType, ProgrammingExercise exercise, LLMRequest... llmRequests) {
        Long courseId = exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;
        storeTokenUsageInternal(serviceType, courseId, exercise.getId(), llmRequests);
    }

    /**
     * Persist token usage using course context (when no exercise is available).
     *
     * @param serviceType the LLM service type (HYPERION, ATLAS, etc.)
     * @param course      the course context
     * @param llmRequests one or more optional LLM requests to store
     */
    public void storeTokenUsage(LLMServiceType serviceType, de.tum.cit.aet.artemis.core.domain.Course course, LLMRequest... llmRequests) {
        Long courseId = course != null ? course.getId() : null;
        storeTokenUsageInternal(serviceType, courseId, null, llmRequests);
    }

    /**
     * Internal method to persist token usage with optional exercise context.
     *
     * @param serviceType the LLM service type
     * @param courseId    the course ID (nullable)
     * @param exerciseId  the exercise ID (nullable)
     * @param llmRequests one or more optional LLM requests to store
     */
    private void storeTokenUsageInternal(LLMServiceType serviceType, Long courseId, Long exerciseId, LLMRequest... llmRequests) {
        if (llmRequests == null) {
            return;
        }
        List<LLMRequest> requests = Arrays.stream(llmRequests).filter(Objects::nonNull).toList();
        if (requests.isEmpty()) {
            return;
        }
        Long userId = SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findIdByLogin).orElse(null);

        llmTokenUsageService.saveLLMTokenUsage(requests, serviceType, builder -> {
            builder.withCourse(courseId).withUser(userId);
            if (exerciseId != null) {
                builder.withExercise(exerciseId);
            }
            return builder;
        });
    }

    /**
     * Normalize provider model names by removing date/version suffixes.
     * Example: "gpt-5-mini-2024-07-18" becomes "gpt-5-mini".
     *
     * @param rawModel raw model identifier from the provider
     * @return normalized model name or empty string when undefined
     */
    public String normalizeModelName(String rawModel) {
        if (rawModel == null) {
            return "";
        }
        return DATE_SUFFIX_PATTERN.matcher(rawModel).replaceAll("");
    }

    private record ModelCost(float costPerMillionInput, float costPerMillionOutput) {
    }
}
