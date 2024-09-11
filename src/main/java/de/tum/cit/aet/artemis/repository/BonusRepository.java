package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.Bonus;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the Bonus entity
 */
@Profile(PROFILE_CORE)
@Repository
public interface BonusRepository extends ArtemisJpaRepository<Bonus, Long> {

    @Query("""
            SELECT gs.bonusFrom
            FROM GradingScale gs
            WHERE gs.exam.id = :examId
            """)
    Set<Bonus> findAllByBonusToExamId(@Param("examId") long examId);

    boolean existsByBonusToGradingScaleId(long bonusToGradingScaleId);

}
