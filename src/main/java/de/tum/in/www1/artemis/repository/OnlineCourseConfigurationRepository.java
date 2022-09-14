package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.OnlineCourseConfiguration;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the OnlineCourseConfiguration entity.
 */
@Repository
public interface OnlineCourseConfigurationRepository extends JpaRepository<OnlineCourseConfiguration, Long> {

    /**
     * Find the online course configuration by course id
     *
     * @param courseId the course id
     * @return an Optional with the online course configuration if such configuration exists and an empty Optional otherwise
     */
    @Query("""
                SELECT configuration
                FROM OnlineCourseConfiguration configuration
                WHERE configuration.course.id = :#{#courseId}
            """)
    Optional<OnlineCourseConfiguration> findByCourseId(@Param("courseId") Long courseId);

    default OnlineCourseConfiguration findByCourseIdOrElseThrow(Long courseId) {
        return findByCourseId(courseId).orElseThrow(() -> new EntityNotFoundException("Online course configuration with course ID " + courseId + " doesn't exist"));
    }
}
