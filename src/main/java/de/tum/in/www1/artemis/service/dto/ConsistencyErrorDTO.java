package de.tum.in.www1.artemis.service.dto;

import java.util.Objects;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;

/**
 * A DTO representing a consistency error
 */
public class ConsistencyErrorDTO {

    private ProgrammingExercise programmingExercise;

    private ErrorType type;

    public ConsistencyErrorDTO(ProgrammingExercise programmingExercise, ErrorType type) {
        this.programmingExercise = programmingExercise;
        this.type = type;
    }

    public ProgrammingExercise getProgrammingExercise() {
        return programmingExercise;
    }

    public void setProgrammingExercise(ProgrammingExercise programmingExercise) {
        this.programmingExercise = programmingExercise;
    }

    public ErrorType getType() {
        return type;
    }

    public void setType(ErrorType type) {
        this.type = type;
    }

    public enum ErrorType {

        IS_LOCAL_SIMULATION("IS_LOCAL_SIMULATION"), VCS_PROJECT_MISSING("VCS_PROJECT_MISSING"), TEMPLATE_REPO_MISSING("TEMPLATE_REPO_MISSING"),
        SOLUTION_REPO_MISSING("SOLUTION_REPO_MISSING"), TEST_REPO_MISSING("TEST_REPO_MISSING"), TEMPLATE_BUILD_PLAN_MISSING("TEMPLATE_BUILD_PLAN_MISSING"),
        SOLUTION_BUILD_PLAN_MISSING("SOLUTION_BUILD_PLAN_MISSING");

        ErrorType(final String value) {
            // This empty constructor is added to avoid Codacy issues
        }

        @Override
        public String toString() {
            return name();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ConsistencyErrorDTO that = (ConsistencyErrorDTO) obj;
        return Objects.equals(getProgrammingExercise(), that.getProgrammingExercise()) && getType() == that.getType();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getProgrammingExercise(), getType());
    }

    @Override
    public String toString() {
        return "ConsistencyErrorDTO{" + "programmingExercise='" + programmingExercise.getTitle() + "', type='" + type.name() + "'}";
    }
}
