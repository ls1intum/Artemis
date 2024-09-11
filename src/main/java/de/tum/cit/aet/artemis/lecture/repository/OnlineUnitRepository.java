package de.tum.cit.aet.artemis.lecture.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.domain.OnlineUnit;

/**
 * Spring Data JPA repository for the Online Unit entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface OnlineUnitRepository extends ArtemisJpaRepository<OnlineUnit, Long> {

    @Query("""
            SELECT ou
            FROM OnlineUnit ou
                LEFT JOIN FETCH ou.competencies
            WHERE ou.id = :onlineUnitId
            """)
    Optional<OnlineUnit> findByIdWithCompetencies(@Param("onlineUnitId") long onlineUnitId);

    @NotNull
    default OnlineUnit findByIdWithCompetenciesElseThrow(long onlineUnitId) {
        return getValueElseThrow(findByIdWithCompetencies(onlineUnitId), onlineUnitId);
    }
}
