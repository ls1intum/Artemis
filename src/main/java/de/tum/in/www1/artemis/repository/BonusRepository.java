package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Bonus;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Bonus entity
 */
@Repository
public interface BonusRepository extends JpaRepository<Bonus, Long> {

    default Bonus findByIdElseThrow(Long bonusId) throws EntityNotFoundException {
        return findById(bonusId).orElseThrow(() -> new EntityNotFoundException("Bonus", bonusId));
    }

    @Query("""
            SELECT gs.bonusFrom
            FROM GradingScale gs
            WHERE gs.exam.id = :#{#examId}
            """)
    Set<Bonus> findAllByBonusToExamId(@Param("examId") Long examId);

    boolean existsByBonusToGradingScaleId(@Param("bonusToGradingScaleId") Long bonusToGradingScaleId);

}
