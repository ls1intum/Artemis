package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * The exercises of a course a given team takes part in, together with that team's participation per exercise.
 * <p>
 * The exercises carry no course: the entity payload nested them under {@code course.exercises}, where
 * {@code @JsonIgnoreProperties("course")} stripped the back reference, so the client never received one here either.
 *
 * @param id        the id of the course
 * @param exercises the team exercises of the course the team exists for
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseWithTeamExercisesDTO(Long id, Set<TeamExerciseDTO> exercises) implements Serializable {

    /**
     * Projects a course with the exercises a team takes part in.
     *
     * @param course    the course
     * @param exercises the already filtered exercises, each carrying the team and the team's participation
     * @return the projected course
     */
    public static CourseWithTeamExercisesDTO of(Course course, Set<Exercise> exercises) {
        return new CourseWithTeamExercisesDTO(course.getId(), exercises.stream().map(TeamExerciseDTO::of).collect(Collectors.toSet()));
    }

    /**
     * An exercise of the team detail page's participation table.
     *
     * @param id                    the id of the exercise
     * @param title                 the exercise title
     * @param type                  the exercise kind, which the client routes on
     * @param releaseDate           when the exercise was released
     * @param dueDate               the exercise due date
     * @param teams                 the team of this exercise, as a single-element list mirroring the entity payload
     * @param studentParticipations the team's participation in this exercise, as a single-element list
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TeamExerciseDTO(Long id, String title, String type, ZonedDateTime releaseDate, ZonedDateTime dueDate, List<TeamDTO> teams,
            List<TeamParticipationDTO> studentParticipations) implements Serializable {

        /**
         * Projects an exercise with the team and participation the resource set on it.
         *
         * @param exercise the exercise to project
         * @return the projected exercise
         */
        public static TeamExerciseDTO of(Exercise exercise) {
            // Both collections are only mapped when the resource set them; an exercise the team has no participation in
            // still carries the lazy collection of the entity, and reading it would load every participation of that exercise.
            List<TeamDTO> teams = Hibernate.isInitialized(exercise.getTeams()) ? exercise.getTeams().stream().map(TeamDTO::of).toList() : List.of();
            List<TeamParticipationDTO> participations = Hibernate.isInitialized(exercise.getStudentParticipations())
                    ? exercise.getStudentParticipations().stream().map(TeamParticipationDTO::of).toList()
                    : List.of();
            return new TeamExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getType(), exercise.getReleaseDate(), exercise.getDueDate(), teams, participations);
        }
    }
}
