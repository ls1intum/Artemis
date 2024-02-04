package de.tum.in.www1.artemis.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.QuizGroup;

/**
 * Spring Data JPA repository for the QuizGroup entity.
 */
@Profile("core")
@SuppressWarnings("unused")
@Repository
public interface QuizGroupRepository extends JpaRepository<QuizGroup, Long> {
}
