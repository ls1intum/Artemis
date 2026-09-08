package de.tum.cit.aet.artemis.hyperionworker.generation.verification;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperionworker.generation.RepositoryRole;
import de.tum.cit.aet.artemis.hyperionworker.generation.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperionworker.generation.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.hyperionworker.generation.workspace.WorkspaceArchive;
import de.tum.cit.aet.artemis.hyperionworker.sandbox.InteractiveSandbox;

/** Applies one semantic-mutant probe to the live workspace and guarantees restoration around the build. */
final class SemanticMutantWorkspaceProbe {

    private SemanticMutantWorkspaceProbe() {
    }

    static BuildSummary run(InteractiveSandbox sandbox, String sessionId, @Nullable SemanticMutant mutant, Map.@Nullable Entry<String, String> counterexampleProbe,
            Runnable restoreCandidate, Runnable prepareBuild, Supplier<BuildSummary> build) {
        try {
            restoreCandidate.run();
            Map<String, String> files = new LinkedHashMap<>();
            if (mutant != null) {
                files.put(GenerationWorkspaceService.directoryFor(RepositoryRole.SOLUTION) + "/" + mutant.solutionPath(), mutant.mutantSource());
            }
            if (counterexampleProbe != null) {
                files.put(GenerationWorkspaceService.directoryFor(RepositoryRole.TESTS) + "/" + counterexampleProbe.getKey(), counterexampleProbe.getValue());
            }
            if (!files.isEmpty()) {
                sandbox.copyIn(sessionId, GenerationWorkspaceService.WORKSPACE, WorkspaceArchive.buildWorkspaceTarStream(files, Map.of()));
            }
            prepareBuild.run();
            return build.get();
        }
        finally {
            try {
                restoreCandidate.run();
            }
            catch (RuntimeException exception) {
                throw new DifferentialVerificationService.VerificationInfrastructureException("The semantic-mutant probe could not restore the verified candidate", exception);
            }
        }
    }
}
