package de.tum.in.www1.artemis.domain;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * A Structured Grading Instruction.
 */
@Entity
@Table(name = "grading_instruction")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class GradingInstruction implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // the score students get if this grading instruction is applicable
    @Column(name = "credits")
    private double credits;

    // describes the level of performance (e.g. "poor" or "average" )
    @Column(name = "grading_scale")
    private String gradingScale;

    @Column(name = "instruction_description")
    @Lob
    private String instructionDescription;

    @Column(name = "feedback")
    @Lob
    private String feedback;

    // how often points for this grading instruction should be calculated if the criteria is applicable more than once for the same submission (e.g. to prevent consequential
    // errors)
    @Column(name = "usage_count")
    private int usageCount;

    @ManyToOne(fetch = FetchType.LAZY)
    private GradingCriterion gradingCriterion;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public double getCredits() {
        return credits;
    }

    public GradingInstruction credits(double credits) {
        this.credits = credits;
        return this;
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

    public GradingInstruction usageCount(int usageCount) {
        this.usageCount = usageCount;
        return this;
    }

    public String getInstructionDescription() {
        return instructionDescription;
    }

    public GradingInstruction instructionDescription(String instructionDescription) {
        this.instructionDescription = instructionDescription;
        return this;
    }

    public void setInstructionDescription(String instructionDescription) {
        this.instructionDescription = instructionDescription;
    }

    public String getGradingScale() {
        return gradingScale;
    }

    public GradingInstruction gradingScale(String gradingScale) {
        this.gradingScale = gradingScale;
        return this;
    }

    public void setGradingScale(String gradingScale) {
        this.gradingScale = gradingScale;
    }

    public String getFeedback() {
        return feedback;
    }

    public GradingInstruction feedback(String feedback) {
        this.instructionDescription = feedback;
        return this;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public GradingCriterion getGradingCriterion() {
        return gradingCriterion;
    }

    public GradingInstruction gradingCriteria(GradingCriterion gradingCriterion) {
        this.gradingCriterion = gradingCriterion;
        return this;
    }

    public void setGradingCriterion(GradingCriterion gradingCriterion) {
        this.gradingCriterion = gradingCriterion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GradingInstruction gradingInstruction = (GradingInstruction) o;
        if (gradingInstruction.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), gradingInstruction.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "GradingInstruction{" + "id=" + getId() + "'" + ", credits='" + getCredits() + "'" + ", gradingScale='" + getGradingScale() + "'" + ", instructionDescription='"
                + getInstructionDescription() + "'" + ", feedback='" + getFeedback() + "'" + ", usageCount='" + getUsageCount() + "'" + '}';
    }
}
