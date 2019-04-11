package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.ShortAnswerMapping;

/**
 * Spring Data JPA repository for the ShortAnswerMapping entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ShortAnswerMappingRepository extends JpaRepository<ShortAnswerMapping, Long> {

}
