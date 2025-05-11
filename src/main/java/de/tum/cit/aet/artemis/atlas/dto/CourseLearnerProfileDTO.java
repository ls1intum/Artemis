package de.tum.cit.aet.artemis.atlas.dto;

import static de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile.MAX_PROFILE_VALUE;
import static de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile.MIN_PROFILE_VALUE;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CourseLearnerProfileDTO(long id, long courseId, String courseTitle, int aimForGradeOrBonus, int timeInvestment, int repetitionIntensity) {

    /**
     * Creates CourseLearnerProfileDTO from given CourseLearnerProfile.
     *
     * @param courseLearnerProfile The given CourseLearnerProfile
     * @return CourseLearnerProfile DTO for transfer
     */
    public static CourseLearnerProfileDTO of(CourseLearnerProfile courseLearnerProfile) {
        var course = courseLearnerProfile.getCourse();
        return new CourseLearnerProfileDTO(courseLearnerProfile.getId(), course.getId(), course.getTitle(), clamp(courseLearnerProfile.getAimForGradeOrBonus()),
                clamp(courseLearnerProfile.getTimeInvestment()), clamp(courseLearnerProfile.getRepetitionIntensity()));
    }

    /**
     * Clamps the given value to be within the range of {@link #MIN_PROFILE_VALUE} and {@link #MAX_PROFILE_VALUE}.
     *
     * @param value The value to clamp
     * @return The clamped value
     */
    private static int clamp(int value) {
        return Math.max(MIN_PROFILE_VALUE, Math.min(MAX_PROFILE_VALUE, value));
    }
}
