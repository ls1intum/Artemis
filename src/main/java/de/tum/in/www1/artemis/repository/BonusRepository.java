package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Bonus;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Bonus entity
 */
@Profile(PROFILE_CORE)
@Repository
public interface BonusRepository extends JpaRepository<Bonus, Long> {

    default Bonus findByIdElseThrow(long bonusId) throws EntityNotFoundException {
        return findById(bonusId).orElseThrow(() -> new EntityNotFoundException("Bonus", bonusId));
    }

    @Query("""
            SELECT gs.bonusFrom
            FROM GradingScale gs
            WHERE gs.exam.id = :examId
            """)
    Set<Bonus> findAllByBonusToExamId(@Param("examId") long examId);

    boolean existsByBonusToGradingScaleId(long bonusToGradingScaleId);

}
