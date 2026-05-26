package de.tum.cit.aet.artemis.course.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleTestArchitectureTest;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class CourseTestArchitectureTest extends AbstractModuleTestArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".course";
    }

    @Override
    protected Set<Class<?>> getAbstractModuleIntegrationTestClasses() {
        return Set.of(AbstractSpringIntegrationIndependentTest.class);
    }
}
