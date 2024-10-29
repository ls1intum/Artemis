package de.tum.cit.aet.artemis.lecture.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.Set;

import org.hibernate.NonUniqueResultException;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

/**
 * Spring Data JPA repository for the Lecture Unit entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface LectureUnitRepository extends ArtemisJpaRepository<LectureUnit, Long> {

    @Query("""
            SELECT lu
            FROM LectureUnit lu
                LEFT JOIN FETCH lu.competencies
                LEFT JOIN FETCH lu.exercise exercise
                LEFT JOIN FETCH exercise.competencies
                LEFT JOIN FETCH lu.slides
            WHERE lu.id = :lectureUnitId
            """)
    Optional<LectureUnit> findWithCompetenciesAndSlidesById(@Param("lectureUnitId") long lectureUnitId);

    @Query("""
            SELECT lu
            FROM LectureUnit lu
                LEFT JOIN FETCH lu.competencies c
                LEFT JOIN FETCH c.lectureUnits
                LEFT JOIN FETCH lu.exercise ex
                LEFT JOIN FETCH ex.competencies
            WHERE lu.id = :lectureUnitId
            """)
    Optional<LectureUnit> findByIdWithCompetenciesBidirectional(@Param("lectureUnitId") long lectureUnitId);

    @Query("""
            SELECT lu
            FROM LectureUnit lu
                LEFT JOIN FETCH lu.competencies c
                LEFT JOIN FETCH c.lectureUnits
                LEFT JOIN FETCH lu.exercise ex
                LEFT JOIN FETCH ex.competencies
            WHERE lu.id IN :lectureUnitIds
            """)
    Set<LectureUnit> findAllByIdWithCompetenciesBidirectional(@Param("lectureUnitIds") Iterable<Long> longs);

    @Query("""
            SELECT lu
            FROM LectureUnit lu
                LEFT JOIN FETCH lu.completedUsers
            WHERE lu.id = :lectureUnitId
            """)
    Optional<LectureUnit> findByIdWithCompletedUsers(@Param("lectureUnitId") long lectureUnitId);

    /**
     * Finds a lecture unit by name, lecture title and course id. Currently, name duplicates are allowed but this method throws an exception if multiple lecture units with the
     * same name are found.
     *
     * @param name         the name of the lecture unit
     * @param lectureTitle the title of the lecture containing the lecture unit
     * @param courseId     the id of the course containing the lecture
     * @return the lecture unit with the given name, lecture title and course id
     * @throws NonUniqueResultException if multiple lecture units with the same name in the same lecture are found
     */
    @Query("""
            SELECT lu
            FROM LectureUnit lu
                LEFT JOIN FETCH lu.competencies
            WHERE lu.name = :name
                AND lu.lecture.title = :lectureTitle
                AND lu.lecture.course.id = :courseId
            """)
    Optional<LectureUnit> findByNameAndLectureTitleAndCourseIdWithCompetencies(@Param("name") String name, @Param("lectureTitle") String lectureTitle,
            @Param("courseId") long courseId) throws NonUniqueResultException;

    default LectureUnit findByIdWithCompletedUsersElseThrow(long lectureUnitId) {
        return getValueElseThrow(findByIdWithCompletedUsers(lectureUnitId), lectureUnitId);
    }

    default LectureUnit findByIdWithCompetenciesBidirectionalElseThrow(long lectureUnitId) {
        return getValueElseThrow(findByIdWithCompetenciesBidirectional(lectureUnitId), lectureUnitId);
    }

    default LectureUnit findByIdWithCompetenciesAndSlidesElseThrow(long lectureUnitId) {
        return getValueElseThrow(findWithCompetenciesAndSlidesById(lectureUnitId), lectureUnitId);
    }

}
