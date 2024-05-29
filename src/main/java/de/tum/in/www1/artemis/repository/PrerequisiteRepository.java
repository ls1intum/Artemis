package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.competency.Prerequisite;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the {@link Prerequisite} entity.
 */
public interface PrerequisiteRepository extends JpaRepository<Prerequisite, Long> {

    List<Prerequisite> findByCourseIdOrderByTitle(long courseId);

    Optional<Prerequisite> findByIdAndCourseId(long prerequisiteId, long courseId);

    Long countByCourseId(long courseId);

    boolean existsByIdAndCourseId(long prerequisiteId, long courseId);

    /**
     *
     * @param prerequisiteId
     * @param courseId
     * @return
     * @throws EntityNotFoundException
     */
    default Prerequisite findByIdAndCourseIdElseThrow(long prerequisiteId, long courseId) throws EntityNotFoundException {
        return findByIdAndCourseId(prerequisiteId, courseId).orElseThrow(() -> new EntityNotFoundException("Prerequisite", prerequisiteId));
    }

    /**
     *
     * @param prerequisiteId
     * @param courseId
     * @throws EntityNotFoundException
     */
    default void existsByIdAndCourseIdElseThrow(long prerequisiteId, long courseId) throws EntityNotFoundException {
        if (!existsByIdAndCourseId(prerequisiteId, courseId)) {
            throw new EntityNotFoundException("Prerequisite", prerequisiteId);
        }
    }

    long countByCourse(Course course);
}
