package de.tum.cit.aet.artemis.metis.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.metis.domain.MetisQuestion;

/**
 * One question of a comprehension check, main question or follow-up, with the student's answer and its rating.
 * <p>
 * Staff see every column ({@link #ofStaff(MetisQuestion)}). Students see their own questions without the review state, and the rating only once the
 * service allows it ({@link #ofStudent(MetisQuestion, boolean)}).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisQuestionDTO(@NotNull long id, @NotNull long checkId, @NotNull int questionOrder, @Nullable Long parentQuestionId, @Nullable String focusFilePath,
        @Nullable Integer focusFileChangedLines, @NotNull String questionText, @NotNull int timeLimitSeconds, @NotNull int regenerationCount, @Nullable ZonedDateTime approvedAt,
        @Nullable ZonedDateTime shownAt, @Nullable String answerText, @Nullable ZonedDateTime answeredAt, @Nullable Integer llmScore, @Nullable String llmRationale,
        @Nullable ZonedDateTime llmRatedAt) {

    /**
     * Maps a question for instructors, editors and tutors.
     *
     * @param question the stored question
     * @return the question with every column
     */
    public static MetisQuestionDTO ofStaff(MetisQuestion question) {
        return new MetisQuestionDTO(question.getId(), question.getCheckId(), question.getQuestionOrder(), question.getParentQuestionId(), question.getFocusFilePath(),
                question.getFocusFileChangedLines(), question.getQuestionText(), question.getTimeLimitSeconds(), question.getRegenerationCount(), question.getApprovedAt(),
                question.getShownAt(), question.getAnswerText(), question.getAnsweredAt(), question.getLlmScore(), question.getLlmRationale(), question.getLlmRatedAt());
    }

    /**
     * Maps a question for the student who answers it. The review state never reaches the student: the approval is left out and the regeneration count is sent
     * as 0.
     *
     * @param question   the stored question
     * @param withRating whether the score, the rationale and the time of the rating are included; the service decides when the student may see them
     * @return the question as the student sees it
     */
    public static MetisQuestionDTO ofStudent(MetisQuestion question, boolean withRating) {
        return new MetisQuestionDTO(question.getId(), question.getCheckId(), question.getQuestionOrder(), question.getParentQuestionId(), question.getFocusFilePath(),
                question.getFocusFileChangedLines(), question.getQuestionText(), question.getTimeLimitSeconds(), 0, null, question.getShownAt(), question.getAnswerText(),
                question.getAnsweredAt(), withRating ? question.getLlmScore() : null, withRating ? question.getLlmRationale() : null, withRating ? question.getLlmRatedAt() : null);
    }
}
