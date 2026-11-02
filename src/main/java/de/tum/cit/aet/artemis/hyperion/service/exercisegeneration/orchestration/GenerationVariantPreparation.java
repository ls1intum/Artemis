package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.Map;

import org.jspecify.annotations.NonNull;

import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/** Core-local source baseline; copying does not reserve or block ordinary source edits. */
public record GenerationVariantPreparation(long sourceExerciseId, @NonNull SourceSnapshot sourceSnapshot, VariantGenerationRequestDTO request) {

    /** Immutable metadata and exact Git heads checked around source copying. */
    public record SourceSnapshot(Map<RepositoryType, String> repositoryHeads, @NonNull ExerciseSnapshotDTO metadata, Long courseId, Long exerciseGroupId) {

        public SourceSnapshot {
            repositoryHeads = Map.copyOf(repositoryHeads);
        }
    }
}
