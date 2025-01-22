package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseLearnerProfileDTO(long id, long courseId, int aimForGradeOrBonus, int timeInvestment, int repetitionIntensity) {

    /**
     * Creates CourseLearnerProfileDTO from given CourseLearnerProfile.
     *
     * @param courseLearnerProfile The given CourseLearnerProfile
     * @return CourseLearnerProfile DTO for transfer
     */
    public static CourseLearnerProfileDTO of(CourseLearnerProfile courseLearnerProfile) {
        return new CourseLearnerProfileDTO(courseLearnerProfile.getId(), courseLearnerProfile.getCourse().getId(), courseLearnerProfile.getAimForGradeOrBonus(),
                courseLearnerProfile.getTimeInvestment(), courseLearnerProfile.getRepetitionIntensity());
    }
}
