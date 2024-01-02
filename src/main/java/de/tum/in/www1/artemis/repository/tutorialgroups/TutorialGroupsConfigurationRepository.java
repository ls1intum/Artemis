package de.tum.in.www1.artemis.repository.tutorialgroups;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupsConfiguration;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface TutorialGroupsConfigurationRepository extends JpaRepository<TutorialGroupsConfiguration, Long> {

    @Query("""
            SELECT t
            FROM TutorialGroupsConfiguration t
                LEFT JOIN t.tutorialGroupFreePeriods
            WHERE t.id = :#{#tutorialGroupConfigurationId}
            """)
    Optional<TutorialGroupsConfiguration> findByIdWithEagerTutorialGroupFreePeriods(@Param("tutorialGroupConfigurationId") Long tutorialGroupConfigurationId);

    default TutorialGroupsConfiguration findByIdWithEagerTutorialGroupFreePeriodsElseThrow(Long tutorialGroupsConfigurationId) {
        return findByIdWithEagerTutorialGroupFreePeriods(tutorialGroupsConfigurationId)
                .orElseThrow(() -> new EntityNotFoundException("TutorialGroupsConfiguration", tutorialGroupsConfigurationId));
    }

    @Query("""
            SELECT t
            FROM TutorialGroupsConfiguration t
                LEFT JOIN FETCH t.tutorialGroupFreePeriods
            WHERE t.course.id = :#{#courseId}
            """)
    Optional<TutorialGroupsConfiguration> findByCourseIdWithEagerTutorialGroupFreePeriods(Long courseId);
}
