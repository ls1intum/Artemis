package de.tum.cit.aet.artemis.core.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.config.LlmUsageProperties;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Helper encapsulating model pricing, normalization and token usage persistence for LLM calls.
 */
@Component
@Lazy
@ConditionalOnProperty(name = Constants.HYPERION_ENABLED_PROPERTY_NAME, havingValue = "true", matchIfMissing = false)
public class LlmUsageHelper {

    private static final Logger log = LoggerFactory.getLogger(LlmUsageHelper.class);

    private static final LlmUsageProperties.ModelCost ZERO_COST = new LlmUsageProperties.ModelCost(0f, 0f);

    private final LLMTokenUsageService llmTokenUsageService;

    private final UserRepository userRepository;

    private final Map<String, LlmUsageProperties.ModelCost> costs;

    private final Map<String, LlmUsageProperties.ModelCostUsd> costsUsd;

    public LlmUsageHelper(LLMTokenUsageService llmTokenUsageService, UserRepository userRepository, LlmUsageProperties llmUsageProperties) {
        this.llmTokenUsageService = llmTokenUsageService;
        this.userRepository = userRepository;
        this.costs = llmUsageProperties.getCosts();
        this.costsUsd = llmUsageProperties.getCostsUsd();
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

        var usage = chatResponse.getMetadata().getUsage();
        log.info("LLM {} usage: prompt={}, completion={}, total={}", checkType, usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());

        int promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        int completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
        String model = chatResponse.getMetadata().getModel();
        String normalizedModel = normalizeModelName(model);
        LlmUsageProperties.ModelCost modelCost = costs.getOrDefault(normalizedModel, ZERO_COST);

        double estimatedCost = (promptTokens * modelCost.costPerMillionInput() / 1_000_000.0) + (completionTokens * modelCost.costPerMillionOutput() / 1_000_000.0);
        log.info("LLM {} estimated cost for model {}: {} € (input {} @ {}/M, output {} @ {}/M)", checkType, normalizedModel, String.format("%.4f", estimatedCost), promptTokens,
                modelCost.costPerMillionInput(), completionTokens, modelCost.costPerMillionOutput());

        return new LLMRequest(model, promptTokens, modelCost.costPerMillionInput(), completionTokens, modelCost.costPerMillionOutput(), pipelineId);
    }

    /**
     * Persist token usage using exercise context.
     *
     * @param exercise    the exercise context
     * @param llmRequests one or more optional LLM requests to store
     */
    public void storeTokenUsage(ProgrammingExercise exercise, LLMRequest... llmRequests) {
        if (llmRequests == null) {
            return;
        }
        List<LLMRequest> requests = Arrays.stream(llmRequests).filter(Objects::nonNull).toList();
        if (requests.isEmpty()) {
            return;
        }
        Long courseId = exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;
        Long userId = SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findIdByLogin).orElse(null);

        llmTokenUsageService.saveLLMTokenUsage(requests, LLMServiceType.HYPERION, builder -> builder.withCourse(courseId).withExercise(exercise.getId()).withUser(userId));
    }

    /**
     * Persist token usage using course context (when no exercise is available).
     *
     * @param course      the course context
     * @param llmRequests one or more optional LLM requests to store
     */
    public void storeTokenUsage(Course course, LLMRequest... llmRequests) {
        if (llmRequests == null) {
            return;
        }
        List<LLMRequest> requests = java.util.Arrays.stream(llmRequests).filter(Objects::nonNull).toList();
        if (requests.isEmpty()) {
            return;
        }
        Long courseId = course != null ? course.getId() : null;
        Long userId = SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findIdByLogin).orElse(null);

        llmTokenUsageService.saveLLMTokenUsage(requests, LLMServiceType.HYPERION, builder -> builder.withCourse(courseId).withUser(userId));
    }

    public String normalizeModelName(String rawModel) {
        if (rawModel == null) {
            return "";
        }
        // Strip trailing date or version suffix like "-2025-08-07"
        int dateIndex = rawModel.indexOf("-20");
        return dateIndex > 0 ? rawModel.substring(0, dateIndex) : rawModel;
    }

    public Map<String, LlmUsageProperties.ModelCost> getCosts() {
        return costs;
    }

    public Map<String, LlmUsageProperties.ModelCostUsd> getCostsUsd() {
        return costsUsd;
    }
}
