package de.tum.in.www1.artemis.web.rest.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Optional;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;
import de.tum.in.www1.artemis.domain.participation.Participation;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationDTO(Long id, boolean testRun, String type, Integer submissionCount, ParticipationExerciseDTO exercise) implements Serializable {

    public static ParticipationDTO of(Participation participation) {
        return Optional.ofNullable(participation)
                .map(p -> new ParticipationDTO(p.getId(), p.isTestRun(), p.getType(), p.getSubmissionCount(), ParticipationExerciseDTO.of(p.getExercise()))).orElse(null);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ParticipationExerciseDTO(Long id, ExerciseType exerciseType, String type, AssessmentType assessmentType, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate,
            Double maxPoints, CourseDTO course) implements Serializable {

        @Nullable
        public static ParticipationExerciseDTO of(Exercise exercise) {
            return Optional.ofNullable(exercise).map(e -> new ParticipationExerciseDTO(e.getId(), e.getExerciseType(), e.getType(), e.getAssessmentType(), e.getDueDate(),
                    e.getAssessmentDueDate(), e.getMaxPoints(), CourseDTO.of(e.getCourseViaExerciseGroupOrCourseMember()))).orElse(null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseDTO(Long id, Integer accuracyOfScores) implements Serializable {

        @Nullable
        public static CourseDTO of(Course course) {
            return Optional.ofNullable(course).map(c -> new CourseDTO(c.getId(), c.getAccuracyOfScores())).orElse(null);
        }
    }
}
