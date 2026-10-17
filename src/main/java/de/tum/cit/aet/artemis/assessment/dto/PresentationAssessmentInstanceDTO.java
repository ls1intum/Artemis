package de.tum.cit.aet.artemis.assessment.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentInstance;
import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentMode;

/**
 * DTO for a scheduled presentation assessment instance.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationAssessmentInstanceDTO(Long id, @NotNull ZonedDateTime presentationDate,
        @PositiveOrZero @DecimalMax("10000") @Digits(integer = 5, fraction = 3) Double resultPoints, @NotBlank @Size(max = 10) String language,
        @NotNull PresentationAssessmentMode mode, @Size(max = 255) String location, @Size(max = 1000) String meetingLink, @Size(max = 1000) String remark,
        PresentationAssessmentStudentDTO student) {

    public static PresentationAssessmentInstanceDTO of(PresentationAssessmentInstance instance) {
        return new PresentationAssessmentInstanceDTO(instance.getId(), instance.getPresentationDate(), instance.getResultPoints(), instance.getLanguage(), instance.getMode(),
                instance.getLocation(), instance.getMeetingLink(), instance.getRemark(), PresentationAssessmentStudentDTO.of(instance.getStudent()));
    }
}
