package de.tum.cit.aet.artemis.quiz.test_repository;

import org.springframework.transaction.annotation.Transactional;

public interface QuizExerciseTestRepositoryCustom {

    /**
     * Flushes pending persistence changes and clears the persistence context so subsequent repository queries reload entities from the database.
     */
    @Transactional
    void flushAndClearPersistenceContext();
}
