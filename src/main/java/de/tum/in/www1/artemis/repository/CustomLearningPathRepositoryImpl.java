package de.tum.in.www1.artemis.repository;

import java.util.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import jakarta.transaction.Transactional;

import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.competency.*;

@Repository
public class CustomLearningPathRepositoryImpl implements CustomLearningPathRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public Optional<LearningPath> findWithEagerCompetenciesAndProgressAndLearningObjectsAndCompletedUsersById(long learningPathId) {
        List<LearningPath> resultList = entityManager.createQuery("""
                    SELECT learningPath
                    FROM LearningPath learningPath
                        LEFT JOIN learningPath.user user
                        LEFT JOIN learningPath.competencies competencies
                        LEFT JOIN competencies.userProgress progress
                            ON progress.user = user
                        LEFT JOIN competencies.lectureUnits lectureUnits
                        LEFT JOIN lectureUnits.completedUsers completedUsers
                            ON completedUsers.user = user
                        LEFT JOIN competencies.exercises exercises
                        LEFT JOIN exercises.studentParticipations studentParticipations
                            ON studentParticipations.student = user
                    WHERE learningPath.id = :learningPathId
                """, LearningPath.class).setParameter("learningPathId", learningPathId)
                .setHint("javax.persistence.fetchgraph", entityManager.getEntityGraph("LearningPath.withEagerCompetenciesAndProgressAndLearningObjectsAndCompletedUsers"))
                .setMaxResults(1).getResultList();
        return resultList.stream().findFirst();
    }
}
