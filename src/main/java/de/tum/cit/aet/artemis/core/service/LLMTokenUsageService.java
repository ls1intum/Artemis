package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.athena.dto.ResponseMetaDTO;
import de.tum.cit.aet.artemis.athena.dto.ResponseMetaLLMCallDTO;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.domain.LLMTokenUsage;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.LLMTokenUsageRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisLLMCostDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;

/**
 * Service for managing the LLMTokenUsage by all LLMs in Artemis
 */
@Service
@Profile(PROFILE_IRIS)
public class LLMTokenUsageService {

    private final LLMTokenUsageRepository llmTokenUsageRepository;

    public LLMTokenUsageService(LLMTokenUsageRepository llmTokenUsageRepository) {
        this.llmTokenUsageRepository = llmTokenUsageRepository;
    }

    /**
     * Method saves the token usage for the Athena response meta to the database
     *
     * @param courseId              of type Long
     * @param exerciseId            of type Long
     * @param userId                of type Long
     * @param meta                  of type ResponseMetaDTO
     * @param isPreliminaryFeedback of type Boolean
     */
    public void saveAthenaTokenUsage(Long courseId, Long exerciseId, Long userId, ResponseMetaDTO meta, Boolean isPreliminaryFeedback) {
        String traceId = UUID.randomUUID().toString();
        LLMServiceType serviceType = isPreliminaryFeedback ? LLMServiceType.ATHENA_PRELIMINARY_FEEDBACK : LLMServiceType.ATHENA_FEEDBACK_SUGGESTION;

        List<LLMTokenUsage> tokenUsages = new ArrayList<>();
        for (ResponseMetaLLMCallDTO llmCall : meta.llmCalls()) {
            LLMTokenUsage llmTokenUsage = new LLMTokenUsage();
            llmTokenUsage.setTraceId(traceId);
            llmTokenUsage.setCourseId(courseId);
            llmTokenUsage.setExerciseId(exerciseId);
            llmTokenUsage.setUserId(userId);
            llmTokenUsage.setServiceType(serviceType.name());
            llmTokenUsage.setNumInputTokens(llmCall.inputTokens());
            llmTokenUsage.setNumOutputTokens(llmCall.outputTokens());
            llmTokenUsage.setModel(llmCall.modelName());
        }
        llmTokenUsageRepository.saveAll(tokenUsages);
    }

    /**
     * method saves the token usage to the database with a link to the IrisMessage
     * messages of the same job are grouped together by saving the job id as a trace id
     *
     * @param builderFunction of type Function<IrisTokenUsageBuilder, IrisTokenUsageBuilder> using IrisTokenUsageBuilder
     * @return saved LLMTokenUsage as a List
     */
    public List<LLMTokenUsage> saveIrisTokenUsage(Function<IrisTokenUsageBuilder, IrisTokenUsageBuilder> builderFunction) {
        IrisTokenUsageBuilder builder = builderFunction.apply(new IrisTokenUsageBuilder());
        List<LLMTokenUsage> tokenUsages = new ArrayList<>();
        List<PyrisLLMCostDTO> tokens = builder.getTokens();
        for (PyrisLLMCostDTO cost : tokens) {
            LLMTokenUsage llmTokenUsage = new LLMTokenUsage();

            builder.getMessage().ifPresent(message -> {
                llmTokenUsage.setIrisMessageId(message.getId());
                llmTokenUsage.setTime(message.getSentAt());
            });

            builder.getUser().ifPresent(user -> {
                llmTokenUsage.setUserId(user.getId());
            });

            builder.getExercise().ifPresent(exercise -> {
                llmTokenUsage.setExerciseId(exercise.getId());
            });

            builder.getCourse().ifPresent(course -> {
                llmTokenUsage.setCourseId(course.getId());
            });

            llmTokenUsage.setTraceId(builder.getJob().jobId());
            llmTokenUsage.setServiceType(cost.pipeline());
            llmTokenUsage.setNumInputTokens(cost.numInputTokens());
            llmTokenUsage.setCostPerMillionInputTokens(cost.costPerInputToken());
            llmTokenUsage.setNumOutputTokens(cost.numOutputTokens());
            llmTokenUsage.setCostPerMillionOutputTokens(cost.costPerOutputToken());
            llmTokenUsage.setModel(cost.modelInfo());
            tokenUsages.add(llmTokenUsage);
        }
        llmTokenUsageRepository.saveAll(tokenUsages);
        return tokenUsages;
    }

    /**
     * Class IrisTokenUsageBuilder to be used for saveIrisTokenUsage()
     */
    public static class IrisTokenUsageBuilder {

        private PyrisJob job;

        private List<PyrisLLMCostDTO> tokens;

        private Optional<Course> course = Optional.empty();

        private Optional<IrisMessage> message = Optional.empty();

        private Optional<Exercise> exercise = Optional.empty();

        private Optional<User> user = Optional.empty();

        public IrisTokenUsageBuilder withJob(PyrisJob job) {
            this.job = job;
            return this;
        }

        public IrisTokenUsageBuilder withCourse(Course course) {
            this.course = Optional.ofNullable(course);
            return this;
        }

        public IrisTokenUsageBuilder withTokens(List<PyrisLLMCostDTO> tokens) {
            this.tokens = tokens;
            return this;
        }

        public IrisTokenUsageBuilder withMessage(IrisMessage message) {
            this.message = Optional.ofNullable(message);
            return this;
        }

        public IrisTokenUsageBuilder withExercise(Exercise exercise) {
            this.exercise = Optional.ofNullable(exercise);
            return this;
        }

        public IrisTokenUsageBuilder withUser(User user) {
            this.user = Optional.ofNullable(user);
            return this;
        }

        // Getters
        public PyrisJob getJob() {
            return job;
        }

        public List<PyrisLLMCostDTO> getTokens() {
            return tokens;
        }

        public Optional<Course> getCourse() {
            return course;
        }

        public Optional<IrisMessage> getMessage() {
            return message;
        }

        public Optional<Exercise> getExercise() {
            return exercise;
        }

        public Optional<User> getUser() {
            return user;
        }
    }
}
