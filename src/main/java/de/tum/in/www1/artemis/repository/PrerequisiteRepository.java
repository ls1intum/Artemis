package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.competency.Prerequisite;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the {@link Prerequisite} entity.
 */
public interface PrerequisiteRepository extends ArtemisJpaRepository<Prerequisite, Long> {

    List<Prerequisite> findAllByCourseIdOrderById(long courseId);

    @Query("""
            SELECT c
            FROM Prerequisite c
            WHERE c.course.id = :courseId
            """)
    Set<Prerequisite> findAllForCourse(@Param("courseId") long courseId);

    @Query("""
            SELECT c
            FROM Prerequisite c
                LEFT JOIN FETCH c.lectureUnits lu
                LEFT JOIN FETCH c.exercises
            WHERE c.id = :competencyId
            """)
    Optional<Prerequisite> findByIdWithLectureUnitsAndExercises(@Param("competencyId") long competencyId);

    @Query("""
            SELECT c
            FROM Prerequisite c
                LEFT JOIN FETCH c.lectureUnits lu
            WHERE c.id = :competencyId
            """)
    Optional<Prerequisite> findByIdWithLectureUnits(@Param("competencyId") long competencyId);

    default Prerequisite findByIdWithLectureUnitsAndExercisesElseThrow(long competencyId) {
        return getValueElseThrow(findByIdWithLectureUnitsAndExercises(competencyId), competencyId);
    }

    default Prerequisite findByIdWithLectureUnitsElseThrow(long competencyId) {
        return getValueElseThrow(findByIdWithLectureUnits(competencyId), competencyId);
    }

    long countByCourse(Course course);

    List<Prerequisite> findByCourseIdOrderById(long courseId);
}
