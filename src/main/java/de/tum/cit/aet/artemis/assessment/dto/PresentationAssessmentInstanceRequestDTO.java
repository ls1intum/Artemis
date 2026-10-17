package de.tum.cit.aet.artemis.assessment.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentMode;

/**
 * Request data for updating an individual presentation assessment instance.
 * Also used internally to represent one student's data during batch creation.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationAssessmentInstanceRequestDTO(Long id, @NotNull ZonedDateTime presentationDate,
        @PositiveOrZero @DecimalMax("10000") @Digits(integer = 5, fraction = 3) Double resultPoints, @NotBlank String studentLogin, @NotBlank @Size(max = 10) String language,
        @NotNull PresentationAssessmentMode mode, @Size(max = 255) String location,
        @Size(max = 1000) @Pattern(regexp = "^https?://\\S+$", flags = Pattern.Flag.CASE_INSENSITIVE) String meetingLink, @Size(max = 1000) String remark) {
}
