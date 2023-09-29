package de.tum.in.www1.artemis.domain.iris.message;

import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * An IrisExercisePlanMessageContent represents an Iris-generated plan to make changes to an exercise.
 * The plans may or may not have been edited by the user before execution.
 */
@Entity
@Table(name = "iris_exercise_plan_message_content")
@DiscriminatorValue(value = "EXERCISE_PLAN")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisExercisePlanMessageContent extends IrisMessageContent {
    
    /**
     * The different components of an exercise that can be changed by Iris.
     */
    public enum ExerciseComponent {
        PROBLEM_STATEMENT,
        SOLUTION_REPOSITORY,
        TEMPLATE_REPOSITORY,
        TEST_REPOSITORY
    }
    
    // Would like to be able to store these in a Map<ExerciseComponent, String>
    // but Hibernate would require creating a join table in the DB, and that's not worth it.
    @Nullable
    private String problemStatementPlan;
    @Nullable
    private String solutionRepositoryPlan;
    @Nullable
    private String templateRepositoryPlan;
    @Nullable
    private String testRepositoryPlan;
    
    // Required by JPA
    public IrisExercisePlanMessageContent() {}
    
    public IrisExercisePlanMessageContent(IrisMessage irisMessage, Map<ExerciseComponent, String> exercisePlan) {
        super(irisMessage);
        this.problemStatementPlan = exercisePlan.getOrDefault(ExerciseComponent.PROBLEM_STATEMENT, null);
        this.solutionRepositoryPlan = exercisePlan.getOrDefault(ExerciseComponent.SOLUTION_REPOSITORY, null);
        this.templateRepositoryPlan = exercisePlan.getOrDefault(ExerciseComponent.TEMPLATE_REPOSITORY, null);
        this.testRepositoryPlan = exercisePlan.getOrDefault(ExerciseComponent.TEST_REPOSITORY, null);
    }
    
    @Nullable
    public String getProblemStatementPlan() {
        return problemStatementPlan;
    }
    
    public void setProblemStatementPlan(String problemStatementPlan) {
        this.problemStatementPlan = problemStatementPlan;
    }
    
    @Nullable
    public String getSolutionRepositoryPlan() {
        return solutionRepositoryPlan;
    }
    
    public void setSolutionRepositoryPlan(String solutionRepositoryPlan) {
        this.solutionRepositoryPlan = solutionRepositoryPlan;
    }
    
    @Nullable
    public String getTemplateRepositoryPlan() {
        return templateRepositoryPlan;
    }
    
    public void setTemplateRepositoryPlan(@Nullable String templateRepositoryPlan) {
        this.templateRepositoryPlan = templateRepositoryPlan;
    }
    
    @Nullable
    public String getTestRepositoryPlan() {
        return testRepositoryPlan;
    }
    
    public void setTestRepositoryPlan(@Nullable String testRepositoryPlan) {
        this.testRepositoryPlan = testRepositoryPlan;
    }
    
    public void setPlan(ExerciseComponent component, @Nullable String plan) {
        switch (component) {
            case PROBLEM_STATEMENT:
                this.problemStatementPlan = plan;
                break;
            case SOLUTION_REPOSITORY:
                this.solutionRepositoryPlan = plan;
                break;
            case TEMPLATE_REPOSITORY:
                this.templateRepositoryPlan = plan;
                break;
            case TEST_REPOSITORY:
                this.testRepositoryPlan = plan;
                break;
        }
    }
    
    @Override
    public String getContentAsString() {
        return asMap().entrySet().stream()
                .map(entry -> entry.getKey().name() + ": " + entry.getValue())
                .collect(Collectors.joining(",\n", "Exercise Generation Plan:\n", ""));
    }
    
    public Map<ExerciseComponent, String> asMap() {
        Map<ExerciseComponent, String> map = new EnumMap<>(ExerciseComponent.class);
        if (problemStatementPlan != null) {
            map.put(ExerciseComponent.PROBLEM_STATEMENT, problemStatementPlan);
        }
        if (solutionRepositoryPlan != null) {
            map.put(ExerciseComponent.SOLUTION_REPOSITORY, solutionRepositoryPlan);
        }
        if (templateRepositoryPlan != null) {
            map.put(ExerciseComponent.TEMPLATE_REPOSITORY, templateRepositoryPlan);
        }
        if (testRepositoryPlan != null) {
            map.put(ExerciseComponent.TEST_REPOSITORY, testRepositoryPlan);
        }
        return map;
    }
    
    @Override
    public String toString() {
        return "IrisExercisePlanMessageContent{"
                + "message=" + (message == null ? "null" : message.getId())
                + ", problemStatementPlan='" + problemStatementPlan + '\''
                + ", solutionRepositoryPlan='" + solutionRepositoryPlan + '\''
                + ", templateRepositoryPlan='" + templateRepositoryPlan + '\''
                + ", testRepositoryPlan='" + testRepositoryPlan + '\''
                + '}';
    }
}
