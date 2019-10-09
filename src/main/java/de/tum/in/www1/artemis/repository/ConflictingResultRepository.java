package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.modeling.ConflictingResult;

@Repository
public interface ConflictingResultRepository extends JpaRepository<ConflictingResult, Long> {
}
