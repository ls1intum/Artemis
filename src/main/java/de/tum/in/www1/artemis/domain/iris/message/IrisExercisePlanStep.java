package de.tum.in.www1.artemis.domain.iris.message;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "iris_exercise_plan_component")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisExercisePlanStep extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "exercise_plan_id")
    @JsonIgnore
    private IrisExercisePlanMessageContent plan;

    @NotNull
    @Enumerated(value = EnumType.STRING)
    @Column(name = "exercise_component")
    private ExerciseComponent component;

    @NotNull
    @Column(name = "instructions")
    private String instructions;

    public IrisExercisePlanStep() {
    }

    public IrisExercisePlanStep(IrisExercisePlanMessageContent plan, ExerciseComponent component, String instructions) {
        this.plan = plan;
        this.component = component;
        this.instructions = instructions;
    }

    public IrisExercisePlanMessageContent getPlan() {
        return plan;
    }

    public void setPlan(IrisExercisePlanMessageContent plan) {
        this.plan = plan;
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
