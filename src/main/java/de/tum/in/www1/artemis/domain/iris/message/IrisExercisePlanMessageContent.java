package de.tum.in.www1.artemis.domain.iris.message;

import com.fasterxml.jackson.annotation.JsonInclude;

import javax.persistence.*;
import java.util.Iterator;
import java.util.List;

/**
 * An IrisExercisePlanMessageContent represents an Iris-generated plan to make changes to an exercise.
 * The plans may or may not have been edited by the user before execution.
 */
@Entity
@Table(name = "iris_exercise_plan_message_content")
@DiscriminatorValue(value = "EXERCISE_PLAN")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisExercisePlanMessageContent extends IrisMessageContent implements Iterator<ExercisePlanComponent> {
    
    @OneToMany(mappedBy = "exercisePlan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExercisePlanComponent> components;
    
    @Column(name = "current_component_index")
    private short currentComponentIndex = 0;
    
    @Transient
    private boolean executing = false;
    
    public List<ExercisePlanComponent> getComponents() {
        return components;
    }
    
    public void setComponents(List<ExercisePlanComponent> components) {
        this.components = components;
    }
    
    public short getCurrentComponentIndex() {
        return currentComponentIndex;
    }
    
    public void setCurrentComponentIndex(short currentInstructionIndex) {
        this.currentComponentIndex = currentInstructionIndex;
    }
    
    public boolean isExecuting() {
        return executing;
    }
    
    public void setExecuting(boolean executing) {
        this.executing = executing;
    }
    
    @Override
    public boolean hasNext() {
        return currentComponentIndex < components.size();
    }
    
    @Override
    public ExercisePlanComponent next() {
        if (!hasNext()) {
            throw new IllegalStateException("No more instructions available");
        }
        return components.get(currentComponentIndex++);
    }
    
    @Override
    public String getContentAsString() {
        var sb = new StringBuilder("Exercise plan:\n");
        for (var entry : components) {
            sb.append(entry.getComponent().toString()).append(": \"").append(entry.getInstructions()).append("\"\n");
        }
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return "IrisExercisePlanMessageContent{"
                + "message=" + (message == null ? "null" : message.getId())
                + ", components=" + components
                + ", currentInstructionIndex=" + currentComponentIndex
                + '}';
    }
}
