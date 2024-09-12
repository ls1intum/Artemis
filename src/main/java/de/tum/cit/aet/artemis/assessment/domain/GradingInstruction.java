package de.tum.cit.aet.artemis.assessment.domain;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A Structured Grading Instruction.
 */
@Entity
@Table(name = "grading_instruction")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GradingInstruction extends DomainObject {

    // the score students get if this grading instruction is applicable
    @Column(name = "credits")
    private double credits;

    // describes the level of performance (e.g. "poor" or "average" )
    @Column(name = "grading_scale")
    private String gradingScale;

    @Column(name = "instruction_description")
    private String instructionDescription;

    @Column(name = "feedback")
    private String feedback;

    // how often points for this grading instruction should be calculated if the criteria is applicable more than once for the same submission (e.g. to prevent consequential
    // errors)
    @Column(name = "usage_count")
    private int usageCount;

    // TODO: we should think about making this eager, because it is inconsistent
    @ManyToOne(fetch = FetchType.LAZY)
    private GradingCriterion gradingCriterion;

    @OneToMany(mappedBy = "gradingInstruction", fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties(value = "gradingInstruction", allowSetters = true)
    private Set<Feedback> feedbacks = new HashSet<>();

    public double getCredits() {
        return credits;
    }

    public void setCredits(double credits) {
        this.credits = credits;
    }

    public void setUsageCount(int usageCount) {
        this.usageCount = usageCount;
    }

    public int getUsageCount() {
        return usageCount;
    }

    public String getInstructionDescription() {
        return instructionDescription;
    }

    public void setInstructionDescription(String instructionDescription) {
        this.instructionDescription = instructionDescription;
    }

    public String getGradingScale() {
        return gradingScale;
    }

    public void setGradingScale(String gradingScale) {
        this.gradingScale = gradingScale;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public GradingCriterion getGradingCriterion() {
        return gradingCriterion;
    }

    public void setGradingCriterion(GradingCriterion gradingCriterion) {
        this.gradingCriterion = gradingCriterion;
    }

    public Set<Feedback> getFeedbacks() {
        return feedbacks;
    }

    public void setFeedbacks(Set<Feedback> feedbacks) {
        this.feedbacks = feedbacks;
    }

    @PreRemove
    public void preRemove() {
        for (Feedback feedback : feedbacks) {
            feedback.setGradingInstruction(null);
        }
    }

    @Override
    public String toString() {
        return "GradingInstruction{" + "id=" + getId() + "'" + ", credits='" + getCredits() + "'" + ", gradingScale='" + getGradingScale() + "'" + ", instructionDescription='"
                + getInstructionDescription() + "'" + ", feedback='" + getFeedback() + "'" + ", usageCount='" + getUsageCount() + "'" + '}';
    }
}
