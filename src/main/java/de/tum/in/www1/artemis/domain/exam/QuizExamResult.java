package de.tum.in.www1.artemis.domain.exam;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.quiz.QuizExamSubmission;

public class QuizExamResult implements QuizResult {

    private final StudentExam studentExam;

    private final Result result;

    public QuizExamResult(StudentExam studentExam, Result result) {
        this.studentExam = studentExam;
        this.result = result;
    }

    /**
     * Calculate score from score in points and max points of the quiz exam and set the result to result
     */
    public void evaluateQuizSubmission() {
        QuizExamSubmission quizExamSubmission = studentExam.getQuizExamSubmission();
        double scoreInPoints = quizExamSubmission.getScoreInPoints(studentExam.getQuizQuestions());
        double maxPoints = quizExamSubmission.getStudentExam().getQuizQuestionTotalPoints();
        double score = 100.0 * scoreInPoints / maxPoints;
        this.result.setScore(score, studentExam.getExam().getCourse());
    }

    @Override
    public Result getEntity() {
        return this.result;
    }

    @Override
    public void setRated(Boolean rated) {
        this.result.setRated(rated);
    }

    @Override
    public void setAssessmentType(AssessmentType assessmentType) {
        this.result.setAssessmentType(assessmentType);
    }

    @Override
    public void setCompletionDate(ZonedDateTime completionDate) {
        this.result.setCompletionDate(completionDate);
    }

    @Override
    public void setSubmission(Submission submission) {
        this.result.setSubmission(submission);
    }
}
