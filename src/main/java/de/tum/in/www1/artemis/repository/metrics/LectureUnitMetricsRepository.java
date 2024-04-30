package de.tum.in.www1.artemis.repository.metrics;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.web.rest.dto.metrics.LectureUnitInformationDTO;

public interface LectureUnitMetricsRepository extends JpaRepository<LectureUnit, Long> {

    /**
     * Get the lecture unit information for all lecture units in a course.
     *
     * @param courseId the id of the course
     * @return the lecture unit information for all lecture units in the course
     */
    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.metrics.LectureUnitInformationDTO(lu.id, lu.name, lu.releaseDate)
            FROM LectureUnit lu
            WHERE lu.lecture.course.id = :courseId
            """)
    Set<LectureUnitInformationDTO> findAllLectureUnitInformationByCourseId(long courseId);
}
