package de.tum.cit.aet.artemis.course.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;
import de.tum.cit.aet.artemis.course.dto.CourseAthenaConfigDTO;

/**
 * Spring Data JPA repository for the course-level Athena configuration.
 * <p>
 * The two feature flags are switched independently and save immediately, so each one is written by its own conditional
 * statement rather than by storing a whole configuration read earlier: two instructors switching the two features at
 * the same time would otherwise each write the value they last saw for the other feature, and the slower request would
 * silently undo the faster one. Writing one column per statement means a request can only ever change the feature it
 * was actually about.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CourseAthenaConfigRepository extends ArtemisJpaRepository<CourseAthenaConfig, Long> {

    /**
     * Switches the grading feedback flag of a configuration, if it does not already have the requested value.
     * <p>
     * The database decides whether the flag actually changed, which is what the caller needs in order to know whether
     * Athena's due-date scheduling has to be republished. Deciding that from a value read earlier would get it wrong
     * whenever a concurrent request changed the flag in between: the refresh would be skipped although the flag flipped.
     *
     * @param configId the id of the configuration to update
     * @param enabled  whether Athena should suggest feedback to tutors while they assess
     * @return 1 if the flag changed, 0 if it already had the requested value
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("""
            UPDATE CourseAthenaConfig config
            SET config.gradingFeedbackEnabled = :enabled
            WHERE config.id = :configId
                AND config.gradingFeedbackEnabled <> :enabled
            """)
    int updateGradingFeedbackEnabled(@Param("configId") long configId, @Param("enabled") boolean enabled);

    /**
     * Switches the formative feedback flag of a configuration, if it does not already have the requested value.
     *
     * @param configId the id of the configuration to update
     * @param enabled  whether students may request preliminary Athena feedback before the due date
     * @return 1 if the flag changed, 0 if it already had the requested value
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("""
            UPDATE CourseAthenaConfig config
            SET config.formativeFeedbackEnabled = :enabled
            WHERE config.id = :configId
                AND config.formativeFeedbackEnabled <> :enabled
            """)
    int updateFormativeFeedbackEnabled(@Param("configId") long configId, @Param("enabled") boolean enabled);

    /**
     * Reads back what is stored for a configuration.
     * <p>
     * A projection rather than the entity, so the values come from the database even when a caller has already loaded
     * the entity: the statements above are issued as SQL and do not update an entity a persistence context may hold.
     *
     * @param configId the id of the configuration to read
     * @return the stored configuration, or empty if there is no configuration with that id
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.course.dto.CourseAthenaConfigDTO(config.gradingFeedbackEnabled, config.formativeFeedbackEnabled)
            FROM CourseAthenaConfig config
            WHERE config.id = :configId
            """)
    Optional<CourseAthenaConfigDTO> findConfigById(@Param("configId") long configId);
}
