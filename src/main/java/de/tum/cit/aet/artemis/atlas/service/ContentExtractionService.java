package de.tum.cit.aet.artemis.atlas.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.dto.ExtractedContentDTO;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Extracts learning-relevant content from {@link LearningObject}s (exercises and lecture units)
 * into {@link ExtractedContentDTO}s for downstream LLM consumption. Currently supports
 * {@link ProgrammingExercise}; other exercise types and lecture unit types will follow.
 * <p>
 * To add a new learning object type:
 * <ol>
 * <li>Add an {@code instanceof} branch in {@link #extractContent(LearningObject)} for the new
 * {@code LearningObject} subtype</li>
 * <li>Create a private {@code extractFrom*()} method accepting the concrete type</li>
 * <li>Always set {@code exerciseType} in metadata via {@link ExerciseType#getValue()} (for exercises)
 * or an equivalent type discriminator for lecture units</li>
 * <li>Add corresponding tests in {@code ContentExtractionServiceTest}</li>
 * </ol>
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Service
public class ContentExtractionService {

    /**
     * Extracts learning-relevant content from the given learning object.
     *
     * @param learningObject the learning object to extract content from
     * @return a DTO containing the title, learning text, and metadata
     * @throws IllegalArgumentException if the learning object type is not yet supported
     */
    public ExtractedContentDTO extractContent(LearningObject learningObject) {
        Objects.requireNonNull(learningObject, "learningObject must not be null");
        if (learningObject instanceof ProgrammingExercise programmingExercise) {
            return extractFromProgrammingExercise(programmingExercise);
        }
        throw new IllegalArgumentException("Unsupported learning object type: " + learningObject.getClass().getSimpleName());
    }

    private ExtractedContentDTO extractFromProgrammingExercise(ProgrammingExercise exercise) {
        String title = Objects.requireNonNullElse(exercise.getTitle(), "");
        String learningText = Objects.requireNonNullElse(exercise.getProblemStatement(), "");

        // LinkedHashMap preserves insertion order for deterministic JSON serialization
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("exerciseType", ExerciseType.PROGRAMMING.getValue());

        if (exercise.getDifficulty() != null) {
            metadata.put("difficulty", exercise.getDifficulty().name().toLowerCase(Locale.ROOT));
        }
        if (exercise.getMaxPoints() != null) {
            metadata.put("maxPoints", String.format(Locale.ROOT, "%.1f", exercise.getMaxPoints()));
        }

        return new ExtractedContentDTO(title, learningText, metadata);
    }
}
