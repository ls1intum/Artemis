package de.tum.cit.aet.artemis.lti.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleRepositoryArchitectureTest;

class LtiRepositoryArchitectureTest extends AbstractModuleRepositoryArchitectureTest {

    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".lti";
    }

    @Override
    protected Set<String> enforceStructureOfTestRepositoriesExclusions() {
        return Set.of("de.tum.cit.aet.artemis.lti.test_repository.OnlineCourseConfigurationTestRepository");
    }
}
