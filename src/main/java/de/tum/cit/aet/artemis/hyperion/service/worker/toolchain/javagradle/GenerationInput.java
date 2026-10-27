package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.protocol.GenerationBuildTool;

/** Immutable Java authoring facts; no persistence entity or repository address reaches the worker. */
public record GenerationInput(long id, String packageName, @Nullable String problemStatement, boolean hasDueDate, Set<String> baselineGradedTestNames,
        Set<String> baselineHiddenTestNames, GenerationBuildTool buildTool) {

    public GenerationInput {
        baselineGradedTestNames = Set.copyOf(baselineGradedTestNames);
        baselineHiddenTestNames = Set.copyOf(baselineHiddenTestNames);
        if (buildTool == null) {
            throw new IllegalArgumentException("A Java build tool is required");
        }
    }
}
