package de.tum.cit.aet.artemis.atlas.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.ExtractedContentDTO;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Extracts learning-relevant content from exercises into {@link ExtractedContentDTO}s
 * for downstream LLM consumption. Currently supports {@link ProgrammingExercise}.
 * <p>
 * To add a new exercise type:
 * <ol>
 * <li>Add an {@code instanceof} branch in {@link #extractContent(Exercise)}</li>
 * <li>Create a private {@code extractFrom*()} method accepting the concrete type</li>
 * <li>Always set {@code exerciseType} in metadata via {@link ExerciseType#getValue()}</li>
 * <li>Add corresponding tests in {@code ContentExtractionServiceTest}</li>
 * </ol>
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Service
public class ContentExtractionService {

    /**
     * Extracts learning-relevant content from the given exercise.
     *
     * @param exercise the exercise to extract content from
     * @return a DTO containing the title, learning text, and metadata
     * @throws IllegalArgumentException if the exercise type is not yet supported
     */
    public ExtractedContentDTO extractContent(Exercise exercise) {
        if (exercise instanceof ProgrammingExercise programmingExercise) {
            return extractFromProgrammingExercise(programmingExercise);
        }
        throw new IllegalArgumentException("Unsupported exercise type: " + exercise.getClass().getSimpleName());
    }

    private ExtractedContentDTO extractFromProgrammingExercise(ProgrammingExercise exercise) {
        String title = exercise.getTitle();
        String learningText = Objects.requireNonNullElse(exercise.getProblemStatement(), "");

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("exerciseType", ExerciseType.PROGRAMMING.getValue());

        if (exercise.getDifficulty() != null) {
            metadata.put("difficulty", exercise.getDifficulty().name().toLowerCase());
        }
        if (exercise.getMaxPoints() != null) {
            metadata.put("maxPoints", exercise.getMaxPoints().toString());
        }

        return new ExtractedContentDTO(title, learningText, metadata);
    }
}
