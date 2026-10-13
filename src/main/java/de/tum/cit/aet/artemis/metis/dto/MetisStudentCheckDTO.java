package de.tum.cit.aet.artemis.metis.dto;

import java.time.ZonedDateTime;
import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus;

/**
 * The comprehension check of the requesting student on one exercise, as the student's tab shows it.
 * <p>
 * The status is the presented one, which can differ from the stored status, and {@code failureReason} carries a code of
 * {@link de.tum.cit.aet.artemis.metis.domain.MetisFailureReason}. The window is the student's effective window; the group title is set only when a group window
 * applies. {@code currentQuestion} is the question that is open right now, and {@code answeredQuestions} lists the answered questions in thread order. The list
 * is always sent, also when it is empty, and it carries ratings only while {@code ratingsVisible} is true. Unshown main questions are never part of it.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisStudentCheckDTO(@NotNull MetisCheckStatus status, @Nullable ZonedDateTime windowStart, @Nullable ZonedDateTime windowEnd, @Nullable String tutorialGroupTitle,
        @NotNull int mainQuestionCount, @NotNull int likertScalePoints, @Nullable MetisQuestionDTO currentQuestion, @JsonInclude @NotNull List<MetisQuestionDTO> answeredQuestions,
        @NotNull boolean ratingsVisible, @Nullable String failureReason) {
}
