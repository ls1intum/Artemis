package de.tum.in.www1.artemis.domain.exam.event;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import de.tum.in.www1.artemis.web.rest.dto.examevent.ProblemStatementUpdateEventDTO;

/**
 * An event indicating an update of the problem statement of an exercise during an exam.
 */
@Entity
@DiscriminatorValue(value = "P")
public class ProblemStatementUpdateEvent extends ExamLiveEvent {

    /**
     * optional text content of the instructor.
     */
    @Column(name = "textContent")
    private String textContent;

    @Column(name = "problem_statement")
    private String problemStatement;

    @Column(name = "exercise_id")
    private long exerciseId;

    @Column(name = "exercise_name")
    private String exerciseName;

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    public String getProblemStatement() {
        return problemStatement;
    }

    public void setProblemStatement(String problemStatement) {
        this.problemStatement = problemStatement;
    }

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public String getExerciseName() {
        return exerciseName;
    }

    public void setExerciseName(String exerciseName) {
        this.exerciseName = exerciseName;
    }

    @Override
    public ProblemStatementUpdateEventDTO asDTO() {
        var dto = new ProblemStatementUpdateEventDTO();
        super.populateDTO(dto);
        dto.setText(textContent);
        dto.setProblemStatement(problemStatement);
        dto.setExerciseId(exerciseId);
        dto.setExerciseName(exerciseName);
        return dto;
    }
}
