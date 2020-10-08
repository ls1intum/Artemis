package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.LearningGoal;

/**
 * Spring Data JPA repository for the LearningGoal entity.
 */
public interface LearningGoalRepository extends JpaRepository<LearningGoal, Long> {
}
