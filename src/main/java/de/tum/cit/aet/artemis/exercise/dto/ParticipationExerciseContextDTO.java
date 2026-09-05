package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

/**
 * Minimal exercise context included in participation responses.
 *
 * @param id                the unique identifier of the exercise
 * @param title             the exercise title, if available
 * @param type              the polymorphic exercise discriminator
 * @param exerciseType      the exercise category
 * @param assessmentType    the configured assessment type, if available
 * @param releaseDate       the release date, if configured
 * @param startDate         the start date, if configured
 * @param dueDate           the due date, if configured
 * @param assessmentDueDate the assessment due date, if configured
 * @param maxPoints         the maximum achievable points, if configured
 * @param course            the minimal course context, if initialized
 * @param exerciseGroup     the minimal exam exercise-group context, if initialized
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationExerciseContextDTO(Long id, @Nullable String title, String type, ExerciseType exerciseType, @Nullable AssessmentType assessmentType,
        @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate,
        @Nullable Double maxPoints, @Nullable ParticipationCourseContextDTO course, @Nullable ParticipationExerciseGroupContextDTO exerciseGroup) {

    /**
     * Minimal exercise-group context required by participation clients.
     *
     * @param id   the unique identifier of the exercise group
     * @param exam the minimal exam context
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ParticipationExerciseGroupContextDTO(long id, ParticipationExamContextDTO exam) {
    }

    /**
     * Minimal exam context required by participation clients.
     *
     * @param id the unique identifier of the exam
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ParticipationExamContextDTO(long id) {
    }

    /**
     * Creates a minimal exercise context.
     *
     * @param exercise the exercise to map
     * @return the minimal exercise context
     */
    public static ParticipationExerciseContextDTO of(Exercise exercise) {
        ParticipationCourseContextDTO courseDTO = mapCourse(exercise);
        ParticipationExerciseGroupContextDTO exerciseGroupDTO = mapExerciseGroup(exercise.getExerciseGroup());
        return new ParticipationExerciseContextDTO(exercise.getId(), exercise.getTitle(), exercise.getType(), exercise.getExerciseType(), exercise.getAssessmentType(),
                exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getMaxPoints(), courseDTO, exerciseGroupDTO);
    }

    @Nullable
    private static ParticipationCourseContextDTO mapCourse(Exercise exercise) {
        Course course;
        if (exercise.isCourseExercise()) {
            course = exercise.getCourseViaExerciseGroupOrCourseMember();
        }
        else {
            ExerciseGroup exerciseGroup = exercise.getExerciseGroup();
            if (exerciseGroup == null || !Hibernate.isInitialized(exerciseGroup)) {
                return null;
            }
            Exam exam = exerciseGroup.getExam();
            if (exam == null || !Hibernate.isInitialized(exam)) {
                return null;
            }
            course = exam.getCourse();
        }
        return course != null && Hibernate.isInitialized(course) ? ParticipationCourseContextDTO.of(course) : null;
    }

    @Nullable
    private static ParticipationExerciseGroupContextDTO mapExerciseGroup(@Nullable ExerciseGroup exerciseGroup) {
        if (exerciseGroup == null || !Hibernate.isInitialized(exerciseGroup)) {
            return null;
        }
        Exam exam = exerciseGroup.getExam();
        if (exam == null || !Hibernate.isInitialized(exam)) {
            return null;
        }
        Course course = exam.getCourse();
        if (course == null || !Hibernate.isInitialized(course)) {
            return null;
        }
        return new ParticipationExerciseGroupContextDTO(exerciseGroup.getId(), new ParticipationExamContextDTO(exam.getId()));
    }
}
