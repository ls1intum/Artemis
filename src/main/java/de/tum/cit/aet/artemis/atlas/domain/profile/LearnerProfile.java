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
     * Minimum value allowed for profile fields.
     */
    public static final int MIN_PROFILE_VALUE = 1;

    /**
     * Default value for profile fields representing values representing neutral.
     */
    public static final int DEFAULT_PROFILE_VALUE = 2;

    /**
     * Maximum value allowed for profile fields.
     */
    public static final int MAX_PROFILE_VALUE = 3;

    @JsonIgnoreProperties("learnerProfile")
    @OneToOne(mappedBy = "learnerProfile")
    private User user;

    @OneToMany(mappedBy = "learnerProfile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("learnerProfile")
    private Set<CourseLearnerProfile> courseLearnerProfiles = new HashSet<>();

    @Column(name = "feedback_detail")
    @Min(MIN_PROFILE_VALUE)
    @Max(MAX_PROFILE_VALUE)
    private int feedbackDetail = DEFAULT_PROFILE_VALUE;

    @Column(name = "feedback_formality")
    @Min(MIN_PROFILE_VALUE)
    @Max(MAX_PROFILE_VALUE)
    private int feedbackFormality = DEFAULT_PROFILE_VALUE;

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

    public int getFeedbackDetail() {
        return feedbackDetail;
    }

    public void setFeedbackDetail(int feedbackDetail) {
        this.feedbackDetail = feedbackDetail;
    }

    public int getFeedbackFormality() {
        return feedbackFormality;
    }

    public void setFeedbackFormality(int feedbackFormality) {
        this.feedbackFormality = feedbackFormality;
    }

    public boolean hasSetupFeedbackPreferences() {
        return hasSetupFeedbackPreferences;
    }

    public void setHasSetupFeedbackPreferences(boolean hasSetupFeedbackPreferences) {
        this.hasSetupFeedbackPreferences = hasSetupFeedbackPreferences;
    }
}
