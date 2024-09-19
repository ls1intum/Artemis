package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.Instant;
import java.time.chrono.ChronoZonedDateTime;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.TextExercise;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisTextExerciseDTO(long id, String title, PyrisCourseDTO course, String problemStatement, Instant startDate, Instant endDate) {

    public PyrisTextExerciseDTO(TextExercise exercise) {
        this(exercise.getId(), exercise.getTitle(), new PyrisCourseDTO(exercise.getCourseViaExerciseGroupOrCourseMember()), exercise.getProblemStatement(),
                Optional.ofNullable(exercise.getStartDate()).map(ChronoZonedDateTime::toInstant).orElse(null),
                Optional.ofNullable(exercise.getDueDate()).map(ChronoZonedDateTime::toInstant).orElse(null));
    }

}
