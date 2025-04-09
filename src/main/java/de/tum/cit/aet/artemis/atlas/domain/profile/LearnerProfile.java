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

    @JsonIgnoreProperties("learnerProfile")
    @OneToOne(mappedBy = "learnerProfile", cascade = CascadeType.PERSIST)
    private User user;

    @OneToMany(mappedBy = "learnerProfile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("learnerProfile")
    private Set<CourseLearnerProfile> courseLearnerProfiles = new HashSet<>();

    @Column(name = "feedback_practical_theoretical")
    @Min(1)
    @Max(5)
    private int feedbackPracticalTheoretical;

    @Column(name = "feedback_creative_guidance")
    @Min(1)
    @Max(5)
    private int feedbackCreativeGuidance;

    @Column(name = "feedback_followup_summary")
    @Min(1)
    @Max(5)
    private int feedbackFollowupSummary;

    @Column(name = "feedback_brief_detailed")
    @Min(1)
    @Max(5)
    private int feedbackBriefDetailed;

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

    public int getFeedbackPracticalTheoretical() {
        return feedbackPracticalTheoretical;
    }

    public void setFeedbackPracticalTheoretical(int feedbackPracticalTheoretical) {
        this.feedbackPracticalTheoretical = feedbackPracticalTheoretical;
    }

    public int getFeedbackCreativeGuidance() {
        return feedbackCreativeGuidance;
    }

    public void setFeedbackCreativeGuidance(int feedbackCreativeGuidance) {
        this.feedbackCreativeGuidance = feedbackCreativeGuidance;
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
}
