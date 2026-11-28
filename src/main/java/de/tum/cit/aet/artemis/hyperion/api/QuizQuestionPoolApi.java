package de.tum.cit.aet.artemis.hyperion.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItem;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolCandidateDTO;
import de.tum.cit.aet.artemis.hyperion.repository.QuizQuestionPoolItemRepository;

/**
 * Cross-module API providing access to the Hyperion AI Quiz Question Pool for external modules like Atlas.
 */
@Controller
@Lazy
@Profile(PROFILE_CORE)
@Conditional(HyperionEnabled.class)
public class QuizQuestionPoolApi implements AbstractApi {

    private final QuizQuestionPoolItemRepository itemRepository;

    public QuizQuestionPoolApi(QuizQuestionPoolItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /**
     * Finds lightweight candidate metadata for all items in a course's quiz question pool that are eligible
     * for adaptive practice quizzing, avoiding full question JSON loading until items are sampled.
     *
     * @param courseId the ID of the course
     * @return the list of eligible candidate DTOs
     */
    public List<QuizQuestionPoolCandidateDTO> getPracticeEligibleQuestionCandidates(long courseId) {
        return itemRepository.findAllPracticeEligibleCandidatesByCourseId(courseId);
    }

    /**
     * Batch-loads full pool items with question data for the specified IDs.
     *
     * @param itemIds the IDs of the items to load
     * @return the list of loaded pool items
     */
    public List<QuizQuestionPoolItem> findAllById(Collection<Long> itemIds) {
        return itemRepository.findAllById(itemIds);
    }
}
