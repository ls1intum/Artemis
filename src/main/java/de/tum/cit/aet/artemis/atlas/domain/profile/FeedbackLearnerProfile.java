package de.tum.cit.aet.artemis.atlas.domain.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Table(name = "feedback_learner_profile")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class FeedbackLearnerProfile extends DomainObject {

    @OneToOne
    @JoinColumn(name = "learner_profile_id")
    private LearnerProfile learnerProfile;

    @Column(name = "practical_vs_theoretical")
    @Min(0)
    @Max(2)
    private int practicalVsTheoretical;

    @Column(name = "creative_vs_focused")
    @Min(0)
    @Max(2)
    private int creativeVsFocused;

    @Column(name = "follow_up_vs_summary")
    @Min(0)
    @Max(2)
    private int followUpVsSummary;

    @Column(name = "brief_vs_detailed")
    @Min(0)
    @Max(2)
    private int briefVsDetailed;

    public LearnerProfile getLearnerProfile() {
        return learnerProfile;
    }

    public void setLearnerProfile(LearnerProfile learnerProfile) {
        this.learnerProfile = learnerProfile;
    }

    public int getPracticalVsTheoretical() {
        return practicalVsTheoretical;
    }

    public void setPracticalVsTheoretical(int practicalVsTheoretical) {
        this.practicalVsTheoretical = practicalVsTheoretical;
    }

    public int getCreativeVsFocused() {
        return creativeVsFocused;
    }

    public void setCreativeVsFocused(int creativeVsFocused) {
        this.creativeVsFocused = creativeVsFocused;
    }

    public int getFollowUpVsSummary() {
        return followUpVsSummary;
    }

    public void setFollowUpVsSummary(int followUpVsSummary) {
        this.followUpVsSummary = followUpVsSummary;
    }

    public int getBriefVsDetailed() {
        return briefVsDetailed;
    }

    public void setBriefVsDetailed(int briefVsDetailed) {
        this.briefVsDetailed = briefVsDetailed;
    }
}
