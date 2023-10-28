package de.tum.in.www1.artemis.domain.iris.message;

import java.util.List;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An IrisExercisePlanMessageContent represents an Iris-generated plan to make changes to an exercise.
 * The plans may or may not have been edited by the user before execution.
 */
@Entity
@Table(name = "iris_exercise_plan_message_content")
@DiscriminatorValue(value = "EXERCISE_PLAN")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisExercisePlanMessageContent extends IrisMessageContent {

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<IrisExercisePlanStep> steps;

    @Column(name = "current_component_index")
    private short currentStepIndex = 0;

    public List<IrisExercisePlanStep> getSteps() {
        return steps;
    }

    public void setSteps(List<IrisExercisePlanStep> steps) {
        this.steps = steps;
    }

    public short getCurrentStepIndex() {
        return currentStepIndex;
    }

    public void setCurrentStepIndex(short currentInstructionIndex) {
        this.currentStepIndex = currentInstructionIndex;
    }

    @Override
    public String getContentAsString() {
        var sb = new StringBuilder("Exercise plan:\n");
        for (var entry : steps) {
            sb.append(entry.getComponent().toString()).append(": \"").append(entry.getInstructions()).append("\"\n");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "IrisExercisePlanMessageContent{" + "message=" + (message == null ? "null" : message.getId()) + ", components=" + steps + ", currentInstructionIndex="
                + currentStepIndex + '}';
    }
}
