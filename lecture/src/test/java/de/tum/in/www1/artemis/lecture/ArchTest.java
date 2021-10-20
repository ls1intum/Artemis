package de.tum.in.www1.artemis.lecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchTest {

    @Test
    void servicesAndRepositoriesShouldNotDependOnWebLayer() {
        JavaClasses importedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("de.tum.in.www1.artemis.lecture");

        noClasses()
            .that()
            .resideInAnyPackage("de.tum.in.www1.artemis.lecture.service..")
            .or()
            .resideInAnyPackage("de.tum.in.www1.artemis.lecture.repository..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..de.tum.in.www1.artemis.lecture.web..")
            .because("Services and repositories should not depend on web layer")
            .check(importedClasses);
    }
}
