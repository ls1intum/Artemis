package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.LearningGoal;

/**
 * Spring Data JPA repository for the Lecture Module entity.
 */
@Repository
public interface LearningGoalRepository extends JpaRepository<LearningGoal, Long> {
}
