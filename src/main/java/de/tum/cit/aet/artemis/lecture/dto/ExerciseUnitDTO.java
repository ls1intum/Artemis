package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseUnitDTO(Long id, String name, ZonedDateTime releaseDate, boolean completed, boolean visibleToStudents, Set<CompetencyLinkDTO> competencyLinks,
        ExerciseReferenceDTO exercise, @JsonProperty("type") String type) implements LectureUnitDTO {

    public ExerciseUnitDTO {
        type = "exercise";
    }

    public static ExerciseUnitDTO of(ExerciseUnit exerciseUnit) {
        return new ExerciseUnitDTO(exerciseUnit.getId(), exerciseUnit.getName(), exerciseUnit.getReleaseDate(), exerciseUnit.isCompleted(), exerciseUnit.isVisibleToStudents(),
                exerciseUnit.getCompetencyLinks().stream().map(CompetencyLinkDTO::of).collect(Collectors.toSet()), ExerciseReferenceDTO.of(exerciseUnit.getExercise()),
                exerciseUnit.getType());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExerciseReferenceDTO(Long id, String title, ZonedDateTime releaseDate, ExerciseType type) {

        public static ExerciseReferenceDTO of(Exercise exercise) {
            if (exercise == null) {
                return null;
            }
            return new ExerciseReferenceDTO(exercise.getId(), exercise.getTitle(), exercise.getReleaseDate(), exercise.getExerciseType());
        }
    }
}
