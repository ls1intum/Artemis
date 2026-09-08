package de.tum.cit.aet.artemis.hyperion.runtime.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

class RuntimeArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("de.tum.cit.aet.artemis.hyperion.runtime", "de.tum.cit.aet.artemis.hyperion.protocol");

    @Test
    void runtimeCannotDependOnServerFeatures() {
        noClasses().should().dependOnClassesThat(new DescribedPredicate<>("belong to the Artemis server rather than the runtime/protocol") {

            @Override
            public boolean test(JavaClass type) {
                return type.getName().startsWith("de.tum.cit.aet.artemis.") && !type.getName().startsWith("de.tum.cit.aet.artemis.hyperion.runtime.")
                        && !type.getName().startsWith("de.tum.cit.aet.artemis.hyperion.protocol.");
            }
        }).check(classes);
    }

    @Test
    void runtimeCannotUseDatabaseGridOrIncomingWebServices() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage("java.sql..", "javax.sql..", "jakarta.persistence..", "org.springframework.data..", "org.hibernate..",
                "com.hazelcast..", "org.redisson..", "org.springframework.jdbc..", "jakarta.servlet..", "org.springframework.web.servlet..", "org.springframework.web.socket..")
                .check(classes);
    }

    @Test
    void extractedClassesStillHaveTheRepositorySizeLimit() throws IOException {
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                try (var lines = Files.lines(file)) {
                    assertThat(lines.count()).as("line count of %s", file).isLessThanOrEqualTo(1_000);
                }
            }
        }
    }
}
