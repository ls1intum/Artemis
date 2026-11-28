package de.tum.cit.aet.artemis.assessment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricRating;

/**
 * The rating of one rubric criterion of a presentation assessment instance: the selected level and an optional comment.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationRubricRatingDTO(@NotNull Long criterionId, @NotNull Long levelId, @Size(max = PresentationRubricRating.MAX_COMMENT_LENGTH) String comment) {

    public static PresentationRubricRatingDTO of(PresentationRubricRating rating) {
        return new PresentationRubricRatingDTO(rating.getCriterion().getId(), rating.getLevel().getId(), rating.getComment());
    }
}
