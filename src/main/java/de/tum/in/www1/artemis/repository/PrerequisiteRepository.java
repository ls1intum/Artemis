package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.competency.Prerequisite;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public interface PrerequisiteRepository extends JpaRepository<Prerequisite, Long> {

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

    long countByCourse(Course course);
}
