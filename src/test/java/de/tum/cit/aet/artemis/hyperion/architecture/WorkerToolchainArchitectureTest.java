package de.tum.cit.aet.artemis.hyperion.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

@Tag("ArchitectureTest")
class WorkerToolchainArchitectureTest {

    @Test
    void workloadConsumersOnlyAccessPublicWorkerContracts() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("de.tum.cit.aet.artemis.hyperion");
        noClasses().should().dependOnClassesThat().resideInAnyPackage("..aiworker.service..", "..aiworker.config..").check(classes);
    }

    @Test
    void workerLifecycleCannotDependOnLanguageImplementations() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("de.tum.cit.aet.artemis.hyperion.service.worker",
                "de.tum.cit.aet.artemis.hyperion.config.worker");
        noClasses().that().resideInAnyPackage("..hyperion.service.worker", "..hyperion.service.worker.messaging..", "..hyperion.config.worker..").should().dependOnClassesThat()
                .resideInAnyPackage("..hyperion.service.worker.toolchain..", "javax.lang.model..", "com.sun.source..").check(classes);
    }
}
