package de.tum.cit.aet.artemis.course.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
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
     * Takes a write lock on a course row, so that the courses whose {@code athena_config_id} is still null - every
     * course that existed before the configuration was introduced - cannot have two configurations created at once.
     * <p>
     * Selects the course's own id and mentions no association, so the statement locks exactly the course row. Locking
     * the loaded course entity instead would not work: its Athena configuration is an eager to-one over a nullable join
     * column, and PostgreSQL rejects a locking read on the nullable side of an outer join.
     *
     * @param courseId the id of the course to lock
     * @return the course's id, or empty if there is no such course
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT course.id
            FROM Course course
            WHERE course.id = :courseId
            """)
    Optional<Long> lockCourseForAthenaConfigInitialization(@Param("courseId") long courseId);

    /**
     * Returns the id of a course's Athena configuration, reading the foreign key without loading either entity.
     *
     * @param courseId the id of the course to read the configuration id of
     * @return the id of the course's Athena configuration, or empty if the course has none yet
     */
    @Query("""
            SELECT course.athenaConfig.id
            FROM Course course
            WHERE course.id = :courseId
            """)
    Optional<Long> findAthenaConfigIdByCourseId(@Param("courseId") long courseId);

    /**
     * Points a course at an Athena configuration.
     *
     * @param courseId the id of the course to attach the configuration to
     * @param config   the configuration to attach
     * @return the number of rows updated: 1 if the course exists, 0 if it does not
     */
    @Modifying
    @Query("""
            UPDATE Course course
            SET course.athenaConfig = :config
            WHERE course.id = :courseId
            """)
    int attachAthenaConfigToCourse(@Param("courseId") long courseId, @Param("config") CourseAthenaConfig config);

    /**
     * Returns the id of a course's Athena configuration, creating an all-disabled one if the course has none yet.
     * <p>
     * The course row is locked first, so that two instructors switching a feature of the same not-yet-configured course
     * cannot both find no configuration, create one each and then race to point the course at theirs: the loser of that
     * race would have written its flag into a row nothing references any more, while its request still answered 200.
     * Locking before reading also makes the read below see the winner's configuration on both databases, because MySQL
     * takes the transaction's snapshot at its first non-locking read rather than at the lock.
     *
     * @param courseId the id of the course to configure
     * @return the id of the course's Athena configuration
     */
    @Transactional // ok because of pessimistic locking combined with a conditional write
    default long ensureAthenaConfigExists(long courseId) {
        if (lockCourseForAthenaConfigInitialization(courseId).isEmpty()) {
            throw new EntityNotFoundException("Course", courseId);
        }
        Optional<Long> existingConfigId = findAthenaConfigIdByCourseId(courseId);
        if (existingConfigId.isPresent()) {
            return existingConfigId.get();
        }
        // Flushed right away because the statement attaching it to the course needs its generated id.
        CourseAthenaConfig config = saveAndFlush(new CourseAthenaConfig());
        attachAthenaConfigToCourse(courseId, config);
        return config.getId();
    }

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
