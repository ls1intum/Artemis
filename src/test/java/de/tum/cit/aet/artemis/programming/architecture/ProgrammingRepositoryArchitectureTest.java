package de.tum.cit.aet.artemis.programming.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleRepositoryArchitectureTest;

public class ProgrammingRepositoryArchitectureTest extends AbstractModuleRepositoryArchitectureTest {

    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".programming";
    }

    // TODO: This method should be removed once all repositories are tested
    @Override
    protected Set<String> testTransactionalExclusions() {
        return Set.of(
                "de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseImportBasicService.importProgrammingExerciseBasis(de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise, de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise)");
    }
}
