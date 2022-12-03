package de.tum.in.www1.artemis.domain;

import javax.persistence.*;

/**
 * This class models the relation between two learning goal. Imagine a graph: (tail) --- type --> (head)
 * Because we want to keep this very generic (using the type attribute), this can not be modeled as a simple JPA relationship.
 */
@Entity
@Table(name = "learning_goal_relation")
public class LearningGoalRelation extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "tail_learning_goal_id")
    private LearningGoal tailLearningGoal;

    @ManyToOne
    @JoinColumn(name = "head_learning_goal_id")
    private LearningGoal headLearningGoal;

    @Column(name = "type")
    @Convert(converter = RelationTypeConverter.class)
    private RelationType type;

    public LearningGoal getTailLearningGoal() {
        return tailLearningGoal;
    }

    public void setTailLearningGoal(LearningGoal tailLearningGoal) {
        this.tailLearningGoal = tailLearningGoal;
    }

    public LearningGoal getHeadLearningGoal() {
        return headLearningGoal;
    }

    public void setHeadLearningGoal(LearningGoal headLearningGoal) {
        this.headLearningGoal = headLearningGoal;
    }

    public RelationType getType() {
        return type;
    }

    public void setType(RelationType type) {
        this.type = type;
    }

    public enum RelationType {
        /**
         * A generic relation between two learning goals.
         */
        RELATES,
        /**
         * The tail learning goal assumes that the student already achieved the head learning goal.
         */
        ASSUMES,
        /**
         * The tail learning goal extends the head learning goal on the same topic in more detail.
         */
        EXTENDS,
        /**
         * The tail learning goal matches the head learning goal (e.g., a duplicate).
         */
        MATCHES;
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
