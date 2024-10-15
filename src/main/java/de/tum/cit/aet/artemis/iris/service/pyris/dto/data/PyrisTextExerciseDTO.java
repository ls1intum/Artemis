package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import java.time.Instant;
import java.time.chrono.ChronoZonedDateTime;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.text.domain.TextExercise;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisTextExerciseDTO(long id, String title, PyrisCourseDTO course, String problemStatement, Instant startDate, Instant endDate) {

    /**
     * Create a new PyrisTextExerciseDTO from the given TextExercise
     *
     * @param exercise the exercise
     * @return the dto
     */
    public static PyrisTextExerciseDTO of(TextExercise exercise) {
        // @formatter:off
       return new PyrisTextExerciseDTO(
                exercise.getId(),
                exercise.getTitle(),
                PyrisCourseDTO.of(exercise.getCourseViaExerciseGroupOrCourseMember()),
                exercise.getProblemStatement(),
                Optional.ofNullable(exercise.getStartDate()).map(ChronoZonedDateTime::toInstant).orElse(null),
                Optional.ofNullable(exercise.getDueDate()).map(ChronoZonedDateTime::toInstant).orElse(null)
        );
        // @formatter:on
    }

}
