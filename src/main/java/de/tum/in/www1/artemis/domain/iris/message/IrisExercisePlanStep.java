package de.tum.in.www1.artemis.domain.iris.message;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "iris_exercise_plan_step")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id", scope = IrisExercisePlanStep.class)
public class IrisExercisePlanStep extends DomainObject {

    public enum ExecutionStage {
        NOT_EXECUTED, IN_PROGRESS, FAILED, COMPLETE
    }

    @ManyToOne(optional = false)
    @JoinColumn(name = "exercise_plan_id")
    private IrisExercisePlan plan;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "exercise_component")
    private ExerciseComponent component;

    @NotNull
    @Column(name = "instructions")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_stage")
    private ExecutionStage executionStage;

    public IrisExercisePlanStep() {
    }

    public IrisExercisePlanStep(ExerciseComponent component, String instructions) {
        this.component = component;
        this.instructions = instructions;
        this.executionStage = ExecutionStage.NOT_EXECUTED;
    }

    public IrisExercisePlan getPlan() {
        return plan;
    }

    /**
     * Sets the plan this step belongs to.
     * Do not use this method directly, use IrisExercisePlan#addStep instead.
     *
     * @param plan the plan this step belongs to
     */
    void setPlan(IrisExercisePlan plan) {
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

    @Override
    public String toString() {
        return "IrisExercisePlanStep{" + "id=" + getId() + ", component=" + component + ", instructions='" + instructions + '\'' + ", executionStage=" + executionStage + '}';
    }

}
