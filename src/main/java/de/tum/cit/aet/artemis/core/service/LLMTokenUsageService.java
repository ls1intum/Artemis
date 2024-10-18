package de.tum.cit.aet.artemis.core.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.domain.LLMTokenUsageRequest;
import de.tum.cit.aet.artemis.core.domain.LLMTokenUsageTrace;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.LLMTokenUsageTraceRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Service for managing the LLMTokenUsage by all LLMs in Artemis
 */
@Service
public class LLMTokenUsageService {

    private final LLMTokenUsageTraceRepository llmTokenUsageTraceRepository;

    public LLMTokenUsageService(LLMTokenUsageTraceRepository llmTokenUsageTraceRepository) {
        this.llmTokenUsageTraceRepository = llmTokenUsageTraceRepository;
    }

    /**
     * method saves the token usage to the database
     *
     * @param llmRequests     List of LLM requests
     * @param serviceType     type of the LLM service
     * @param builderFunction of type Function<IrisTokenUsageBuilder, IrisTokenUsageBuilder> using IrisTokenUsageBuilder
     * @return saved LLMTokenUsage as a List
     */
    public LLMTokenUsageTrace saveLLMTokenUsage(List<LLMRequest> llmRequests, LLMServiceType serviceType, Function<LLMTokenUsageBuilder, LLMTokenUsageBuilder> builderFunction) {
        LLMTokenUsageTrace llmTokenUsageTrace = new LLMTokenUsageTrace();
        llmTokenUsageTrace.setServiceType(serviceType);

        LLMTokenUsageBuilder builder = builderFunction.apply(new LLMTokenUsageBuilder());
        builder.getIrisMessageID().ifPresent(llmTokenUsageTrace::setIrisMessageId);
        builder.getUser().ifPresent(user -> {
            llmTokenUsageTrace.setUserId(user.getId());
        });
        builder.getExercise().ifPresent(exercise -> {
            llmTokenUsageTrace.setExerciseId(exercise.getId());
        });
        builder.getCourse().ifPresent(course -> {
            llmTokenUsageTrace.setCourseId(course.getId());
        });

        Set<LLMTokenUsageRequest> llmRequestsSet = llmTokenUsageTrace.getLLMRequests();
        for (LLMRequest llmRequest : llmRequests) {
            LLMTokenUsageRequest llmTokenUsageRequest = new LLMTokenUsageRequest();
            llmTokenUsageRequest.setModel(llmRequest.model());
            llmTokenUsageRequest.setNumInputTokens(llmRequest.numInputTokens());
            llmTokenUsageRequest.setNumOutputTokens(llmRequest.numOutputTokens());
            llmTokenUsageRequest.setCostPerMillionInputTokens(llmRequest.costPerMillionInputToken());
            llmTokenUsageRequest.setCostPerMillionOutputTokens(llmRequest.costPerMillionOutputToken());
            llmTokenUsageRequest.setServicePipelineId(llmRequest.pipelineId());
            llmTokenUsageRequest.setTrace(llmTokenUsageTrace);
            llmRequestsSet.add(llmTokenUsageRequest);
        }
        return llmTokenUsageTraceRepository.save(llmTokenUsageTrace);
    }

    /**
     * Class LLMTokenUsageBuilder to be used for saveLLMTokenUsage()
     */
    public static class LLMTokenUsageBuilder {

        private Optional<Course> course = Optional.empty();

        private Optional<Long> irisMessageID = Optional.empty();

        private Optional<Exercise> exercise = Optional.empty();

        private Optional<User> user = Optional.empty();

        public LLMTokenUsageBuilder withCourse(Course course) {
            this.course = Optional.ofNullable(course);
            return this;
        }

        public LLMTokenUsageBuilder withIrisMessageID(Long irisMessageID) {
            this.irisMessageID = Optional.ofNullable(irisMessageID);
            return this;
        }

        public LLMTokenUsageBuilder withExercise(Exercise exercise) {
            this.exercise = Optional.ofNullable(exercise);
            return this;
        }

        public LLMTokenUsageBuilder withUser(User user) {
            this.user = Optional.ofNullable(user);
            return this;
        }

        public Optional<Course> getCourse() {
            return course;
        }

        public Optional<Long> getIrisMessageID() {
            return irisMessageID;
        }

        public Optional<Exercise> getExercise() {
            return exercise;
        }

        public Optional<User> getUser() {
            return user;
        }
    }
}
