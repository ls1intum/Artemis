package de.tum.cit.aet.artemis.hyperion.api;

import java.util.Collection;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionDifficulty;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolQuestionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolSelectionCandidateDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionQuizQuestionPoolSelectionService;

/**
 * Cross-module read access to the questions of a course's quiz question pool that quizzes may use: judged questions whose material has not
 * changed and that are approved, or await a decision after the filter accepted them without flagging them for review. These are the questions
 * the pool page draws instructor quizzes from.
 * <p>
 * Neither method records a selection, so reads through this API leave the rotation of instructor quizzes unchanged. The returned questions
 * carry their answer key and explanations; a caller that sends them to students must remove both and grade on the server.
 */
@Conditional(HyperionEnabled.class)
@Controller
@Lazy
public class HyperionQuizQuestionPoolApi implements AbstractApi {

    private final HyperionQuizQuestionPoolSelectionService selectionService;

    public HyperionQuizQuestionPoolApi(HyperionQuizQuestionPoolSelectionService selectionService) {
        this.selectionService = selectionService;
    }

    /**
     * Finds the questions of a course's pool that quizzes may use, least often drawn into an instructor quiz first. Every filter left
     * {@code null} or empty matches everything.
     *
     * @param courseId       the course
     * @param competencyIds  the competencies the questions were generated for
     * @param lectureUnitIds the lecture units whose material grounds the questions
     * @param questionTypes  the question types
     * @param difficulties   the difficulties
     * @return the matching questions
     */
    public List<QuizQuestionPoolQuestionDTO> findEligibleQuestions(long courseId, @Nullable Collection<Long> competencyIds, @Nullable Collection<Long> lectureUnitIds,
            @Nullable Collection<QuizQuestionGenerationType> questionTypes, @Nullable Collection<QuizQuestionDifficulty> difficulties) {
        return selectionService.findEligible(courseId, competencyIds, lectureUnitIds, questionTypes, difficulties).stream().map(QuizQuestionPoolQuestionDTO::of).toList();
    }

    /**
     * Counts the questions of a course's pool that quizzes may use, per competency, lecture unit, question type and difficulty, without
     * loading them.
     *
     * @param courseId the course
     * @return one count per combination that holds such questions
     */
    public List<QuizQuestionPoolSelectionCandidateDTO> countEligibleQuestions(long courseId) {
        return selectionService.candidates(courseId);
    }
}
