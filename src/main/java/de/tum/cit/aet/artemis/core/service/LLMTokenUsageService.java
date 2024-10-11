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
     * saves the tokens used for a specific IrisMessage or Athena call
     * in case of an Athena call IrisMessage can be null and the
     * LLMServiceType in tokens has to by Athena
     *
     * @param message  IrisMessage related to the TokenUsage
     * @param exercise Exercise in which the request was made
     * @param user     User that made the request
     * @param course   Course in which the request was made
     * @param tokens   List with Tokens of the PyrisLLMCostDTO Mdel
     * @return List of the created LLMTokenUsage entries
     */

    public List<LLMTokenUsage> saveTokenUsage(IrisMessage message, Exercise exercise, User user, Course course, List<PyrisLLMCostDTO> tokens) {
        List<LLMTokenUsage> tokenUsages = new ArrayList<>();
        for (PyrisLLMCostDTO cost : tokens) {
            LLMTokenUsage llmTokenUsage = new LLMTokenUsage();
            if (message != null) {
                llmTokenUsage.setIrisMessage(message);
                llmTokenUsage.setTimestamp(message.getSentAt());
            }
            llmTokenUsage.setServiceType(cost.pipeline());
            llmTokenUsage.setExercise(exercise);
            if (user != null) {
                llmTokenUsage.setUserId(user.getId());
            }
            llmTokenUsage.setCourse(course);
            llmTokenUsage.setNum_input_tokens(cost.num_input_tokens());
            llmTokenUsage.setNum_output_tokens(cost.num_output_tokens());
            llmTokenUsage.setModel(cost.model_info());
            tokenUsages.add(llmTokenUsageRepository.save(llmTokenUsage));
        }
        return tokenUsages;
    }
}
