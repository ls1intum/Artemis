package de.tum.cit.aet.artemis.tutorialgroup.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleRepositoryArchitectureTest;

class TutorialGroupRepositoryArchitectureTest extends AbstractModuleRepositoryArchitectureTest {

    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".tutorialgroup";
    }

    // TODO: This method should be removed once all repositories are tested
    @Override
    protected Set<String> testTransactionalExclusions() {
        return Set.of("de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupsConfigurationService.onTimeZoneUpdate(de.tum.cit.aet.artemis.core.domain.Course)");
    }
}
