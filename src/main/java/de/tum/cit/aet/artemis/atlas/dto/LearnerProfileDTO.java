package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LearnerProfileDTO(long id,

        @JsonProperty("feedback_alternative_standard") int feedbackAlternativeStandard,

        @JsonProperty("feedback_followup_summary") int feedbackFollowupSummary,

        @JsonProperty("feedback_brief_detailed") int feedbackBriefDetailed) {

    /**
     * Creates LearnerProfileDTO from given LearnerProfile.
     *
     * @param learnerProfile The given LearnerProfile
     * @return LearnerProfile DTO for transfer
     */
    public static LearnerProfileDTO of(LearnerProfile learnerProfile) {
        return new LearnerProfileDTO(learnerProfile.getId(), learnerProfile.getFeedbackAlternativeStandard(), learnerProfile.getFeedbackFollowupSummary(),
                learnerProfile.getFeedbackBriefDetailed());
    }
}
