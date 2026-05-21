package de.tum.cit.aet.artemis.core.dto.export;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for exporting learner profile data for GDPR user data export.
 * Includes course information so the user knows which course each profile belongs to.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserLearnerProfileExportDTO(Long courseId, String courseTitle, Integer aimForGradeOrBonus, Integer timeInvestment, Integer repetitionIntensity) {
}
