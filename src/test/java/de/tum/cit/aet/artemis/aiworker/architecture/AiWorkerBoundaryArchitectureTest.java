package de.tum.cit.aet.artemis.aiworker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

@Tag("ArchitectureTest")
class AiWorkerBoundaryArchitectureTest {

    @Test
    void coordinationAndTransportDoNotDependOnWorkloadFeatures() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("de.tum.cit.aet.artemis.aiworker");
        noClasses()
                .should().dependOnClassesThat().resideInAnyPackage("de.tum.cit.aet.artemis.hyperion..", "de.tum.cit.aet.artemis.iris..", "de.tum.cit.aet.artemis.athena..",
                        "de.tum.cit.aet.artemis.programming..", "de.tum.cit.aet.artemis.exercise..", "de.tum.cit.aet.artemis.localci..", "de.tum.cit.aet.artemis.localvc..")
                .check(classes);
    }
}
