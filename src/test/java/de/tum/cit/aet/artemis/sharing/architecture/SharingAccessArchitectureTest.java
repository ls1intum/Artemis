package de.tum.cit.aet.artemis.sharing.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.dto.ImportProgrammingExerciseRequestDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResponseDTO;
import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

class SharingAccessArchitectureTest extends AbstractArchitectureTest {

    private static final String SHARING = ARTEMIS_PACKAGE + ".sharing..";

    @Test
    void sharingUsesSharedCoreAndTheProgrammingArchiveApi() {
        var availablePackages = resideInAnyPackage(SHARING, ARTEMIS_PACKAGE + ".core..", ARTEMIS_PACKAGE + ".programming.api..");
        var archiveContracts = equivalentTo(ImportProgrammingExerciseRequestDTO.class).or(equivalentTo(ProgrammingExerciseResponseDTO.class));
        classes().that().resideInAPackage(SHARING).should().onlyDependOnClassesThat(resideOutsideOfPackage(ARTEMIS_PACKAGE + "..").or(availablePackages).or(archiveContracts))
                .check(productionClasses);
    }

    @Test
    void otherModulesCanRunWithoutSharing() {
        noClasses().that().resideOutsideOfPackage(SHARING).should().dependOnClassesThat().resideInAPackage(SHARING).check(productionClasses);
    }
}
