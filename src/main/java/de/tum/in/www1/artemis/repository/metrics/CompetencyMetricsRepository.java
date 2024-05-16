package de.tum.in.www1.artemis.repository.metrics;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.web.rest.dto.metrics.CompetencyInformationDTO;
import de.tum.in.www1.artemis.web.rest.dto.metrics.CompetencyProgressDTO;
import de.tum.in.www1.artemis.web.rest.dto.metrics.MapEntryDTO;

/**
 * Spring Data JPA repository to fetch competency related metrics.
 */
@Profile(PROFILE_CORE)
@Repository
public interface CompetencyMetricsRepository extends JpaRepository<Competency, Long> {

    /**
     * Get the competency information for all competencies in a course.
     *
     * @param courseId the id of the course
     * @return the competency information for all competencies in the course
     */
    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.metrics.CompetencyInformationDTO(c.id, c.title, c.description, c.taxonomy, c.softDueDate, c.optional, c.masteryThreshold)
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
            SELECT new de.tum.in.www1.artemis.web.rest.dto.metrics.MapEntryDTO(c.id, e.id)
            FROM Exercise e
            JOIN e.competencies c
            WHERE c.id IN :competencyIds
            """)
    Set<MapEntryDTO> findAllExerciseIdsByCompetencyIds(@Param("competencyIds") Set<Long> competencyIds);

    /**
     * Get the lecture unit ids for all lecture units that are associated with a set of competencies.
     *
     * @param competencyIds the ids of the competencies
     * @return the lecture unit ids for all lecture units that are associated with the competencies
     */
    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.metrics.MapEntryDTO(c.id, lu.id)
            FROM LectureUnit lu
            JOIN lu.competencies c
            WHERE c.id IN :competencyIds
            """)
    Set<MapEntryDTO> findAllLectureUnitIdsByCompetencyIds(@Param("competencyIds") Set<Long> competencyIds);

    /**
     * Get the competency progress for a user in a set of competencies.
     *
     * @param userId        the id of the user
     * @param competencyIds the ids of the competencies
     * @return the competency progress for the user in the competencies
     */
    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.metrics.CompetencyProgressDTO(c.id, cp.progress, cp.confidence)
            FROM CompetencyProgress cp
            JOIN cp.competency c
            WHERE cp.user.id = :userId
            AND c.id IN :competencyIds
            """)
    Set<CompetencyProgressDTO> findAllCompetencyProgressForUserByCompetencyIds(@Param("userId") long userId, @Param("competencyIds") Set<Long> competencyIds);
}
