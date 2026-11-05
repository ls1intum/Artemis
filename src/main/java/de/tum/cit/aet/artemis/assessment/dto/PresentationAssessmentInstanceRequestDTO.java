package de.tum.cit.aet.artemis.assessment.dto;

import java.time.ZonedDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentMode;

/**
 * Request data for updating an individual presentation assessment instance.
 * Also used internally to represent one student's data during batch creation.
 * <p>
 * {@code ratings} is the result of a presentation graded by rubric: one rating for every criterion, or none for an instance that is not assessed yet.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationAssessmentInstanceRequestDTO(Long id, @NotNull ZonedDateTime presentationDate,
        @PositiveOrZero @DecimalMax("10000") @Digits(integer = 5, fraction = 3) Double resultPoints, Boolean passed, @NotBlank String studentLogin,
        @NotBlank @Size(max = 10) String language, @NotNull PresentationAssessmentMode mode, @Size(max = 255) String location,
        @Size(max = 1000) @Pattern(regexp = "^https?://\\S+$", flags = Pattern.Flag.CASE_INSENSITIVE) String meetingLink, @Size(max = 1000) String remark,
        @Nullable @Size(min = 1, max = 20) List<@NotNull @Valid PresentationRubricRatingDTO> ratings) {
}
