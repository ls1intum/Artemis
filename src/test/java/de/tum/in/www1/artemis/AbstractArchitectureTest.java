package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

@Tag("ArchitectureTest")
public abstract class AbstractArchitectureTest {

    protected static final String ARTEMIS_PACKAGE = "de.tum.in.www1.artemis";

    protected static JavaClasses testClasses;

    protected static JavaClasses allClasses;

    protected static JavaClasses productionClasses;

    @BeforeAll
    static void loadClasses() {
        if (allClasses == null) {
            testClasses = new ClassFileImporter().withImportOption(new ImportOption.OnlyIncludeTests()).importPackages(ARTEMIS_PACKAGE);
            productionClasses = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages(ARTEMIS_PACKAGE);
            allClasses = new ClassFileImporter().importPackages(ARTEMIS_PACKAGE);
        }
        ensureClassSetsNonEmpty();
        ensureAllClassesFound();
    }

    private static void ensureClassSetsNonEmpty() {
        assertThat(testClasses).isNotEmpty();
        assertThat(productionClasses).isNotEmpty();
        assertThat(allClasses).isNotEmpty();
    }

    private static void ensureAllClassesFound() {
        assertThat(testClasses.size() + productionClasses.size()).isEqualTo(allClasses.size());
    }
}
