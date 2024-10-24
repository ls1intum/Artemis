package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.domain.LLMTokenUsageRequest;
import de.tum.cit.aet.artemis.core.domain.LLMTokenUsageTrace;
import de.tum.cit.aet.artemis.core.repository.LLMTokenUsageRequestRepository;
import de.tum.cit.aet.artemis.core.repository.LLMTokenUsageTraceRepository;

/**
 * Service for managing the LLMTokenUsage by all LLMs in Artemis
 */
@Profile(PROFILE_CORE)
@Service
public class LLMTokenUsageService {

    private final LLMTokenUsageTraceRepository llmTokenUsageTraceRepository;

    private final LLMTokenUsageRequestRepository llmTokenUsageRequestRepository;

    public LLMTokenUsageService(LLMTokenUsageTraceRepository llmTokenUsageTraceRepository, LLMTokenUsageRequestRepository llmTokenUsageRequestRepository) {
        this.llmTokenUsageTraceRepository = llmTokenUsageTraceRepository;
        this.llmTokenUsageRequestRepository = llmTokenUsageRequestRepository;
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
        llmTokenUsageRequest.setCostPerMillionInputTokens(llmRequest.costPerMillionInputToken());
        llmTokenUsageRequest.setCostPerMillionOutputTokens(llmRequest.costPerMillionOutputToken());
        llmTokenUsageRequest.setServicePipelineId(llmRequest.pipelineId());
        return llmTokenUsageRequest;
    }

    // TODO: this should ideally be done Async
    public void appendRequestsToTrace(List<LLMRequest> requests, LLMTokenUsageTrace trace) {
        var requestSet = requests.stream().map(LLMTokenUsageService::convertLLMRequestToLLMTokenUsageRequest).peek(llmTokenUsageRequest -> llmTokenUsageRequest.setTrace(trace))
                .collect(Collectors.toSet());
        llmTokenUsageRequestRepository.saveAll(requestSet);
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
