package de.tum.cit.aet.artemis.exam.service;

import java.util.List;

import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;

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
