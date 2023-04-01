package de.tum.in.www1.artemis.domain.exam;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.quiz.QuizExamSubmission;

@Entity
@DiscriminatorValue("QE")
public class QuizExamResult extends Result {

    @Transient
    private StudentExam studentExam;

    public QuizExamResult() {

    }

    public QuizExamResult(StudentExam studentExam) {
        this.studentExam = studentExam;
    }

    @Override
    public void evaluateQuizSubmission() {
        QuizExamSubmission quizExamSubmission = (QuizExamSubmission) getSubmission();
        double score = quizExamSubmission.getScoreInPointsForSubmission(studentExam.getQuizQuestions());
        double maxPoints = quizExamSubmission.getStudentExam().getQuizQuestionTotalPoints();
        score = 100.0 * score / maxPoints;
        setScore(score, studentExam.getExam().getCourse());
    }
}
