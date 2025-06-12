package de.tum.cit.aet.artemis.quiz.exception;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;

public class UnknownQuizQuestionTypeException extends EntityNotFoundException {

    public UnknownQuizQuestionTypeException(QuizQuestion question) {
        super("Artemis doesn't recognize \"" + ((question != null && question.getExercise() != null) ? question.getExercise().getType() : "unknown")
                + "\" as a valid quiz question type");
    }
}
