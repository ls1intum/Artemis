package de.tum.in.www1.artemis.domain.exam;

import java.util.List;

import de.tum.in.www1.artemis.domain.quiz.QuizQuestion;

public interface QuizExamExercise extends ExamExercise {

    List<QuizQuestion> getQuizQuestions();

    Boolean isRandomizeQuestionOrder();

    Boolean isQuizExam();
}
