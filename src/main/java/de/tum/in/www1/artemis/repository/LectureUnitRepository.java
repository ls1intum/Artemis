package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Lecture Unit entity.
 */
@Repository
public interface LectureUnitRepository extends JpaRepository<LectureUnit, Long> {

    @Query("""
            SELECT lu
            FROM LectureUnit lu
                LEFT JOIN FETCH lu.competencies
                LEFT JOIN FETCH lu.exercise exercise
                LEFT JOIN FETCH exercise.competencies
            WHERE lu.id = :lectureUnitId
            """)
    Optional<LectureUnit> findByIdWithCompetencies(@Param("lectureUnitId") Long lectureUnitId);

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

    default LectureUnit findByIdWithCompetenciesBidirectionalElseThrow(long lectureUnitId) {
        return findByIdWithCompetenciesBidirectional(lectureUnitId).orElseThrow(() -> new EntityNotFoundException("LectureUnit", lectureUnitId));
    }

    default LectureUnit findByIdWithCompetenciesElseThrow(long lectureUnitId) {
        return findByIdWithCompetencies(lectureUnitId).orElseThrow(() -> new EntityNotFoundException("LectureUnit", lectureUnitId));
    }

    @EntityGraph(type = LOAD, attributePaths = { "completedUsers" })
    Optional<LectureUnit> findWithEagerCompletedUsersById(long lectureUnitId);

    @NotNull
    default LectureUnit findWithEagerCompletedUsersByIdElseThrow(long lectureUnitId) {
        return findWithEagerCompletedUsersById(lectureUnitId).orElseThrow(() -> new EntityNotFoundException("LectureUnit", lectureUnitId));
    }
}
