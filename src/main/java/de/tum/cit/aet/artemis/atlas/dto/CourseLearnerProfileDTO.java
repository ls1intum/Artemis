package de.tum.cit.aet.artemis.atlas.dto;

import static de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile.MAX_PROFILE_VALUE;
import static de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile.MIN_PROFILE_VALUE;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CourseLearnerProfileDTO(long id, long courseId, String courseTitle, int aimForGradeOrBonus, int timeInvestment, int repetitionIntensity, double proficiency,
        double initialProficiency) {

    /**
     * Creates CourseLearnerProfileDTO from given CourseLearnerProfile.
     *
     * @param courseLearnerProfile The given CourseLearnerProfile
     * @return CourseLearnerProfile DTO for transfer
     */
    public static CourseLearnerProfileDTO of(CourseLearnerProfile courseLearnerProfile) {
        var course = courseLearnerProfile.getCourse();
        return new CourseLearnerProfileDTO(courseLearnerProfile.getId(), course.getId(), course.getTitle(), (int) clamp(courseLearnerProfile.getAimForGradeOrBonus()),
                (int) clamp(courseLearnerProfile.getTimeInvestment()), (int) clamp(courseLearnerProfile.getRepetitionIntensity()), clamp(courseLearnerProfile.getProficiency()),
                clamp(courseLearnerProfile.getInitialProficiency()));
    }

    /**
     * Clamps the given value to be within the range of {@link CourseLearnerProfile#MIN_PROFILE_VALUE} and {@link CourseLearnerProfile#MAX_PROFILE_VALUE}.
     *
     * @param value The value to clamp
     * @return The clamped value
     */
    private static double clamp(double value) {
        return Math.max(MIN_PROFILE_VALUE, Math.min(MAX_PROFILE_VALUE, value));
    }
}
