package de.tum.cit.aet.artemis.hyperion.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleServiceArchitectureTest;

class HyperionServiceArchitectureTest extends AbstractModuleServiceArchitectureTest {

    @Test
    void otherModulesOnlyAccessPublicContracts() {
        classes().that().resideOutsideOfPackage(getModulePackage() + "..").should().onlyDependOnClassesThat(resideOutsideOfPackage(getModulePackage() + "..")
                .or(resideInAnyPackage(getModulePackage() + ".api..", getModulePackage() + ".domain..", getModulePackage() + ".dto.."))).check(productionClasses);
    }

    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".hyperion";
    }
}
