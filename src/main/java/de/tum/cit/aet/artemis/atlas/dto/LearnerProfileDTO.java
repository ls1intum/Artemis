package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LearnerProfileDTO(long id, int feedbackPracticalTheoretical, int feedbackCreativeGuidance, int feedbackFollowupSummary, int feedbackBriefDetailed) {

    /**
     * Creates LearnerProfileDTO from given LearnerProfile.
     *
     * @param learnerProfile The given LearnerProfile
     * @return LearnerProfile DTO for transfer
     */
    public static LearnerProfileDTO of(LearnerProfile learnerProfile) {
        return new LearnerProfileDTO(learnerProfile.getId(), learnerProfile.getFeedbackPracticalTheoretical(), learnerProfile.getFeedbackCreativeGuidance(),
                learnerProfile.getFeedbackFollowupSummary(), learnerProfile.getFeedbackBriefDetailed());
    }
}
