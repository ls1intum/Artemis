package de.tum.cit.aet.artemis.jenkins.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleResourceArchitectureTest;

class JenkinsResourceArchitectureTest extends AbstractModuleResourceArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".jenkins";
    }
}
