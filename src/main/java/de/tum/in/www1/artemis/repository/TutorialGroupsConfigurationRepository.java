package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TutorialGroupsConfiguration;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Repository
public interface TutorialGroupsConfigurationRepository extends JpaRepository<TutorialGroupsConfiguration, Long> {

    default TutorialGroupsConfiguration findByIdWithElseThrow(Long tutorialGroupsConfigurationId) {
        return findById(tutorialGroupsConfigurationId).orElseThrow(() -> new EntityNotFoundException("TutorialGroupsConfiguration", tutorialGroupsConfigurationId));
    }

}
