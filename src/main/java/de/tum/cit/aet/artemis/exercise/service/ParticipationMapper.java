package de.tum.cit.aet.artemis.exercise.service;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationDTO;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationDTO.CourseDTO;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationDTO.ParticipationExerciseDTO;

/** Maps persisted data to the records exchanged with clients and build agents. */
public final class ParticipationMapper {

    private ParticipationMapper() {
    }

    public static ParticipationDTO of(Participation participation) {
        return Optional.ofNullable(participation)
                .map(p -> new ParticipationDTO(p.getId(), p.isTestRun(), p.getType(), p.getInitializationState(), p.getSubmissionCount(), exercise(p.getExercise()))).orElse(null);
    }

    /**
     * Maps an {@link Exercise} to a {@link ParticipationExerciseDTO}. An exam exercise whose exam was masked out for
     * a student-facing payload resolves to a {@code null} course.
     *
     * @param exercise the exercise to convert (may be {@code null})
     * @return the corresponding DTO, or {@code null} if the input was {@code null}
     */
    @Nullable
    public static ParticipationExerciseDTO exercise(Exercise exercise) {
        return Optional.ofNullable(exercise).map(e -> {
            Course course = e.getCourseViaExerciseGroupOrCourseMember();
            return new ParticipationExerciseDTO(e.getId(), e.getExerciseType(), e.getType(), e.getAssessmentType(), e.getDueDate(), e.getAssessmentDueDate(), e.getMaxPoints(),
                    course(course));
        }).orElse(null);
    }

    @Nullable
    private static CourseDTO course(Course course) {
        return Optional.ofNullable(course).map(c -> new CourseDTO(c.getId(), c.getAccuracyOfScores())).orElse(null);
    }
}
