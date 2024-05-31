package de.tum.in.www1.artemis.repository.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.competency.CompetencyJol;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyJolDTO;

/**
 * Spring Data JPA repository for the {@link CompetencyJol} entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface CompetencyJolRepository extends JpaRepository<CompetencyJol, Long> {

    @Query("""
            SELECT jol
            FROM CompetencyJol jol
            WHERE jol.competency.id = :competencyId
                AND jol.user.id = :userId
                AND jol.judgementTime = (
                    SELECT MAX(jol2.judgementTime)
                    FROM CompetencyJol jol2
                    WHERE jol2.competency.id = jol.competency.id
                        AND jol2.user.id = jol.user.id
                )
            """)
    Optional<CompetencyJol> findLatestByCompetencyIdAndUserId(@Param("competencyId") long competencyId, @Param("userId") long userId);

    @Query("""
            SELECT jol
            FROM CompetencyJol jol
            WHERE jol.competency.id = :competencyId
                AND jol.user.id = :userId
                AND jol.id != :jolIdToExclude
                AND jol.judgementTime = (
                    SELECT MAX(jol2.judgementTime)
                    FROM CompetencyJol jol2
                    WHERE jol2.competency.id = jol.competency.id
                        AND jol2.user.id = jol.user.id
                        AND jol2.id != :jolIdToExclude
                )
            """)
    Optional<CompetencyJol> findLatestByCompetencyIdAndUserIdExceptJolId(@Param("competencyId") long competencyId, @Param("userId") long userId,
            @Param("jolIdToExclude") long jolIdToExclude);

    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyJolDTO(jol.id, jol.competency.id, jol.value, jol.judgementTime, jol.competencyProgress, jol.competencyConfidence)
            FROM CompetencyJol jol
            WHERE jol.user.id = :userId
                AND jol.competency.course.id = :courseId
                AND jol.judgementTime = (
                    SELECT MAX(jol2.judgementTime)
                    FROM CompetencyJol jol2
                    WHERE jol2.user.id = jol.user.id
                        AND jol2.competency.id = jol.competency.id
                    )
            """)
    Set<CompetencyJolDTO> findLatestJolValuesForUserByCourseId(@Param("userId") long userId, @Param("courseId") long courseId);

    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyJolDTO(jol.id, jol.competency.id, jol.value, jol.judgementTime, jol.competencyProgress, jol.competencyConfidence)
            FROM CompetencyJol jol
            WHERE jol.user.id = :userId
                AND jol.competency.course.id = :courseId
                AND jol.id NOT IN :jolIdsToExclude
                AND jol.judgementTime = (
                    SELECT MAX(jol2.judgementTime)
                    FROM CompetencyJol jol2
                    WHERE jol2.user.id = jol.user.id
                        AND jol2.competency.id = jol.competency.id
                        AND jol2.id NOT IN :jolIdsToExclude
                    )
            """)
    Set<CompetencyJolDTO> findLatestJolValuesForUserByCourseIdExcludeJolIds(@Param("userId") long userId, @Param("courseId") long courseId,
            @Param("jolIdsToExclude") Set<Long> jolIdsToExclude);
}
