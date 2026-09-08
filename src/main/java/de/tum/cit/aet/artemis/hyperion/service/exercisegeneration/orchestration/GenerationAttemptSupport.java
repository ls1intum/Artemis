package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/** Small immutable-data helpers shared by generation-attempt orchestration. */
final class GenerationAttemptSupport {

    private GenerationAttemptSupport() {
    }

    static AgentLoopResult cancelledResult(AgentLoopResult lastResult) {
        return new AgentLoopResult(AgentLoopResult.Status.CANCELLED, lastResult.turns(), lastResult.finalMessage());
    }

    static Map<RepositoryType, Map<String, String>> copyProducedFiles(Map<RepositoryType, Map<String, String>> producedFiles) {
        Map<RepositoryType, Map<String, String>> copy = new EnumMap<>(RepositoryType.class);
        producedFiles.forEach((type, files) -> copy.put(type, Map.copyOf(files)));
        return Map.copyOf(copy);
    }

    static boolean hasProducedChanges(Map<RepositoryType, Map<String, String>> baselineFiles, Map<RepositoryType, Map<String, String>> producedFiles,
            @Nullable String baselineProblemStatement, String producedProblemStatement) {
        if (!Objects.equals(baselineProblemStatement == null ? "" : baselineProblemStatement.trim(), producedProblemStatement)) {
            return true;
        }
        return List.of(RepositoryType.SOLUTION, RepositoryType.TEMPLATE, RepositoryType.TESTS).stream()
                .anyMatch(type -> !baselineFiles.getOrDefault(type, Map.of()).equals(producedFiles.getOrDefault(type, Map.of())));
    }

    static void addIfExtractionFailed(Set<String> extractionFailed, GenerationWorkspaceService.RepositoryExtraction extraction, RepositoryType type) {
        if (extraction.extractionFailed()) {
            extractionFailed.add(GenerationWorkspaceService.directoryFor(type));
        }
    }
}
