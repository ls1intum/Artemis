package de.tum.cit.aet.artemis.hyperion.runtime.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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
    void protocolCannotDependOnRuntimeOrFrameworkServices() {
        noClasses().that().resideInAPackage("de.tum.cit.aet.artemis.hyperion.protocol..").should().dependOnClassesThat()
                .resideInAnyPackage("de.tum.cit.aet.artemis.hyperion.runtime..", "org.springframework..", "io.micrometer..").check(classes);
    }

    @Test
    void runtimeCannotDependOnServerFeatures() {
        noClasses().should().dependOnClassesThat(new DescribedPredicate<>("belong to the Artemis server rather than the runtime/protocol") {

            @Override
            public boolean test(JavaClass type) {
                return type.getName().startsWith("de.tum.cit.aet.artemis.") && !type.getName().startsWith("de.tum.cit.aet.artemis.hyperion.runtime.")
                        && !type.getName().startsWith("de.tum.cit.aet.artemis.hyperion.protocol.") && !type.getName().startsWith("de.tum.cit.aet.artemis.aiworker.api.")
                        && !type.getName().startsWith("de.tum.cit.aet.artemis.aiworker.dto.") && !type.getName().startsWith("de.tum.cit.aet.artemis.aiworker.domain.");
            }
        }).check(classes);
    }

    @Test
    void runtimeCannotUseDatabaseGridOrIncomingWebServices() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage("java.sql..", "javax.sql..", "jakarta.persistence..", "org.springframework.data..", "org.hibernate..",
                "com.hazelcast..", "org.redisson..", "org.springframework.jdbc..", "jakarta.servlet..", "org.springframework.web.servlet..", "org.springframework.web.socket..")
                .check(classes);
    }

}
