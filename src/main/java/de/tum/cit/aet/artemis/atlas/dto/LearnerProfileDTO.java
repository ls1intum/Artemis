package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LearnerProfileDTO(long id, int feedbackAlternativeStandard, int feedbackFollowupSummary, int feedbackBriefDetailed) {

    /**
     * Creates LearnerProfileDTO from given LearnerProfile.
     *
     * @param learnerProfile The given LearnerProfile
     * @return LearnerProfile DTO for transfer
     */
    public static LearnerProfileDTO of(LearnerProfile learnerProfile) {
        if (learnerProfile == null) {
            return null;
        }
        return new LearnerProfileDTO(learnerProfile.getId(), learnerProfile.getFeedbackAlternativeStandard(), learnerProfile.getFeedbackFollowupSummary(),
                learnerProfile.getFeedbackBriefDetailed());
    }
}
