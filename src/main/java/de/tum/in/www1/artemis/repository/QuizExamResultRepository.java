package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.exam.QuizExamResult;

/**
 * Spring Data JPA repository for the QuizExamResult entity.
 */
@SuppressWarnings("unused")
@Repository
public interface QuizExamResultRepository extends JpaRepository<QuizExamResult, Long> {
}
