package de.tum.in.www1.artemis.repository.competency;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.competency.CompetencyJOL;

/**
 * Spring Data JPA repository for the {@link CompetencyJOL} entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface CompetencyJOLRepository extends JpaRepository<CompetencyJOL, Long> {

    Optional<CompetencyJOL> findByCompetencyIdAndUserId(long competencyId, long userId);

    @Query("""
            SELECT c.value
            FROM CompetencyJOL c
            WHERE c.competency.id = :competencyId
                AND c.user.id = :userId
            """)
    Optional<Integer> findValueByCompetencyIdAndUserId(@Param("competencyId") long competencyId, @Param("userId") long userId);

    @Query("""
            SELECT new de.tum.in.www1.artemis.repository.competency.JOLValueEntry(c.competency.id, c.value)
            FROM CompetencyJOL c
            WHERE c.user.id = :userId
                AND c.competency.course.id = :courseId
            """)
    Set<JOLValueEntry> findValuesForUserByCourseId(@Param("userId") long userId, @Param("courseId") long courseId);
}
