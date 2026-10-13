package de.tum.cit.aet.artemis.aiworker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/** Local execution contracts have no Spring service, model-provider or domain dependency. */
@Tag("ArchitectureTest")
class AiWorkerContractArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("de.tum.cit.aet.artemis.aiworker.api", "de.tum.cit.aet.artemis.aiworker.domain", "de.tum.cit.aet.artemis.aiworker.dto");

    @Test
    void contractsAreIndependentOfImplementationsAndWorkloads() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage("..hyperion..", "..aiworker.service..", "..aiworker.config..", "de.tum.cit.aet.artemis.core..",
                "org.springframework..", "com.github.dockerjava..", "com.hazelcast..", "org.redisson..", "jakarta.persistence..").check(CLASSES);
    }

    @Test
    void dtosFollowTheSameZeroExceptionConventionAsServerModules() {
        classes().that().resideInAPackage("..dto..").should().haveSimpleNameEndingWith("DTO").andShould().beRecords().andShould().beAnnotatedWith(JsonInclude.class).check(CLASSES);
    }

    @Test
    void contractImportIsNotEmpty() {
        assertThat(CLASSES.stream().map(type -> type.getName())).contains("de.tum.cit.aet.artemis.aiworker.api.SandboxApi", "de.tum.cit.aet.artemis.aiworker.dto.SandboxPolicyDTO");
    }
}
