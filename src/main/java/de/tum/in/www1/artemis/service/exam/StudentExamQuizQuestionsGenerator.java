package de.tum.in.www1.artemis.service.exam;

import java.util.List;

import de.tum.in.www1.artemis.domain.quiz.QuizQuestion;

public interface StudentExamQuizQuestionsGenerator {

    List<QuizQuestion> generateQuizQuestions();
}
