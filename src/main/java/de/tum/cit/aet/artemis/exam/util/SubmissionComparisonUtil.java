package de.tum.cit.aet.artemis.exam.util;

import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.compare.DnDMapping;
import de.tum.cit.aet.artemis.quiz.domain.compare.SAMapping;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Utility class for comparing the content of various submission types.
 */
public class SubmissionComparisonUtil {

    private static final Logger log = LoggerFactory.getLogger(SubmissionComparisonUtil.class);

    private SubmissionComparisonUtil() {
        // Utility class
    }

    /**
     * Returns {@code true} if the drag and drop answer submitted answer of a quiz exercise are equal to each other
     * and {@code false} otherwise.
     *
     * @param answer1 a drag and drop submitted answer
     * @param answer2 a drag and drop submitted answer to be compared with {@code answer1} for equality
     * @return {@code true} if the answers are equal to each other and {@code false} otherwise
     */
    public static boolean isContentEqualTo(DragAndDropSubmittedAnswer answer1, DragAndDropSubmittedAnswer answer2) {
        // we use a record with dragItemId and dropLocationId and use streams to create those records for both submitted answers and compare them using sets
        Set<DnDMapping> mappings1 = answer1.toDnDMapping();
        Set<DnDMapping> mappings2 = answer2.toDnDMapping();
        return Objects.equals(mappings1, mappings2);
    }

    /**
     * Returns {@code true} if the multiple choice answer submitted answer of a quiz exercise are equal to each other
     * and {@code false} otherwise.
     *
     * @param answer1 a multiple choice submitted answer
     * @param answer2 a multiple choice submitted answer to be compared with {@code answer1} for equality
     * @return {@code true} if the answers are equal to each other and {@code false} otherwise
     */
    public static boolean isContentEqualTo(MultipleChoiceSubmittedAnswer answer1, MultipleChoiceSubmittedAnswer answer2) {
        // we compare if all selected options are the same by comparing the selection option id sets, e.g. (1,3,5) vs. (2,4,5)
        Set<Long> selections1 = answer1.toSelectedIds();
        Set<Long> selections2 = answer2.toSelectedIds();
        return Objects.equals(selections1, selections2);
    }

    /**
     * Returns {@code true} if the short answer submitted answer of a quiz exercise are equal to each other
     * and {@code false} otherwise.
     *
     * @param answer1 a short answer submitted answer
     * @param answer2 a short answer submitted answer to be compared with {@code answer1} for equality
     * @return {@code true} if the answers are equal to each other and {@code false} otherwise
     */
    public static boolean isContentEqualTo(ShortAnswerSubmittedAnswer answer1, ShortAnswerSubmittedAnswer answer2) {
        // we use a record with spotId and spotText and use streams to create those records for both submitted answers and compare them using sets
        Set<SAMapping> mappings1 = answer1.toSAMappings();
        Set<SAMapping> mappings2 = answer2.toSAMappings();
        return Objects.equals(mappings1, mappings2);
    }

    /**
     * Returns {@code true} if the quiz submissions are equal to each other
     * and {@code false} otherwise.
     *
     * @param submission1 a quiz submission
     * @param submission2 a quiz submission to be compared with {@code submission1} for equality
     * @return {@code true} if the quiz submissions are equal to each other and {@code false} otherwise
     */
    public static boolean isContentEqualTo(@Nullable QuizSubmission submission1, @Nullable QuizSubmission submission2) {
        if (submission1 == null && submission2 == null) {
            return true;
        }
        else if (submission1 == null || submission2 == null) {
            return false;
        }

        var answers1 = submission1.getSubmittedAnswers();
        var answers2 = submission2.getSubmittedAnswers();
        if (answers1.size() != answers2.size()) {
            return false;
        }

        for (var answer1 : answers1) {
            for (var answer2 : answers2) {
                QuizQuestion quizQuestion1 = answer1.getQuizQuestion();
                QuizQuestion quizQuestion2 = answer2.getQuizQuestion();

                // we should still be able to compare even if the quizQuestion or the quizQuestion id is null
                if (quizQuestion1 == null || quizQuestion1.getId() == null || quizQuestion2 == null || quizQuestion2.getId() == null
                        || quizQuestion1.getId().equals(quizQuestion2.getId())) {
                    if (!isContentEqualTo(answer1, answer2)) {
                        return false;
                    }
                }
            }
        }
        // we did not find any differences
        return true;
    }

    /**
     * Returns {@code true} if the quiz submissions are equal to each other
     * and {@code false} otherwise.
     *
     * @param answer1 a quiz submission
     * @param answer2 a quiz submission to be compared with {@code submission1} for equality
     * @return {@code true} if the quiz submissions are equal to each other and {@code false} otherwise
     * @throws RuntimeException if the answer types are not supported
     */
    public static boolean isContentEqualTo(SubmittedAnswer answer1, SubmittedAnswer answer2) {
        return switch (answer1) {
            case DragAndDropSubmittedAnswer dndSubmittedAnswer1 when answer2 instanceof DragAndDropSubmittedAnswer dndSubmittedAnswer2 ->
                isContentEqualTo(dndSubmittedAnswer1, dndSubmittedAnswer2);
            case MultipleChoiceSubmittedAnswer mcSubmittedAnswer1 when answer2 instanceof MultipleChoiceSubmittedAnswer mcSubmittedAnswer2 ->
                isContentEqualTo(mcSubmittedAnswer1, mcSubmittedAnswer2);
            case ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer1 when answer2 instanceof ShortAnswerSubmittedAnswer shortAnswerSubmittedAnswer2 ->
                isContentEqualTo(shortAnswerSubmittedAnswer1, shortAnswerSubmittedAnswer2);
            default -> {
                log.error("Cannot compare {} and {} for equality, classes unknown", answer1, answer2);
                yield false;
            }
        };
    }

    /**
     * Returns {@code true} if the text submissions are equal to each other
     * and {@code false} otherwise.
     *
     * @param submission1 a text submission
     * @param submission2 a text submission to be compared with {@code submission1} for equality
     * @return {@code true} if the text submissions are equal to each other and {@code false} otherwise
     */
    public static boolean isContentEqualTo(@Nullable TextSubmission submission1, @Nullable TextSubmission submission2) {
        if (submission1 == null && submission2 == null) {
            return true;
        }
        else if (submission1 == null || submission2 == null) {
            return false;
        }
        return Objects.equals(submission1.getText(), submission2.getText());
    }

    /**
     * Returns {@code true} if the modeling submissions are equal to each other
     * and {@code false} otherwise.
     *
     * @param submission1 a modeling submission
     * @param submission2 a modeling submission to be compared with {@code submission1} for equality
     * @return {@code true} if the modeling submissions are equal to each other and {@code false} otherwise
     */
    public static boolean isContentEqualTo(@Nullable ModelingSubmission submission1, @Nullable ModelingSubmission submission2) {
        if (submission1 == null && submission2 == null) {
            return true;
        }
        else if (submission1 == null || submission2 == null) {
            return false;
        }
        return Objects.equals(submission1.getModel(), submission2.getModel()) && Objects.equals(submission1.getExplanationText(), submission2.getExplanationText());
    }
}
