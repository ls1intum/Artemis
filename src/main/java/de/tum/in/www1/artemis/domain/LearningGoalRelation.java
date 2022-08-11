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
        GENERIC,
        /**
         * The tail learning goal is a prerequisite for the head learning goal.
         */
        PREREQUISITE,
        /**
         * The head learning goal is on the same topic but more detailed than the tail learning goal.
         */
        CONSECUTIVE;
    }

    @Converter
    public static class RelationTypeConverter implements AttributeConverter<RelationType, String> {

        @Override
        public String convertToDatabaseColumn(RelationType type) {
            if (type == null)
                return null;

            return switch (type) {
                case GENERIC -> "G";
                case PREREQUISITE -> "P";
                case CONSECUTIVE -> "C";
            };
        }

        @Override
        public RelationType convertToEntityAttribute(String value) {
            if (value == null)
                return null;

            return switch (value) {
                case "G" -> RelationType.GENERIC;
                case "P" -> RelationType.PREREQUISITE;
                case "C" -> RelationType.CONSECUTIVE;
                default -> throw new IllegalArgumentException("Unknown RelationType: " + value);
            };
        }
    }
}
