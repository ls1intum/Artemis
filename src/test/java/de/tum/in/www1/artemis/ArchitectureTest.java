package de.tum.in.www1.artemis;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

class ArchitectureTest {

    @Test
    void testNoJUnit4() {
        JavaClasses testClasses = new ClassFileImporter().withImportOption(new ImportOption.OnlyIncludeTests()).importPackages("de.tum.in.www1.artemis");

        ArchRule noJUnit4Imports = noClasses().should().dependOnClassesThat().resideInAPackage("org.junit");
        ArchRule noPublicTests = noMethods().that().areAnnotatedWith(Test.class).or().areAnnotatedWith(ParameterizedTest.class).or().areAnnotatedWith(BeforeEach.class).or()
                .areAnnotatedWith(BeforeAll.class).or().areAnnotatedWith(AfterEach.class).or().areAnnotatedWith(AfterAll.class).should().bePublic();

        noJUnit4Imports.check(testClasses);
        noPublicTests.check(testClasses);
    }
}
