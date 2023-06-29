package de.tum.in.www1.artemis;

import org.junit.jupiter.api.BeforeAll;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

public class AbstractArchitectureTest {

    protected static final String ARTEMIS_PACKAGE = "de.tum.in.www1.artemis";

    protected static JavaClasses testClasses;

    protected static JavaClasses allClasses;

    @BeforeAll
    static void loadClasses() {
        testClasses = new ClassFileImporter().withImportOption(new ImportOption.OnlyIncludeTests()).importPackages(ARTEMIS_PACKAGE);
        allClasses = new ClassFileImporter().importPackages(ARTEMIS_PACKAGE);
    }
}
