package de.tum.cit.aet.artemis.hyperion.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleRepositoryArchitectureTest;

class HyperionRepositoryArchitectureTest extends AbstractModuleRepositoryArchitectureTest {

    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".hyperion";
    }

    // TODO: This method should be removed once all repositories are tested
    @Override
    protected Set<String> testTransactionalExclusions() {
        // updateProblemStatementAndTasks couples the problem-statement/title compare-and-set with the resulting task rebuild so the two are never observed half-applied; the
        // transaction contains no Git/CI/network I/O.
        return Set.of("de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.ProblemStatementMetadataUpdateService.updateProblemStatementAndTasks("
                + "de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise, java.lang.String, java.lang.String, java.lang.String, java.lang.String)");
    }
}
