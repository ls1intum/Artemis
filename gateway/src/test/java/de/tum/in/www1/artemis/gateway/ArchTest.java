package de.tum.in.www1.artemis.gateway;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

class ArchTest {

    private static final String BASE_PACKAGE = "de.tum.in.www1.artemis.gateway";
    private static final String SERVICE_PACKAGE = "de.tum.in.www1.artemis.gateway.service..";
    private static final String REPOSITORY_PACKAGE = "de.tum.in.www1.artemis.gateway.repository..";
    private static final String WEB_PACKAGE = "..de.tum.in.www1.artemis.gateway.web..";

    @Test
    void servicesAndRepositoriesShouldNotDependOnWebLayer() {
        JavaClasses importedClasses = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(BASE_PACKAGE);

        noClasses().that().resideInAnyPackage(SERVICE_PACKAGE).or().resideInAnyPackage(REPOSITORY_PACKAGE).should()
                .dependOnClassesThat().resideInAnyPackage(WEB_PACKAGE).because("Services and repositories should not depend on web layer")
                .check(importedClasses);
    }
}
