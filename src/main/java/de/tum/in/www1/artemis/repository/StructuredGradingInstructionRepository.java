package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.StructuredGradingInstruction;

/**
 * Spring Data JPA repository for the StructuredGradingInstruction entity.
 */
@Repository
public interface StructuredGradingInstructionRepository extends JpaRepository<StructuredGradingInstruction, Long> {

    List<StructuredGradingInstruction> findByGradingCriterionId(long gradingCriterionId);

}
