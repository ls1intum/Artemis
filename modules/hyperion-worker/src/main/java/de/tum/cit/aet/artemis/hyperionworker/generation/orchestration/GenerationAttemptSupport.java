package de.tum.cit.aet.artemis.hyperionworker.generation.orchestration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.runtime.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperionworker.generation.RepositoryRole;
import de.tum.cit.aet.artemis.hyperionworker.generation.workspace.GenerationWorkspaceService;

/** Small immutable-data helpers shared by generation-attempt orchestration. */
final class GenerationAttemptSupport {

    private GenerationAttemptSupport() {
    }

    static AgentLoopResult cancelledResult(AgentLoopResult lastResult) {
        return new AgentLoopResult(AgentLoopResult.Status.CANCELLED, lastResult.turns(), lastResult.finalMessage());
    }

    static Map<RepositoryRole, Map<String, String>> copyProducedFiles(Map<RepositoryRole, Map<String, String>> producedFiles) {
        Map<RepositoryRole, Map<String, String>> copy = new EnumMap<>(RepositoryRole.class);
        producedFiles.forEach((type, files) -> copy.put(type, Map.copyOf(files)));
        return Map.copyOf(copy);
    }

    static boolean hasProducedChanges(Map<RepositoryRole, Map<String, String>> baselineFiles, Map<RepositoryRole, Map<String, String>> producedFiles,
            @Nullable String baselineProblemStatement, String producedProblemStatement) {
        if (!Objects.equals(baselineProblemStatement == null ? "" : baselineProblemStatement.trim(), producedProblemStatement)) {
            return true;
        }
        return List.of(RepositoryRole.SOLUTION, RepositoryRole.TEMPLATE, RepositoryRole.TESTS).stream()
                .anyMatch(type -> !baselineFiles.getOrDefault(type, Map.of()).equals(producedFiles.getOrDefault(type, Map.of())));
    }

    static void addIfExtractionFailed(Set<String> extractionFailed, GenerationWorkspaceService.RepositoryExtraction extraction, RepositoryRole type) {
        if (extraction.extractionFailed()) {
            extractionFailed.add(GenerationWorkspaceService.directoryFor(type));
        }
    }
}
