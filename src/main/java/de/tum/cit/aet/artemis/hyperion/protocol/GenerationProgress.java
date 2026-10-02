package de.tum.cit.aet.artemis.hyperion.protocol;

import java.time.Instant;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Structured progress mapped by core onto the existing instructor transcript. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GenerationProgress(@Nullable String phase, @Nullable RepairRound repairRound, @Nullable FileChange fileChange, @Nullable ProviderUsageUpdate usage) {

    public GenerationProgress {
        if ((phase == null ? 0 : 1) + (repairRound == null ? 0 : 1) + (fileChange == null ? 0 : 1) + (usage == null ? 0 : 1) != 1) {
            throw new IllegalArgumentException("A progress update must describe exactly one kind of activity");
        }
        if (phase != null && !Set.of("PREPARING", "DESIGNING", "VERIFYING", "REVIEWING", "REPAIRING").contains(phase)) {
            throw new IllegalArgumentException("Worker cannot report a core persistence phase");
        }
    }

    /** Stable semantic-repair bookkeeping, not a scheduling policy. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record RepairRound(int round, int attempt, int blocking, int advisory, int carriedOver, int drained, int fresh) {
    }

    /** Successful text-file mutation and its bounded preview. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record FileChange(String path, String action, int turn, Instant timestamp, @Nullable String content) {

        public FileChange {
            WorkspaceFile.validatePath(path);
            if (timestamp == null || !Set.of("write", "edit", "delete").contains(action) || turn < 0 || (content != null && content.length() > 65_536)) {
                throw new IllegalArgumentException("Invalid file activity preview");
            }
        }
    }
}
