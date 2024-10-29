package de.tum.cit.aet.artemis.shared.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMembers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractModuleTestArchitectureTest<T> extends AbstractArchitectureTest {

    abstract protected String getModulePackageName();

    abstract protected Class<T> getAbstractModuleIntegrationTestClass();

    @Test
    void integrationTestsShouldExtendAbstractModuleIntegrationTest() {
        classes().that().resideInAPackage(ARTEMIS_PACKAGE + "." + getModulePackageName()).and().haveSimpleNameEndingWith("IntegrationTest").should()
                .beAssignableTo(getAbstractModuleIntegrationTestClass()).because("All integration tests should extend %s".formatted(getAbstractModuleIntegrationTestClass()))
                .check(testClasses);
    }

    @Test
    void integrationTestsShouldNotAutowireMembers() {
        noMembers().that().areAnnotatedWith(Autowired.class).should().beDeclaredInClassesThat().areAssignableTo(getAbstractModuleIntegrationTestClass()).andShould()
                .notBeDeclaredIn(getAbstractModuleIntegrationTestClass())
                .because("Integration tests should not autowire members in any class that inherits from %s".formatted(getAbstractModuleIntegrationTestClass())).check(testClasses);
    }
}
