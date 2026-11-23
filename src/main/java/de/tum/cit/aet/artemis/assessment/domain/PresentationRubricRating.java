package de.tum.cit.aet.artemis.assessment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;

/**
 * The level a grader chose for one rubric criterion of a presentation assessment instance.
 */
@Entity
@Table(name = "presentation_rubric_rating")
public class PresentationRubricRating extends DomainObject {

    public static final String ENTITY_NAME = "presentationRubricRating";

    /** The longest comment of a rating. Keep in sync with MAX_COMMENT_LENGTH in presentation-rubric.util.ts. */
    public static final int MAX_COMMENT_LENGTH = 1000;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instance_id", nullable = false)
    @Parent
    private PresentationAssessmentInstance instance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criterion_id", nullable = false)
    private PresentationRubricCriterion criterion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", nullable = false)
    private PresentationRubricLevel level;

    @Column(name = "comment", length = MAX_COMMENT_LENGTH)
    private String comment;

    public PresentationAssessmentInstance getInstance() {
        return instance;
    }

    public void setInstance(PresentationAssessmentInstance instance) {
        this.instance = instance;
    }

    public PresentationRubricCriterion getCriterion() {
        return criterion;
    }

    public void setCriterion(PresentationRubricCriterion criterion) {
        this.criterion = criterion;
    }

    public PresentationRubricLevel getLevel() {
        return level;
    }

    public void setLevel(PresentationRubricLevel level) {
        this.level = level;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
