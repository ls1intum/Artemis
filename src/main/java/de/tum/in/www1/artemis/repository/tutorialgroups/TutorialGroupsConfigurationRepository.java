package de.tum.in.www1.artemis.repository.tutorialgroups;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupsConfiguration;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface TutorialGroupsConfigurationRepository extends JpaRepository<TutorialGroupsConfiguration, Long> {

    @Query(value = "select tutorialGroupConfiguration from TutorialGroupsConfiguration tutorialGroupConfiguration left join tutorialGroupConfiguration.tutorialGroupFreeDays where tutorialGroupConfiguration.id = :#{#tutorialGroupConfigurationId}")
    Optional<TutorialGroupsConfiguration> findByIdWithEagerTutorialGroupFreeDays(@Param("tutorialGroupConfigurationId") Long tutorialGroupConfigurationId);

    default TutorialGroupsConfiguration findByIdWithElseThrow(Long tutorialGroupsConfigurationId) {
        return findByIdWithEagerTutorialGroupFreeDays(tutorialGroupsConfigurationId)
                .orElseThrow(() -> new EntityNotFoundException("TutorialGroupsConfiguration", tutorialGroupsConfigurationId));
    }

    Optional<TutorialGroupsConfiguration> findByCourse(Course course);
}
