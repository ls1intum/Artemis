package de.tum.in.www1.artemis.web.rest.dto.examevent;

import java.util.Objects;

/**
 * A DTO for the {@link de.tum.in.www1.artemis.domain.exam.event.ProblemStatementUpdateEvent} entity.
 */
public class ProblemStatementUpdateEventDTO extends ExamLiveEventDTO {

    private String text;

    private String problemStatement;

    private long exerciseId;

    private String exerciseName;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getProblemStatement() {
        return problemStatement;
    }

    public void setProblemStatement(String problemStatement) {
        this.problemStatement = problemStatement;
    }

    public long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public String getExerciseName() {
        return exerciseName;
    }

    public void setExerciseName(String exerciseName) {
        this.exerciseName = exerciseName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ProblemStatementUpdateEventDTO that = (ProblemStatementUpdateEventDTO) o;
        return Objects.equals(text, that.text) && Objects.equals(problemStatement, that.problemStatement) && exerciseId == that.exerciseId
                && Objects.equals(exerciseName, that.exerciseName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), text, problemStatement, exerciseId, exerciseName);
    }
}
