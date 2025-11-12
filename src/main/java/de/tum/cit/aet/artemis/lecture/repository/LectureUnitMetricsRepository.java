package de.tum.cit.aet.artemis.lecture.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.dto.metrics.LectureUnitInformationDTO;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

/**
 * Spring Data JPA repository to fetch lecture unit related metrics.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface LectureUnitMetricsRepository extends ArtemisJpaRepository<LectureUnit, Long> {

    /**
     * Get the lecture unit information for all lecture units in a course.
     *
     * @param courseId the id of the course
     * @return the lecture unit information for all lecture units in the course
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.atlas.dto.metrics.LectureUnitInformationDTO(
                lectureUnit.id,
                lectureUnit.lecture.id,
                lectureUnit.lecture.title,
                COALESCE(lectureUnit.name, attachment.name),
                lectureUnit.releaseDate,
                TYPE(lectureUnit)
            )
            FROM LectureUnit lectureUnit
                LEFT JOIN Attachment attachment ON attachment.attachmentVideoUnit.id = lectureUnit.id
            WHERE lectureUnit.lecture.course.id = :courseId AND NOT lectureUnit.lecture.isTutorialLecture
            """)
    Set<LectureUnitInformationDTO> findAllNormalLectureUnitInformationByCourseId(@Param("courseId") long courseId);

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
