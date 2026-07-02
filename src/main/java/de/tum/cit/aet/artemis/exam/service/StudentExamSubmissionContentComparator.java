package de.tum.cit.aet.artemis.exam.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
 * Compares submission content while ignoring persistence metadata such as generated ids and timestamps.
 */
public final class StudentExamSubmissionContentComparator {

    private static final Logger log = LoggerFactory.getLogger(StudentExamSubmissionContentComparator.class);

    private StudentExamSubmissionContentComparator() {
    }

    /**
     * Returns {@code true} if the drag and drop submitted answers of a quiz exercise are equal to each other
     * and {@code false} otherwise.
     *
     * @param answer1 a drag and drop submitted answer
     * @param answer2 a drag and drop submitted answer to be compared with {@code answer1} for equality
     * @return {@code true} if the answers are equal to each other and {@code false} otherwise
     */
    public static boolean isContentEqualTo(DragAndDropSubmittedAnswer answer1, DragAndDropSubmittedAnswer answer2) {
        Set<DnDMapping> mappings1 = answer1.toDnDMapping();
        Set<DnDMapping> mappings2 = answer2.toDnDMapping();
        return Objects.equals(mappings1, mappings2);
    }

    /**
     * Returns {@code true} if the multiple choice submitted answers of a quiz exercise are equal to each other
     * and {@code false} otherwise.
     *
     * @param answer1 a multiple choice submitted answer
     * @param answer2 a multiple choice submitted answer to be compared with {@code answer1} for equality
     * @return {@code true} if the answers are equal to each other and {@code false} otherwise
     */
    public static boolean isContentEqualTo(MultipleChoiceSubmittedAnswer answer1, MultipleChoiceSubmittedAnswer answer2) {
        Set<Long> selections1 = answer1.toSelectedIds();
        Set<Long> selections2 = answer2.toSelectedIds();
        return Objects.equals(selections1, selections2);
    }

    /**
     * Returns {@code true} if the short answer submitted answers of a quiz exercise are equal to each other
     * and {@code false} otherwise.
     *
     * @param answer1 a short answer submitted answer
     * @param answer2 a short answer submitted answer to be compared with {@code answer1} for equality
     * @return {@code true} if the answers are equal to each other and {@code false} otherwise
     */
    public static boolean isContentEqualTo(ShortAnswerSubmittedAnswer answer1, ShortAnswerSubmittedAnswer answer2) {
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

        List<SubmittedAnswer> unmatchedAnswers = new ArrayList<>(answers2);
        for (var answer1 : answers1) {
            Optional<SubmittedAnswer> matchingAnswer = unmatchedAnswers.stream()
                    .filter(answer2 -> isSubmittedAnswerForSameQuestion(answer1, answer2) && isContentEqualTo(answer1, answer2)).findFirst();
            if (matchingAnswer.isEmpty()) {
                return false;
            }
            unmatchedAnswers.remove(matchingAnswer.get());
        }
        return unmatchedAnswers.isEmpty();
    }

    private static boolean isSubmittedAnswerForSameQuestion(SubmittedAnswer answer1, SubmittedAnswer answer2) {
        Long quizQuestionId1 = getQuizQuestionId(answer1);
        Long quizQuestionId2 = getQuizQuestionId(answer2);
        if (quizQuestionId1 != null && quizQuestionId2 != null) {
            return quizQuestionId1.equals(quizQuestionId2);
        }
        // Fall back to the concrete answer type when question references are missing in a lightweight payload.
        return answer1.getClass().equals(answer2.getClass());
    }

    private static Long getQuizQuestionId(SubmittedAnswer submittedAnswer) {
        QuizQuestion quizQuestion = submittedAnswer.getQuizQuestion();
        return quizQuestion == null ? null : quizQuestion.getId();
    }

    /**
     * Returns {@code true} if the submitted answers are equal to each other
     * and {@code false} otherwise.
     *
     * @param answer1 a submitted answer
     * @param answer2 a submitted answer to be compared with {@code answer1} for equality
     * @return {@code true} if the submitted answers are equal to each other and {@code false} otherwise
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
                log.error("Cannot compare submitted answers for equality, classes unknown: {}({}) and {}({})", answer1.getClass().getSimpleName(), answer1.getId(),
                        answer2 == null ? null : answer2.getClass().getSimpleName(), answer2 == null ? null : answer2.getId());
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
