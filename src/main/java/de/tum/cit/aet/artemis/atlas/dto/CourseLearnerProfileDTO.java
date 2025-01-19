package de.tum.cit.aet.artemis.atlas.dto;

import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.core.domain.Course;

/**
 * DTO containing id for {@link Course} and all information stored in the profile.
 */
public record CourseLearnerProfileDTO(long id, long courseId, int aimForGradeOrBonus, int timeInvestment, int repetitionIntensity) {

    public static CourseLearnerProfileDTO of(CourseLearnerProfile courseLearnerProfile) {
        return new CourseLearnerProfileDTO(courseLearnerProfile.getId(), courseLearnerProfile.getCourse().getId(), courseLearnerProfile.getAimForGradeOrBonus(),
                courseLearnerProfile.getTimeInvestment(), courseLearnerProfile.getRepetitionIntensity());
    }
}
