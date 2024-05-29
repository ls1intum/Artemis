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
            SELECT c
            FROM CompetencyJol c
            WHERE c.competency.id = :competencyId
                AND c.user.id = :userId
                AND c.judgementTime = (
                    SELECT MAX(c2.judgementTime)
                    FROM CompetencyJol c2
                    WHERE c2.competency.id = c.competency.id
                        AND c2.user.id = c.user.id
                )
            """)
    Optional<CompetencyJol> findLatestByCompetencyIdAndUserId(long competencyId, long userId);

    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyJolDTO(c.competency.id, c.value, c.judgementTime)
            FROM CompetencyJol c
            WHERE c.user.id = :userId
                AND c.competency.course.id = :courseId
                AND c.judgementTime = (
                    SELECT MAX(c2.judgementTime)
                    FROM CompetencyJol c2
                    WHERE c2.user.id = c.user.id
                        AND c2.competency.id = c.competency.id
                )
            """)
    Set<CompetencyJolDTO> findLatestJolValuesForUserByCourseId(@Param("userId") long userId, @Param("courseId") long courseId);
}
