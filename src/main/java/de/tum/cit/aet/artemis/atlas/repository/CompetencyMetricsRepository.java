package de.tum.cit.aet.artemis.atlas.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyJolDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.CompetencyInformationDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.CompetencyProgressDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.MapEntryLongLong;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository to fetch competency related metrics.
 */
@Profile(PROFILE_CORE)
@Repository
public interface CompetencyMetricsRepository extends ArtemisJpaRepository<Competency, Long> {

    /**
     * Get the competency information for all competencies in a course.
     *
     * @param courseId the id of the course
     * @return the competency information for all competencies in the course
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.atlas.dto.metrics.CompetencyInformationDTO(c.id, c.title, c.description, c.taxonomy, c.softDueDate, c.optional, c.masteryThreshold)
            FROM Competency c
            WHERE c.course.id = :courseId
            """)
    Set<CompetencyInformationDTO> findAllCompetencyInformationByCourseId(@Param("courseId") long courseId);

    /**
     * Get the exercise ids for all exercises that are associated with a set of competencies.
     *
     * @param competencyIds the ids of the competencies
     * @return the exercise ids for all exercises that are associated with the competencies
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.atlas.dto.metrics.MapEntryLongLong(c.id, e.id)
            FROM Exercise e
            JOIN e.competencyLinks cl
            JOIN cl.competency c
            WHERE c.id IN :competencyIds
            """)
    Set<MapEntryLongLong> findAllExerciseIdsByCompetencyIds(@Param("competencyIds") Set<Long> competencyIds);

    /**
     * Get the lecture unit ids for all lecture units that are associated with a set of competencies.
     *
     * @param competencyIds the ids of the competencies
     * @return the lecture unit ids for all lecture units that are associated with the competencies
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.atlas.dto.metrics.MapEntryLongLong(cl.competency.id, lu.id)
            FROM LectureUnit lu
            JOIN lu.competencyLinks cl
            WHERE cl.competency.id IN :competencyIds
            """)
    Set<MapEntryLongLong> findAllLectureUnitIdsByCompetencyIds(@Param("competencyIds") Set<Long> competencyIds);

    /**
     * Get the competency progress for a user in a set of competencies.
     *
     * @param userId        the id of the user
     * @param competencyIds the ids of the competencies
     * @return the competency progress for the user in the competencies
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.atlas.dto.metrics.CompetencyProgressDTO(c.id, cp.progress, cp.confidence)
            FROM CompetencyProgress cp
            JOIN cp.competency c
            WHERE cp.user.id = :userId
            AND c.id IN :competencyIds
            """)
    Set<CompetencyProgressDTO> findAllCompetencyProgressForUserByCompetencyIds(@Param("userId") long userId, @Param("competencyIds") Set<Long> competencyIds);

    /**
     * Get the latest competency judgement of learning values for a user in a set of competencies.
     *
     * @param userId        the id of the user
     * @param competencyIds the ids of the competencies
     * @return the competency judgement of learning values for the user in the competencies
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.atlas.dto.CompetencyJolDTO(jol.id, jol.competency.id, jol.value, jol.judgementTime, jol.competencyProgress, jol.competencyConfidence)
            FROM CompetencyJol jol
            WHERE jol.user.id = :userId
                AND jol.competency.id IN :competencyIds
                AND jol.judgementTime = (
                    SELECT MAX(jol2.judgementTime)
                    FROM CompetencyJol jol2
                    WHERE jol2.user.id = jol.user.id
                        AND jol2.competency.id = jol.competency.id
                    )
            """)
    Set<CompetencyJolDTO> findAllLatestCompetencyJolValuesForUserByCompetencyIds(@Param("userId") long userId, @Param("competencyIds") Set<Long> competencyIds);

    /**
     * Get the latest competency judgement of learning values for a user in a set of competencies, excluding certain judgement of learning ids.
     *
     * @param userId          the id of the user
     * @param competencyIds   the ids of the competencies
     * @param jolIdsToExclude the ids of the judgement of learning values to exclude
     * @return the competency judgement of learning values for the user in the competencies
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.atlas.dto.CompetencyJolDTO(jol.id, jol.competency.id, jol.value, jol.judgementTime, jol.competencyProgress, jol.competencyConfidence)
            FROM CompetencyJol jol
            WHERE jol.user.id = :userId
                AND jol.competency.id IN :competencyIds
                AND jol.id NOT IN :jolIdsToExclude
                AND jol.judgementTime = (
                    SELECT MAX(jol2.judgementTime)
                    FROM CompetencyJol jol2
                    WHERE jol2.user.id = jol.user.id
                        AND jol2.competency.id = jol.competency.id
                        AND jol2.id NOT IN :jolIdsToExclude
                    )
            """)
    Set<CompetencyJolDTO> findAllLatestCompetencyJolValuesForUserByCompetencyIdsExcludeJolIds(@Param("userId") long userId, @Param("competencyIds") Set<Long> competencyIds,
            @Param("jolIdsToExclude") Set<Long> jolIdsToExclude);
}
