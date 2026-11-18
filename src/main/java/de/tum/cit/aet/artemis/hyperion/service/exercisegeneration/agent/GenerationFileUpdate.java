package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;

/** A successful file mutation and the resulting text kept for the instructor's live, read-only candidate preview. */
public record GenerationFileUpdate(ExerciseGenerationFileChangeDTO change, @Nullable String content) {
}
