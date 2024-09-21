package de.tum.cit.aet.artemis.atlas.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

class TestArchitectureTest extends AbstractArchitectureTest {

    @Test
    void integrationTestsShouldExtendAbstractAtlasIntegrationTest() {
        classes().that().resideInAPackage(ARTEMIS_PACKAGE + ".atlas").and().haveSimpleNameEndingWith("IntegrationTest").should().beAssignableTo(AbstractAtlasIntegrationTest.class)
                .because("All integration tests should extend AbstractAtlasIntegrationTest").check(testClasses);
    }
}
