package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.GradingInstruction;

/**
 * Spring Data JPA repository for the GradingInstruction entity.
 */
@Repository
public interface GradingInstructionRepository extends JpaRepository<GradingInstruction, Long> {

    GradingInstruction findById(long id);

}
