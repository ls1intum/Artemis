package de.tum.in.www1.artemis.repository.metrics;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;
import de.tum.in.www1.artemis.web.rest.dto.metrics.LectureUnitInformationDTO;

/**
 * Spring Data JPA repository to fetch lecture unit related metrics.
 */
@Profile(PROFILE_CORE)
@Repository
public interface LectureUnitMetricsRepository extends ArtemisJpaRepository<LectureUnit, Long> {

    /**
     * Get the lecture unit information for all lecture units in a course.
     *
     * @param courseId the id of the course
     * @return the lecture unit information for all lecture units in the course
     */
    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.metrics.LectureUnitInformationDTO(lu.id, lu.lecture.id, lu.lecture.title, COALESCE(lu.name, a.name), lu.releaseDate, TYPE(lu))
            FROM LectureUnit lu
                LEFT JOIN Attachment a ON a.attachmentUnit.id = lu.id
            WHERE lu.lecture.course.id = :courseId
            """)
    Set<LectureUnitInformationDTO> findAllLectureUnitInformationByCourseId(@Param("courseId") long courseId);

    /**
     * Get the ids of the completed lecture units for a user for a set of lecture units.
     *
     * @param userId         the id of the user
     * @param lectureUnitIds the ids of the lecture units
     * @return the ids of the completed lecture units for the user
     */
    @Query("""
            SELECT luc.lectureUnit.id
            FROM LectureUnitCompletion luc
            WHERE luc.user.id = :userId
                AND luc.lectureUnit.id IN :lectureUnitIds
            """)
    Set<Long> findAllCompletedLectureUnitIdsForUserByLectureUnitIds(@Param("userId") long userId, @Param("lectureUnitIds") Set<Long> lectureUnitIds);
}
