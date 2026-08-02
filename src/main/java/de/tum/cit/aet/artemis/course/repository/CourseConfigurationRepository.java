package de.tum.cit.aet.artemis.course.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;

/**
 * Spring Data JPA repository for the {@link CourseConfiguration} entity. Used to load a course's configuration
 * independently of the (lazy) association on the course, e.g. when updating the grade-relevance flag without eagerly
 * fetching the configuration through the course entity graph.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CourseConfigurationRepository extends ArtemisJpaRepository<CourseConfiguration, Long> {

    /**
     * Finds the configuration of the given course, if one exists.
     *
     * @param courseId the id of the course
     * @return the course configuration or an empty optional if the course has none yet
     */
    @Query("""
            SELECT configuration
            FROM CourseConfiguration configuration
            WHERE configuration.course.id = :courseId
            """)
    Optional<CourseConfiguration> findByCourseId(@Param("courseId") long courseId);
}
