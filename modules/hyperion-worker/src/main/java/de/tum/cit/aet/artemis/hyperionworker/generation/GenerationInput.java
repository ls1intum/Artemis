package de.tum.cit.aet.artemis.hyperionworker.generation;

import java.util.Set;

import org.jspecify.annotations.Nullable;

/** Immutable Java Gradle authoring facts; no persistence entity or repository address reaches the worker. */
public record GenerationInput(long id, String packageName, @Nullable String problemStatement, boolean hasDueDate, Set<String> baselineGradedTestNames) {

    public GenerationInput {
        baselineGradedTestNames = Set.copyOf(baselineGradedTestNames);
    }
}
