package de.tum.cit.aet.artemis.shared.architecture.module;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMembers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

public abstract class AbstractModuleTestArchitectureTest<T> extends AbstractArchitectureTest implements ModuleArchitectureTest {

    abstract protected Class<T> getAbstractModuleIntegrationTestClass();

    @Test
    void integrationTestsShouldExtendAbstractModuleIntegrationTest() {
        classesOfThisModuleThat().haveSimpleNameEndingWith("IntegrationTest").should().beAssignableTo(getAbstractModuleIntegrationTestClass())
                .because("All integration tests should extend %s".formatted(getAbstractModuleIntegrationTestClass())).check(testClasses);
    }

    @Test
    void integrationTestsShouldNotAutowireMembers() {
        noMembers().that().areAnnotatedWith(Autowired.class).should().beDeclaredInClassesThat().areAssignableTo(getAbstractModuleIntegrationTestClass()).andShould()
                .notBeDeclaredIn(getAbstractModuleIntegrationTestClass())
                .because("Integration tests should not autowire members in any class that inherits from %s".formatted(getAbstractModuleIntegrationTestClass())).check(testClasses);
    }
}
