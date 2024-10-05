package de.tum.cit.aet.artemis.atlas.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMembers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

class TestArchitectureTest extends AbstractArchitectureTest {

    @Test
    void integrationTestsShouldExtendAbstractAtlasIntegrationTest() {
        classes().that().resideInAPackage(ARTEMIS_PACKAGE + ".atlas").and().haveSimpleNameEndingWith("IntegrationTest").should().beAssignableTo(AbstractAtlasIntegrationTest.class)
                .because("All integration tests should extend AbstractAtlasIntegrationTest").check(testClasses);
    }

    @Test
    void integrationTestsShouldNotAutowireMembers() {
        noMembers().that().areAnnotatedWith(Autowired.class).should().beDeclaredInClassesThat().areAssignableTo(AbstractAtlasIntegrationTest.class).andShould()
                .notBeDeclaredIn(AbstractAtlasIntegrationTest.class)
                .because("Integration tests should not autowire members in any class that inherits from AbstractAtlasIntegrationTest").check(testClasses);
    }
}
