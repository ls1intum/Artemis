package de.tum.cit.aet.artemis.jenkins.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleTestArchitectureTest;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTestBase;

class JenkinsTestArchitectureTest extends AbstractModuleTestArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".jenkins";
    }

    @Override
    protected Set<Class<?>> getAbstractModuleIntegrationTestClasses() {
        return Set.of(AbstractSpringIntegrationJenkinsLocalVCTestBase.class);
    }
}
