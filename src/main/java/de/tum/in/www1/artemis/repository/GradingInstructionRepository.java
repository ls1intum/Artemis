package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.GradingInstruction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for the GradingInstruction entity.
 */
@Repository
public interface GradingInstructionRepository extends JpaRepository<GradingInstruction, Long> {
    List<GradingInstruction> findByExerciseId(@Param("exerciseId") Long exerciseId);

}
