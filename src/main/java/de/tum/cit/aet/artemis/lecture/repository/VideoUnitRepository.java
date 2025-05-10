package de.tum.cit.aet.artemis.lecture.repository;

import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.VideoUnit;

/**
 * Spring Data JPA repository for the Video Unit entity.
 */
@Conditional(LectureEnabled.class)
@Repository
public interface VideoUnitRepository extends ArtemisJpaRepository<VideoUnit, Long> {

    @Query("""
            SELECT vu
            FROM VideoUnit vu
                LEFT JOIN FETCH vu.competencyLinks cl
                LEFT JOIN FETCH cl.competency
            WHERE vu.id = :videoUnitId
            """)
    Optional<VideoUnit> findByIdWithCompetencies(@Param("videoUnitId") long videoUnitId);

    @NotNull
    default VideoUnit findByIdWithCompetenciesElseThrow(long videoUnitId) {
        return getValueElseThrow(findByIdWithCompetencies(videoUnitId), videoUnitId);
    }
}
