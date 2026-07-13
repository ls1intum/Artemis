package de.tum.cit.aet.artemis.quiz.test_repository;

import jakarta.persistence.EntityManager;

public class QuizExerciseTestRepositoryCustomImpl implements QuizExerciseTestRepositoryCustom {

    private final EntityManager entityManager;

    public QuizExerciseTestRepositoryCustomImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void flushAndClearPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }
}
