package de.tum.cit.aet.artemis.lecture.repository;

import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.OnlineUnit;

/**
 * Spring Data JPA repository for the Online Unit entity.
 */
@Conditional(LectureEnabled.class)
@Repository
public interface OnlineUnitRepository extends ArtemisJpaRepository<OnlineUnit, Long> {

    @Query("""
            SELECT ou
            FROM OnlineUnit ou
                LEFT JOIN FETCH ou.competencyLinks cl
                LEFT JOIN FETCH cl.competency
            WHERE ou.id = :onlineUnitId
            """)
    Optional<OnlineUnit> findByIdWithCompetencies(@Param("onlineUnitId") long onlineUnitId);

    @NotNull
    default OnlineUnit findByIdWithCompetenciesElseThrow(long onlineUnitId) {
        return getValueElseThrow(findByIdWithCompetencies(onlineUnitId), onlineUnitId);
    }
}
