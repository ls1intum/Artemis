package de.tum.cit.aet.artemis.atlas.domain.profile;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;

@Entity
@Table(name = "learner_profile")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LearnerProfile extends DomainObject {

    public static final String ENTITY_NAME = "learnerProfile";

    /**
     * Minimum value allowed for profile fields representing values on a Likert scale.
     */
    public static final int MIN_PROFILE_VALUE = 1;

    /**
     * Maximum value allowed for profile fields representing values on a Likert scale.
     */
    public static final int MAX_PROFILE_VALUE = 3;

    @JsonIgnoreProperties("learnerProfile")
    @OneToOne(mappedBy = "learnerProfile", cascade = CascadeType.PERSIST)
    private User user;

    @OneToMany(mappedBy = "learnerProfile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("learnerProfile")
    private Set<CourseLearnerProfile> courseLearnerProfiles = new HashSet<>();

    @Column(name = "feedback_alternative_standard")
    @Min(MIN_PROFILE_VALUE)
    @Max(MAX_PROFILE_VALUE)
    private int feedbackAlternativeStandard = 2;

    @Column(name = "feedback_followup_summary")
    @Min(MIN_PROFILE_VALUE)
    @Max(MAX_PROFILE_VALUE)
    private int feedbackFollowupSummary = 2;

    @Column(name = "feedback_brief_detailed")
    @Min(MIN_PROFILE_VALUE)
    @Max(MAX_PROFILE_VALUE)
    private int feedbackBriefDetailed = 2;

    @Column(name = "has_setup_feedback_preferences")
    private boolean hasSetupFeedbackPreferences = false;

    public void setUser(User user) {
        this.user = user;
    }

    public User getUser() {
        return this.user;
    }

    public void setCourseLearnerProfiles(Set<CourseLearnerProfile> courseLearnerProfiles) {
        this.courseLearnerProfiles = courseLearnerProfiles;
    }

    public Set<CourseLearnerProfile> getCourseLearnerProfiles() {
        return this.courseLearnerProfiles;
    }

    public boolean addCourseLearnerProfile(CourseLearnerProfile courseLearnerProfile) {
        return this.courseLearnerProfiles.add(courseLearnerProfile);
    }

    public boolean addAllCourseLearnerProfiles(Collection<? extends CourseLearnerProfile> courseLearnerProfiles) {
        return this.courseLearnerProfiles.addAll(courseLearnerProfiles);
    }

    public boolean removeCourseLearnerProfile(CourseLearnerProfile courseLearnerProfile) {
        return this.courseLearnerProfiles.remove(courseLearnerProfile);
    }

    public int getFeedbackAlternativeStandard() {
        return feedbackAlternativeStandard;
    }

    public void setFeedbackAlternativeStandard(int feedbackAlternativeStandard) {
        this.feedbackAlternativeStandard = feedbackAlternativeStandard;
    }

    public int getFeedbackFollowupSummary() {
        return feedbackFollowupSummary;
    }

    public void setFeedbackFollowupSummary(int feedbackFollowupSummary) {
        this.feedbackFollowupSummary = feedbackFollowupSummary;
    }

    public int getFeedbackBriefDetailed() {
        return feedbackBriefDetailed;
    }

    public void setFeedbackBriefDetailed(int feedbackBriefDetailed) {
        this.feedbackBriefDetailed = feedbackBriefDetailed;
    }

    public boolean hasSetupFeedbackPreferences() {
        return hasSetupFeedbackPreferences;
    }

    public void setHasSetupFeedbackPreferences(boolean hasSetupFeedbackPreferences) {
        this.hasSetupFeedbackPreferences = hasSetupFeedbackPreferences;
    }
}
