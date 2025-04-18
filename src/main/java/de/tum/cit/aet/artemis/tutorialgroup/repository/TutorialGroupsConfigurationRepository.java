package de.tum.cit.aet.artemis.tutorialgroup.repository;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;

@Conditional(TutorialGroupEnabled.class)
@Repository
public interface TutorialGroupsConfigurationRepository extends ArtemisJpaRepository<TutorialGroupsConfiguration, Long> {

    @Query("""
            SELECT t
            FROM TutorialGroupsConfiguration t
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
                LEFT JOIN FETCH t.tutorialGroupFreePeriods
            WHERE t.course.id = :courseId
            """)
    Optional<TutorialGroupsConfiguration> findByCourseIdWithEagerTutorialGroupFreePeriods(@Param("courseId") Long courseId);
}
