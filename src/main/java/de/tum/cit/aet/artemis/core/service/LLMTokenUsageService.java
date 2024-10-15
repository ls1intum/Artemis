package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
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
     * method saves the token usage to the database with a link to the IrisMessage
     * messages of the same job are grouped together by saving the job id as a trace id
     *
     * @param job      used to create a unique traceId to group multiple LLM calls
     * @param message  IrisMessage to map the usage to an IrisMessage
     * @param exercise to map the token cost to an exercise
     * @param user     to map the token cost to a user
     * @param course   to map the token to a course
     * @param tokens   token cost list of type PyrisLLMCostDTO
     * @return list of the saved data
     */
    public List<LLMTokenUsage> saveIrisTokenUsage(PyrisJob job, IrisMessage message, Exercise exercise, User user, Course course, List<PyrisLLMCostDTO> tokens) {
        List<LLMTokenUsage> tokenUsages = new ArrayList<>();

        for (PyrisLLMCostDTO cost : tokens) {
            LLMTokenUsage llmTokenUsage = new LLMTokenUsage();
            if (message != null) {
                llmTokenUsage.setIrisMessageId(message.getId());
                llmTokenUsage.setTime(message.getSentAt());
            }
            if (user != null) {
                llmTokenUsage.setUserId(user.getId());
            }
            if (job != null) {
                llmTokenUsage.setTraceId(job.jobId());
            }
            llmTokenUsage.setServiceType(cost.pipeline());
            if (exercise != null) {
                llmTokenUsage.setExerciseId(exercise.getId());
            }
            if (course != null) {
                llmTokenUsage.setCourseId(course.getId());
            }
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
     * Overloaded method to save token usage without message and exercise.
     *
     * @param job    used to create a unique traceId to group multiple LLM calls
     * @param user   to map the token cost to a user
     * @param course to map the token to a course
     * @param tokens token cost list of type PyrisLLMCostDTO
     * @return list of the saved data
     */
    public List<LLMTokenUsage> saveIrisTokenUsage(PyrisJob job, User user, Course course, List<PyrisLLMCostDTO> tokens) {
        return saveIrisTokenUsage(job, null, null, user, course, tokens);
    }

    /**
     * Overloaded method to save token usage without exercise.
     *
     * @param job     used to create a unique traceId to group multiple LLM calls
     * @param message IrisMessage to map the usage to an IrisMessage
     * @param user    to map the token cost to a user
     * @param course  to map the token to a course
     * @param tokens  token cost list of type PyrisLLMCostDTO
     * @return list of the saved data
     */
    public List<LLMTokenUsage> saveIrisTokenUsage(PyrisJob job, IrisMessage message, User user, Course course, List<PyrisLLMCostDTO> tokens) {
        return saveIrisTokenUsage(job, message, null, user, course, tokens);
    }
}
