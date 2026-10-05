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
import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;
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
            WHERE configuration.course.id = :courseId AND configuration.course.onlineCourse = TRUE
            """)
    Optional<OnlineCourseConfiguration> findByCourseId(@Param("courseId") long courseId);

    /**
     * Updates settings without replacing the permanent configuration row.
     *
     * @param courseId            the owning course id
     * @param configId            the configuration id validated by the endpoint
     * @param prefix              the user prefix
     * @param requireExistingUser whether new LTI accounts are prohibited
     * @param platform            the linked LTI platform, or null to remove the link
     * @return the number of updated rows
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("""
            UPDATE OnlineCourseConfiguration configuration
            SET configuration.userPrefix = :prefix, configuration.requireExistingUser = :requireExistingUser,
                configuration.ltiPlatformConfiguration = :platform
            WHERE configuration.course.id = :courseId AND configuration.id = :configId
            """)
    int updateSettings(@Param("courseId") long courseId, @Param("configId") long configId, @Param("prefix") String prefix,
            @Param("requireExistingUser") boolean requireExistingUser, @Param("platform") LtiPlatformConfiguration platform);
}
