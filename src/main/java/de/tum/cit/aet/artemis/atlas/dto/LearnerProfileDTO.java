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
     * Creates a {@link LearnerProfileDTO} from the given profile, substituting a course-level default for a field the
     * student has not explicitly set.
     * <p>
     * A student who has never opened the feedback preferences page still has {@code feedbackDetail}/
     * {@code feedbackFormality} on their profile - both sit at {@code LearnerProfile.DEFAULT_PROFILE_VALUE} - so
     * {@link LearnerProfile#hasSetupFeedbackPreferences()} is what actually tells an explicit choice from that untouched default. Only
     * when it is {@code false} does a course default (if the instructor set one) take over; a student who has set
     * their own preference always keeps it, regardless of what the course requests.
     *
     * @param learnerProfile                 the student's learner profile, or null
     * @param courseDefaultFeedbackDetail    the course's default feedback detail, or a non-positive value for "not set"
     * @param courseDefaultFeedbackFormality the course's default feedback formality, or a non-positive value for "not set"
     * @return the resulting DTO, or null if {@code learnerProfile} is null or not persisted
     */
    public static LearnerProfileDTO withCourseDefaults(LearnerProfile learnerProfile, int courseDefaultFeedbackDetail, int courseDefaultFeedbackFormality) {
        if (learnerProfile == null || learnerProfile.getId() == null) {
            return null;
        }
        if (learnerProfile.hasSetupFeedbackPreferences()) {
            return of(learnerProfile);
        }
        int feedbackDetail = courseDefaultFeedbackDetail > 0 ? clamp(courseDefaultFeedbackDetail) : clamp(learnerProfile.getFeedbackDetail());
        int feedbackFormality = courseDefaultFeedbackFormality > 0 ? clamp(courseDefaultFeedbackFormality) : clamp(learnerProfile.getFeedbackFormality());
        return new LearnerProfileDTO(learnerProfile.getId(), feedbackDetail, feedbackFormality, false);
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
