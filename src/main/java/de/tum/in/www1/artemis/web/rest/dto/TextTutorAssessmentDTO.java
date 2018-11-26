package de.tum.in.www1.artemis.web.rest.dto;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;

import java.util.List;

public class TextTutorAssessmentDTO {
    private TextExercise exercise;
    private TextSubmission submission;
    private Result result;
    private List<Feedback> assessments;

    public TextExercise getExercise() {
        return exercise;
    }

    public void setExercise(TextExercise exercise) {
        this.exercise = exercise;
    }

    public TextSubmission getSubmission() {
        return submission;
    }

    public void setSubmission(TextSubmission submission) {
        this.submission = submission;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public List<Feedback> getAssessments() {
        return assessments;
    }

    public void setAssessments(List<Feedback> assessments) {
        this.assessments = assessments;
    }
}
