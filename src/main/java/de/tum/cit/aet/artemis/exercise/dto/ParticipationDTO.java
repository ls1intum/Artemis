package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationDTO(Long id, boolean testRun, String type, InitializationState initializationState, Integer submissionCount, ParticipationExerciseDTO exercise)
        implements Serializable {

    public static ParticipationDTO of(Participation participation) {
        return Optional.ofNullable(participation).map(
                p -> new ParticipationDTO(p.getId(), p.isTestRun(), p.getType(), p.getInitializationState(), p.getSubmissionCount(), ParticipationExerciseDTO.of(p.getExercise())))
                .orElse(null);
    }

    /**
     * Minimal exercise context of a participation.
     *
     * @param id                the unique identifier of the exercise
     * @param exerciseType      the exercise category
     * @param type              the polymorphic exercise discriminator
     * @param assessmentType    the configured assessment type, if available
     * @param dueDate           the due date, if configured
     * @param assessmentDueDate the assessment due date, if configured
     * @param maxPoints         the maximum achievable points, if configured
     * @param course            the course the exercise belongs to, resolved through the exam for an exam exercise
     * @param title             the exercise title, if available
     * @param teamMode          whether students participate as a team
     * @param releaseDate       the release date, if configured
     * @param startDate         the start date, if configured
     * @param exerciseGroup     the exam exercise group, if this is an exam exercise whose exam is visible
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ParticipationExerciseDTO(Long id, ExerciseType exerciseType, String type, AssessmentType assessmentType, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate,
            Double maxPoints, @Nullable CourseDTO course, @Nullable String title, boolean teamMode, @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate,
            @Nullable ParticipationExerciseGroupDTO exerciseGroup) implements Serializable {

        /**
         * Maps an {@link Exercise} to a {@link ParticipationExerciseDTO}.
         * <p>
         * Student-facing endpoints mask exam exercises by stripping {@code exerciseGroup.exam} before mapping (the
         * masked-exam state). In that state {@link Exercise#getCourseViaExerciseGroupOrCourseMember()} would dereference the
         * now-missing exam and throw, so the course and the exercise group are omitted instead.
         *
         * @param exercise the exercise to convert (may be {@code null})
         * @return the corresponding DTO, or {@code null} if the input was {@code null}
         */
        @Nullable
        public static ParticipationExerciseDTO of(Exercise exercise) {
            return Optional.ofNullable(exercise).map(e -> {
                ExerciseGroup exerciseGroup = e.getExerciseGroup();
                Exam exam = exerciseGroup != null ? exerciseGroup.getExam() : null;
                Course course = e.isExamExercise() && exam == null ? null : e.getCourseViaExerciseGroupOrCourseMember();
                ParticipationExerciseGroupDTO exerciseGroupDTO = exam != null ? new ParticipationExerciseGroupDTO(exerciseGroup.getId(), new ParticipationExamDTO(exam.getId()))
                        : null;
                return new ParticipationExerciseDTO(e.getId(), e.getExerciseType(), e.getType(), e.getAssessmentType(), e.getDueDate(), e.getAssessmentDueDate(), e.getMaxPoints(),
                        CourseDTO.of(course), e.getTitle(), e.isTeamMode(), e.getReleaseDate(), e.getStartDate(), exerciseGroupDTO);
            }).orElse(null);
        }
    }

    /**
     * Minimal exam exercise-group context of an exam participation.
     *
     * @param id   the unique identifier of the exercise group
     * @param exam the exam the group belongs to
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ParticipationExerciseGroupDTO(long id, ParticipationExamDTO exam) implements Serializable {
    }

    /**
     * Minimal exam context of an exam participation.
     *
     * @param id the unique identifier of the exam
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ParticipationExamDTO(long id) implements Serializable {
    }

    /**
     * Minimal course context of a participation.
     *
     * @param id               the unique identifier of the course
     * @param title            the course title, if available
     * @param shortName        the course short name, if available
     * @param accuracyOfScores the configured number of decimal places for scores, if available
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseDTO(Long id, @Nullable String title, @Nullable String shortName, Integer accuracyOfScores) implements Serializable {

        @Nullable
        public static CourseDTO of(Course course) {
            return Optional.ofNullable(course).map(c -> new CourseDTO(c.getId(), c.getTitle(), c.getShortName(), c.getAccuracyOfScores())).orElse(null);
        }
    }
}
