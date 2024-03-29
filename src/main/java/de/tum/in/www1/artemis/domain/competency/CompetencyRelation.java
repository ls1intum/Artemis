package de.tum.in.www1.artemis.domain.competency;

import jakarta.persistence.*;

import de.tum.in.www1.artemis.domain.DomainObject;

/**
 * This class models the relation between two competencies. Imagine a graph: (tail) --- type --> (head)
 * Because we want to keep this very generic (using the type attribute), this can not be modeled as a simple JPA relationship.
 */
@Entity
@Table(name = "competency_relation")
public class CompetencyRelation extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "tail_competency_id", nullable = false)
    private Competency tailCompetency;

    @ManyToOne
    @JoinColumn(name = "head_competency_id", nullable = false)
    private Competency headCompetency;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private RelationType type;

    public Competency getTailCompetency() {
        return tailCompetency;
    }

    public void setTailCompetency(Competency tailCompetency) {
        this.tailCompetency = tailCompetency;
    }

    public Competency getHeadCompetency() {
        return headCompetency;
    }

    public void setHeadCompetency(Competency headCompetency) {
        this.headCompetency = headCompetency;
    }

    public RelationType getType() {
        return type;
    }

    public void setType(RelationType type) {
        this.type = type;
    }

}
