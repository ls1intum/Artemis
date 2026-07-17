package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import java.util.Map;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Thrown when saving or finalizing a generated exercise stops after persistence may have begun. It records whether canonical state changed and any exact repository commits that
 * remain available for instructor review.
 */
public class GenerationIncompleteException extends RuntimeException {

    private final boolean liveExerciseChanged;

    private final Map<RepositoryType, String> savedRepositoryCommits;

    public GenerationIncompleteException(String message, Throwable cause) {
        this(message, cause, true, Map.of());
    }

    public GenerationIncompleteException(String message, Throwable cause, boolean liveExerciseChanged, Map<RepositoryType, String> savedRepositoryCommits) {
        super(message, cause);
        this.liveExerciseChanged = liveExerciseChanged;
        this.savedRepositoryCommits = Map.copyOf(savedRepositoryCommits);
    }

    public boolean liveExerciseChanged() {
        return liveExerciseChanged;
    }

    public Map<RepositoryType, String> savedRepositoryCommits() {
        return savedRepositoryCommits;
    }
}
