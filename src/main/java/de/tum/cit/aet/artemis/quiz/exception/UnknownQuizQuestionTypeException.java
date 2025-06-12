package de.tum.cit.aet.artemis.quiz.exception;

import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;

public class UnknownQuizQuestionTypeException extends RuntimeException {

    public UnknownQuizQuestionTypeException(QuizQuestion question) {
        super("Artemis doesn't recognize \"" + question.getExercise().getType() + "\" as a valid quiz question type");
    }
}
