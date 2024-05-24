package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.competency.Prerequisite;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public interface PrerequisiteRepository extends JpaRepository<Prerequisite, Long> {

    // TODO: needed -> if yes we need to add linkedCompetency to prerequisite -> Would it make sense to also add to competency?
    /*
     * @Query("""
     * SELECT c
     * FROM Competency c
     * LEFT JOIN FETCH c.consecutiveCourses
     * WHERE c.id = :competencyId
     * """)
     * Optional<Competency> findByIdWithConsecutiveCourses(@Param("competencyId") long competencyId);
     */

    List<Prerequisite> findByCourseIdOrderByTitle(long courseId);

    Long countByCourseId(long courseId);

    Optional<Prerequisite> findByIdAndCourseId(long prerequisiteId, long courseId);

    default Prerequisite findByIdAndCourseIdElseThrow(long prerequisiteId, long courseId) throws EntityNotFoundException {
        return findByIdAndCourseId(prerequisiteId, courseId).orElseThrow(() -> new EntityNotFoundException("Prerequisite", prerequisiteId));
    }

    boolean existsByIdAndCourseId(long prerequisiteId, long courseId);

    default void existsByIdAndCourseIdElseThrow(long prerequisiteId, long courseId) throws EntityNotFoundException {
        if (!existsByIdAndCourseId(prerequisiteId, courseId)) {
            throw new EntityNotFoundException("Prerequisite", prerequisiteId);
        }
    }
}
