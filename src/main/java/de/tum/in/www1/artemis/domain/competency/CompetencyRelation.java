package de.tum.in.www1.artemis.domain.competency;

import javax.persistence.*;

import de.tum.in.www1.artemis.domain.DomainObject;

/**
 * This class models the relation between two competencies. Imagine a graph: (tail) --- type --> (head)
 * Because we want to keep this very generic (using the type attribute), this can not be modeled as a simple JPA relationship.
 */
@Entity
@Table(name = "learning_goal_relation")
public class CompetencyRelation extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "tail_learning_goal_id")
    private Competency tailCompetency;

    @ManyToOne
    @JoinColumn(name = "head_learning_goal_id")
    private Competency headCompetency;

    @Column(name = "type")
    @Convert(converter = RelationTypeConverter.class)
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

    public enum RelationType {
        /**
         * A generic relation between two competencies.
         */
        RELATES,
        /**
         * The tail competency assumes that the student already achieved the head competency.
         */
        ASSUMES,
        /**
         * The tail competency extends the head competency on the same topic in more detail.
         */
        EXTENDS,
        /**
         * The tail competency matches the head competency (e.g., a duplicate).
         */
        MATCHES
    }

    @Converter
    public static class RelationTypeConverter implements AttributeConverter<RelationType, String> {

        @Override
        public String convertToDatabaseColumn(RelationType type) {
            if (type == null) {
                return null;
            }

            return switch (type) {
                case RELATES -> "R";
                case ASSUMES -> "A";
                case EXTENDS -> "E";
                case MATCHES -> "M";
            };
        }

        @Override
        public RelationType convertToEntityAttribute(String value) {
            if (value == null) {
                return null;
            }

            return switch (value) {
                case "R" -> RelationType.RELATES;
                case "A" -> RelationType.ASSUMES;
                case "E" -> RelationType.EXTENDS;
                case "M" -> RelationType.MATCHES;
                default -> throw new IllegalArgumentException("Unknown RelationType: " + value);
            };
        }
    }
}
