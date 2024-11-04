package de.tum.cit.aet.artemis.programming.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationGitlabCIGitlabSamlTest;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationJenkinsGitlabTest;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleTestArchitectureTest;

class ProgrammingTestArchitectureTest extends AbstractModuleTestArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".programming";
    }

    @Override
    protected Set<Class<?>> getAbstractModuleIntegrationTestClasses() {
        // @formatter:off
        return Set.of(
            AbstractProgrammingIntegrationGitlabCIGitlabSamlTest.class,
            AbstractProgrammingIntegrationIndependentTest.class,
            AbstractProgrammingIntegrationJenkinsGitlabTest.class,
            AbstractProgrammingIntegrationLocalCILocalVCTest.class,
            AbstractProgrammingIntegrationLocalCILocalVCTestBase.class
        );
        // @formatter:on
    }
}
