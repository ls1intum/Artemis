package de.tum.in.www1.artemis.domain.iris.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.domain.DomainObject;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

@Entity
@Table(name = "iris_exercise_plan_component")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisExercisePlanComponent extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "exercise_plan_id")
    private IrisExercisePlanMessageContent exercisePlan;
    
    @NotNull
    @Enumerated(value = EnumType.STRING)
    @Column(name = "exercise_component")
    private ExerciseComponent component;
    
    @NotNull
    @Column(name = "instructions")
    private String instructions;
    
    public IrisExercisePlanComponent() {
    }
    
    public IrisExercisePlanComponent(IrisExercisePlanMessageContent exercisePlan, ExerciseComponent component, String instructions) {
        this.exercisePlan = exercisePlan;
        this.component = component;
        this.instructions = instructions;
    }
    
    public IrisExercisePlanMessageContent getExercisePlan() {
        return exercisePlan;
    }
    
    public void setExercisePlan(IrisExercisePlanMessageContent plan) {
        this.exercisePlan = plan;
    }
    
    public ExerciseComponent getComponent() {
        return component;
    }
    
    public void setComponent(ExerciseComponent component) {
        this.component = component;
    }
    
    public String getInstructions() {
        return instructions;
    }
    
    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }
    
}
