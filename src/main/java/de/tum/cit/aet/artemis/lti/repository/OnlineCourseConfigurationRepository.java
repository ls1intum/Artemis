package de.tum.cit.aet.artemis.lti.repository;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lti.config.LtiEnabled;
import de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration;

/**
 * Spring Data JPA repository for the {@link OnlineCourseConfiguration} entity. The configuration holds the key to its
 * course, so a course carries no association to it and loading a course never loads the configuration. Read it here, at
 * the point where it is needed.
 */
@Conditional(LtiEnabled.class)
@Lazy
@Repository
public interface OnlineCourseConfigurationRepository extends ArtemisJpaRepository<OnlineCourseConfiguration, Long> {

    /**
     * Finds the online course configuration of the given course, if one exists.
     *
     * @param courseId the id of the course
     * @return the configuration, or empty when the course is not an online course
     */
    @Query("""
            SELECT configuration
            FROM OnlineCourseConfiguration configuration
            WHERE configuration.course.id = :courseId
            """)
    Optional<OnlineCourseConfiguration> findByCourseId(@Param("courseId") long courseId);

    /**
     * Removes the online course configuration of the given course, e.g. when the course stops being an online course.
     *
     * @param courseId the id of the course
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM OnlineCourseConfiguration configuration
            WHERE configuration.course.id = :courseId
            """)
    void deleteByCourseId(@Param("courseId") long courseId);
}
