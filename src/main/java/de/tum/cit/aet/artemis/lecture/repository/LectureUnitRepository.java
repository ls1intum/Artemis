package de.tum.cit.aet.artemis.lecture.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.Set;

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
