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

    List<Prerequisite> findByCourseIdOrderById(long courseId);

    Optional<Prerequisite> findByIdAndCourseId(long prerequisiteId, long courseId);

    /**
     * Finds a prerequisite with the given id in the course of the given id. If it does not exist throws a {@link EntityNotFoundException}
     *
     * @param prerequisiteId the id of the prerequisite to find
     * @param courseId       the id of the course
     * @return the prerequisite
     */
    default Prerequisite findByIdAndCourseIdElseThrow(long prerequisiteId, long courseId) {
        return findByIdAndCourseId(prerequisiteId, courseId).orElseThrow(() -> new EntityNotFoundException("Prerequisite", prerequisiteId));
    }

    long countByCourse(Course course);
}
