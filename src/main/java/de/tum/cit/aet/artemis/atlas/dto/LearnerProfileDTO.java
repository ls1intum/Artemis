package de.tum.cit.aet.artemis.atlas.dto;

import static de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile.MAX_PROFILE_VALUE;
import static de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile.MIN_PROFILE_VALUE;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LearnerProfileDTO(long id, @Min(MIN_PROFILE_VALUE) @Max(MAX_PROFILE_VALUE) int feedbackDetail, @Min(MIN_PROFILE_VALUE) @Max(MAX_PROFILE_VALUE) int feedbackFormality,
        boolean hasSetupFeedbackPreferences) {

    /**
     * Creates LearnerProfileDTO from given LearnerProfile.
     *
     * @param learnerProfile The given LearnerProfile
     * @return LearnerProfile DTO for transfer
     */
    public static LearnerProfileDTO of(LearnerProfile learnerProfile) {
        if (learnerProfile == null || learnerProfile.getId() == null) {
            return null;
        }
        return new LearnerProfileDTO(learnerProfile.getId(), clamp(learnerProfile.getFeedbackDetail()), clamp(learnerProfile.getFeedbackFormality()),
                learnerProfile.hasSetupFeedbackPreferences());
    }

    /**
     * Clamps the given value to be within the range of {@link LearnerProfile#MIN_PROFILE_VALUE} and {@link LearnerProfile#MAX_PROFILE_VALUE}.
     *
     * @param value The value to clamp
     * @return The clamped value
     */
    private static int clamp(int value) {
        return Math.max(MIN_PROFILE_VALUE, Math.min(MAX_PROFILE_VALUE, value));
    }
}
