package de.tum.cit.aet.artemis.assessment.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentInstance;
import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentMode;

/**
 * DTO for a scheduled presentation assessment instance.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationAssessmentInstanceDTO(Long id, @NotNull ZonedDateTime presentationDate,
        @PositiveOrZero @DecimalMax("10000") @Digits(integer = 5, fraction = 3) Double resultPoints, @NotBlank @Size(max = 10) String language,
        @NotNull PresentationAssessmentMode mode, @Size(max = 255) String location, @Size(max = 1000) String meetingLink, @Size(max = 1000) String remark,
        PresentationAssessmentStudentDTO student, UserNameAndLoginDTO assessor, UserNameAndLoginDTO gradeEditedBy) {

    /**
     * Creates the DTO of an instance whose student, assessor and grader are loaded. The person who last changed the grade is only included when they are not
     * the assessor, as only then the client names them.
     *
     * @param instance the presentation assessment instance
     * @return the DTO of the instance
     */
    public static PresentationAssessmentInstanceDTO of(PresentationAssessmentInstance instance) {
        User gradedBy = instance.getGradedBy();
        boolean gradeEditedByOther = gradedBy != null && (instance.getAssessor() == null || !gradedBy.getId().equals(instance.getAssessor().getId()));
        return new PresentationAssessmentInstanceDTO(instance.getId(), instance.getPresentationDate(), instance.getResultPoints(), instance.getLanguage(), instance.getMode(),
                instance.getLocation(), instance.getMeetingLink(), instance.getRemark(), PresentationAssessmentStudentDTO.of(instance.getStudent()),
                instance.getAssessor() != null ? UserNameAndLoginDTO.of(instance.getAssessor()) : null, gradeEditedByOther ? UserNameAndLoginDTO.of(gradedBy) : null);
    }
}
