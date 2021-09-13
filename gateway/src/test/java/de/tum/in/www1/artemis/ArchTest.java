package de.tum.in.www1.artemis;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

class ArchTest {

    @Test
    void servicesAndRepositoriesShouldNotDependOnWebLayer() {
        JavaClasses importedClasses = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("de.tum.in.www1.artemis");

        noClasses().that().resideInAnyPackage("de.tum.in.www1.artemis.service..").or().resideInAnyPackage("de.tum.in.www1.artemis.repository..").should().dependOnClassesThat()
                .resideInAnyPackage("..de.tum.in.www1.artemis.web..").because("Services and repositories should not depend on web layer").check(importedClasses);
    }
}
