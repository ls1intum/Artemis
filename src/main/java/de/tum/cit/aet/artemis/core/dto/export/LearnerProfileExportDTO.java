package de.tum.cit.aet.artemis.core.dto.export;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for exporting learner profile data for course archival.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LearnerProfileExportDTO(String userLogin, Integer aimForGradeOrBonus, Integer timeInvestment, Integer repetitionIntensity) {
}
