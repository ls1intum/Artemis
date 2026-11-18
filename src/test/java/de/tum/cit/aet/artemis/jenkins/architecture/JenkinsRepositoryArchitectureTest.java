package de.tum.cit.aet.artemis.jenkins.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleRepositoryArchitectureTest;

class JenkinsRepositoryArchitectureTest extends AbstractModuleRepositoryArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".jenkins";
    }
}
