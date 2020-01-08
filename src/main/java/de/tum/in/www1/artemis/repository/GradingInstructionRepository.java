package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.GradingInstruction;

/**
 * Spring Data JPA repository for the GradingInstruction entity.
 */
@Repository
public interface GradingInstructionRepository extends JpaRepository<GradingInstruction, Long> {

    List<GradingInstruction> findByExerciseId(long exerciseId);

}
