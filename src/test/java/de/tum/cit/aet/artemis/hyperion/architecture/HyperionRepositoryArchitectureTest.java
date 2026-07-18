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
        // ProblemStatementMetadataUpdateService.updateProblemStatementAndTasks deliberately couples the problem-statement/title compare-and-set write with the resulting task
        // rebuild in a single narrow database transaction, so the two can never be observed half-applied (no Git/CI/network I/O happens inside it). See the class javadoc.
        return Set.of("de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.ProblemStatementMetadataUpdateService.updateProblemStatementAndTasks("
                + "de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise, java.lang.String, java.lang.String, java.lang.String, java.lang.String)");
    }
}
