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
import de.tum.in.www1.artemis.web.rest.dto.metrics.MapEntryDTO;

@Profile(PROFILE_CORE)
@Repository
public interface CompetencyMetricsRepository extends JpaRepository<Competency, Long> {

    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.metrics.CompetencyInformationDTO(c.id, c.title, c.description, c.taxonomy, c.softDueDate, c.optional)
            FROM Competency c
            WHERE c.course.id = :courseId
            """)
    Set<CompetencyInformationDTO> findAllCompetencyInformationByCourseId(@Param("courseId") long courseId);

    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.metrics.MapEntryDTO(c.id, e.id)
            FROM Exercise e
            JOIN e.competencies c
            WHERE c.id IN :competencyIds
            """)
    Set<MapEntryDTO> findAllExerciseIdsByCompetencyIds(@Param("competencyIds") Set<Long> competencyIds);

    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.metrics.MapEntryDTO(c.id, lu.id)
            FROM LectureUnit lu
            JOIN lu.competencies c
            WHERE c.id IN :competencyIds
            """)
    Set<MapEntryDTO> findAllLectureUnitIdsByCompetencyIds(@Param("competencyIds") Set<Long> competencyIds);
}
