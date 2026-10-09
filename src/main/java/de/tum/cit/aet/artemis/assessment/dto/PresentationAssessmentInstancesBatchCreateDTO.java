package de.tum.cit.aet.artemis.assessment.dto;

import java.time.ZonedDateTime;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentMode;

/**
 * Request to create an individual presentation assessment instance for each selected student.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationAssessmentInstancesBatchCreateDTO(@NotNull ZonedDateTime presentationDate,
        @PositiveOrZero @DecimalMax("10000") @Digits(integer = 5, fraction = 3) Double resultPoints, @NotEmpty List<@NotBlank String> studentLogins,
        @NotBlank @Size(max = 10) String language, @NotNull PresentationAssessmentMode mode, @Size(max = 255) String location, @Size(max = 1000) String meetingLink,
        @Size(max = 1000) String remark) {

    public PresentationAssessmentInstanceRequestDTO forStudent(String studentLogin) {
        return new PresentationAssessmentInstanceRequestDTO(null, presentationDate, resultPoints, studentLogin, language, mode, location, meetingLink, remark);
    }
}
