package de.tum.in.www1.artemis.domain.iris.message;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "iris_exercise_plan_step")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class IrisExercisePlanStep extends DomainObject {

    public enum ExecutionStage {
        NOT_EXECUTED, IN_PROGRESS, FAILED, COMPLETE
    }

    @ManyToOne
    @JoinColumn(name = "exercise_plan_id")
    private IrisExercisePlan plan;

    @NotNull
    @Enumerated(value = EnumType.STRING)
    @Column(name = "exercise_component")
    private ExerciseComponent component;

    @NotNull
    @Column(name = "instructions")
    private String instructions;

    @Column(name = "execution_stage")
    @Enumerated(EnumType.STRING)
    private ExecutionStage executionStage;

    public IrisExercisePlanStep() {
    }

    public IrisExercisePlanStep(IrisExercisePlan plan, ExerciseComponent component, String instructions) {
        this.plan = plan;
        this.component = component;
        this.instructions = instructions;
        this.executionStage = ExecutionStage.NOT_EXECUTED;
    }

    public IrisExercisePlan getPlan() {
        return plan;
    }

    public void setPlan(IrisExercisePlan plan) {
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

    public ExecutionStage getExecutionStage() {
        return executionStage;
    }

    public void setExecutionStage(ExecutionStage executionStage) {
        this.executionStage = executionStage;
    }
}
