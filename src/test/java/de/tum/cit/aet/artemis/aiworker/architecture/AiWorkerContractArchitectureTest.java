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

import de.tum.cit.aet.artemis.aiworker.api.ExecutionObserver;
import de.tum.cit.aet.artemis.aiworker.api.InteractiveSandbox;
import de.tum.cit.aet.artemis.aiworker.api.SandboxApi;
import de.tum.cit.aet.artemis.aiworker.api.SandboxUnavailableException;
import de.tum.cit.aet.artemis.aiworker.api.WorkerMessageCodecApi;
import de.tum.cit.aet.artemis.aiworker.api.WorkloadApi;

/** Local execution contracts have no Spring service, model-provider or domain dependency. */
@Tag("ArchitectureTest")
class AiWorkerContractArchitectureTest {

    private static final ClassFileImporter IMPORTER = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS);

    private static final JavaClasses CLASSES = IMPORTER.importPackages("de.tum.cit.aet.artemis.aiworker.domain", "de.tum.cit.aet.artemis.aiworker.dto");

    private static final JavaClasses PORTABLE_API = IMPORTER.importClasses(ExecutionObserver.class, InteractiveSandbox.class, SandboxApi.class, SandboxUnavailableException.class,
            WorkerMessageCodecApi.class, WorkloadApi.class);

    @Test
    void contractsAreIndependentOfImplementationsAndWorkloads() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage("..hyperion..", "..aiworker.service..", "..aiworker.config..", "de.tum.cit.aet.artemis.core..",
                "org.springframework..", "com.github.dockerjava..", "com.hazelcast..", "org.redisson..", "jakarta.persistence..").check(CLASSES);
        noClasses().should().dependOnClassesThat().resideInAnyPackage("..hyperion..", "..aiworker.service..", "..aiworker.config..", "de.tum.cit.aet.artemis.core..",
                "org.springframework..", "com.github.dockerjava..", "com.hazelcast..", "org.redisson..", "jakarta.persistence..").check(PORTABLE_API);
    }

    @Test
    void dtosFollowTheSameZeroExceptionConventionAsServerModules() {
        classes().that().resideInAPackage("..dto..").should().haveSimpleNameEndingWith("DTO").andShould().beRecords().andShould().beAnnotatedWith(JsonInclude.class).check(CLASSES);
    }

    @Test
    void contractImportIsNotEmpty() {
        assertThat(PORTABLE_API.stream().map(type -> type.getName())).contains("de.tum.cit.aet.artemis.aiworker.api.SandboxApi");
        assertThat(CLASSES.stream().map(type -> type.getName())).contains("de.tum.cit.aet.artemis.aiworker.dto.SandboxPolicyDTO");
    }
}
