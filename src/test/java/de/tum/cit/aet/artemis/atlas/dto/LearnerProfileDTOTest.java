package de.tum.cit.aet.artemis.atlas.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;

class LearnerProfileDTOTest {

    @Test
    void of_shouldReturnNullForNullInput() {
        assertThat(LearnerProfileDTO.of(null)).isNull();
    }

    @Test
    void of_shouldClampValuesWithinRange() {
        LearnerProfile profile = new LearnerProfile();
        profile.setId(42L);
        profile.setFeedbackAlternativeStandard(0); // below min
        profile.setFeedbackFollowupSummary(5);    // above max
        profile.setFeedbackBriefDetailed(2);      // within range

        LearnerProfileDTO dto = LearnerProfileDTO.of(profile);
        assertThat(dto.id()).isEqualTo(42L);
        assertThat(dto.feedbackAlternativeStandard()).isEqualTo(LearnerProfile.MIN_PROFILE_VALUE);
        assertThat(dto.feedbackFollowupSummary()).isEqualTo(LearnerProfile.MAX_PROFILE_VALUE);
        assertThat(dto.feedbackBriefDetailed()).isEqualTo(2);
    }
}
