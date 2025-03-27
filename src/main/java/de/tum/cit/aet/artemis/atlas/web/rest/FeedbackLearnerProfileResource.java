package de.tum.cit.aet.artemis.atlas.web.rest;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.domain.profile.FeedbackLearnerProfile;
import de.tum.cit.aet.artemis.atlas.service.profile.FeedbackLearnerProfileService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.user.UserService;

@RestController
@RequestMapping("/api")
@Profile(PROFILE_CORE)
public class FeedbackLearnerProfileResource {

    private final FeedbackLearnerProfileService feedbackLearnerProfileService;

    private final UserService userService;

    public FeedbackLearnerProfileResource(FeedbackLearnerProfileService feedbackLearnerProfileService, UserService userService) {
        this.feedbackLearnerProfileService = feedbackLearnerProfileService;
        this.userService = userService;
    }

    /**
     * GET /feedback-learner-profile : get the current user's feedback learner profile
     *
     * @return the ResponseEntity with status 200 (OK) and the feedback learner profile in body
     */
    @GetMapping("/feedback-learner-profile")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<FeedbackLearnerProfile> getFeedbackLearnerProfile() {
        User user = userService.getUserWithGroupsAndAuthorities();
        FeedbackLearnerProfile profile = feedbackLearnerProfileService.getFeedbackProfile(user);
        return ResponseEntity.ok(profile);
    }

    /**
     * PUT /feedback-learner-profile : update the current user's feedback learner profile
     *
     * @param profile the feedback learner profile to update
     * @return the ResponseEntity with status 200 (OK) and the updated feedback learner profile in body
     */
    @PutMapping("/feedback-learner-profile")
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<FeedbackLearnerProfile> updateFeedbackLearnerProfile(@RequestBody FeedbackLearnerProfile profile) {
        User user = userService.getUserWithGroupsAndAuthorities();
        // Get the existing profile for the current user
        FeedbackLearnerProfile existingProfile = feedbackLearnerProfileService.getFeedbackProfile(user);

        // Update the values from the request
        existingProfile.setPracticalVsTheoretical(profile.getPracticalVsTheoretical());
        existingProfile.setCreativeVsFocused(profile.getCreativeVsFocused());
        existingProfile.setFollowUpVsSummary(profile.getFollowUpVsSummary());
        existingProfile.setBriefVsDetailed(profile.getBriefVsDetailed());

        // Save the updated profile
        FeedbackLearnerProfile updatedProfile = feedbackLearnerProfileService.updateFeedbackProfile(existingProfile);
        return ResponseEntity.ok(updatedProfile);
    }
}
