package de.tum.in.www1.artemis.service.exam;

import java.util.List;

import de.tum.in.www1.artemis.domain.quiz.QuizQuestion;

/**
 * Service Interface for generating quiz questions for an exam
 */
public interface ExamQuizQuestionsGenerator {

    /**
     * Generates quiz questions for an exam
     *
     * @param examId the id of the exam for which quiz questions should be generated
     * @return the list of generated quiz questions
     */
    List<QuizQuestion> generateQuizQuestionsForExam(long examId);
}
