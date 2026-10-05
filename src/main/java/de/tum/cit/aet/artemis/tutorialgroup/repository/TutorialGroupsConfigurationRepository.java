package de.tum.cit.aet.artemis.tutorialgroup.repository;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;

@Conditional(TutorialGroupEnabled.class)
@Lazy
@Repository
public interface TutorialGroupsConfigurationRepository extends ArtemisJpaRepository<TutorialGroupsConfiguration, Long> {

    @Query("""
            SELECT t
            FROM TutorialGroupsConfiguration t
                JOIN FETCH t.course
                LEFT JOIN t.tutorialGroupFreePeriods
            WHERE t.id = :tutorialGroupConfigurationId
            """)
    Optional<TutorialGroupsConfiguration> findByIdWithEagerTutorialGroupFreePeriods(@Param("tutorialGroupConfigurationId") Long tutorialGroupConfigurationId);

    default TutorialGroupsConfiguration findByIdWithEagerTutorialGroupFreePeriodsElseThrow(Long tutorialGroupsConfigurationId) {
        return getValueElseThrow(findByIdWithEagerTutorialGroupFreePeriods(tutorialGroupsConfigurationId), tutorialGroupsConfigurationId);
    }

    @Query("""
            SELECT t
            FROM TutorialGroupsConfiguration t
                JOIN FETCH t.course
                LEFT JOIN FETCH t.tutorialGroupFreePeriods
            WHERE t.course.id = :courseId AND t.tutorialPeriodStartInclusive IS NOT NULL AND t.tutorialPeriodEndInclusive IS NOT NULL
            """)
    Optional<TutorialGroupsConfiguration> findByCourseIdWithEagerTutorialGroupFreePeriods(@Param("courseId") Long courseId);

    /**
     * Finds the tutorial groups configuration of the given course, if one exists. The configuration holds the key to its
     * course, so a course carries no association to it and this is where it is read.
     *
     * @param courseId the id of the course
     * @return the configured settings, or empty until both tutorial-period dates are set
     */
    @Query("""
            SELECT t
            FROM TutorialGroupsConfiguration t
            WHERE t.course.id = :courseId AND t.tutorialPeriodStartInclusive IS NOT NULL AND t.tutorialPeriodEndInclusive IS NOT NULL
            """)
    Optional<TutorialGroupsConfiguration> findByCourseId(@Param("courseId") long courseId);

    /**
     * Updates tutorial-group settings without inserting or replacing their course-owned row.
     *
     * @param courseId       the course id
     * @param start          the tutorial period start
     * @param end            the tutorial period end
     * @param channels       whether tutorial group channels are used
     * @param publicChannels whether the channels are public
     * @return the number of updated rows
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE TutorialGroupsConfiguration configuration
            SET configuration.tutorialPeriodStartInclusive = :start, configuration.tutorialPeriodEndInclusive = :end,
                configuration.useTutorialGroupChannels = :channels, configuration.usePublicTutorialGroupChannels = :publicChannels
            WHERE configuration.course.id = :courseId
            """)
    int updateSettings(@Param("courseId") long courseId, @Param("start") String start, @Param("end") String end, @Param("channels") boolean channels,
            @Param("publicChannels") boolean publicChannels);
}
