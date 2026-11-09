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
 * A level of the rating scale of a rubric criterion. Its value is its position plus one.
 */
@Entity
@Table(name = "presentation_rubric_level")
public class PresentationRubricLevel extends DomainObject {

    public static final String ENTITY_NAME = "presentationRubricLevel";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criterion_id", nullable = false)
    @Parent
    private PresentationRubricCriterion criterion;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "description", length = PresentationRubricCriterion.MAX_DESCRIPTION_LENGTH)
    private String description;

    public PresentationRubricCriterion getCriterion() {
        return criterion;
    }

    public void setCriterion(PresentationRubricCriterion criterion) {
        this.criterion = criterion;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
