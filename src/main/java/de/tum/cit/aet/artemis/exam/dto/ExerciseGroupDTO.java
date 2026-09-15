package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamMode;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * DTO wire representation of an {@link ExerciseGroup}.
 * <p>
 * The same record serves several endpoints; the two nullable components differ per endpoint:
 * <ul>
 * <li>single-group responses (create / update / get-by-id) carry the nested {@link ExamForExerciseGroupDTO} (which the
 * exercise editors read to rebuild course / exam references) and omit the (previously never serialized, lazy)
 * exercises list;</li>
 * <li>list / import responses carry the embedded {@link ExerciseForExerciseGroupDTO} summaries and omit the exam. The
 * import response never serialized the exam back-reference; the list endpoint previously did, but no consumer reads it
 * (the web client does not call the list endpoint at all), so it is deliberately dropped for data economy.</li>
 * </ul>
 *
 * @param id          the id of the exercise group
 * @param title       the title of the exercise group
 * @param isMandatory whether the exercise group must be included when generating student exams
 * @param exam        the (slim) exam the group belongs to; only populated on single-group responses
 * @param exercises   the (slim) exercises of the group; only populated on list / import responses
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGroupDTO(long id, @Nullable String title, @Nullable Boolean isMandatory, @Nullable ExamForExerciseGroupDTO exam,
        @Nullable List<ExerciseForExerciseGroupDTO> exercises) {

    /**
     * Slim exam projection embedded in a single-group response. Carries only the fields the exam-exercise editors read
     * off {@code exerciseGroup.exam} (the exam mode, the example-solution publication date the programming-exercise
     * editor uses to gate the "release tests with example solution" checkbox, and the nested course used to rebuild
     * request references).
     *
     * @param id                             the id of the exam
     * @param examMode                       the mode of the exam
     * @param exampleSolutionPublicationDate the exam's example-solution publication date (gates the programming-exercise
     *                                           editor's "release tests with example solution" checkbox)
     * @param course                         the (slim) course of the exam
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExamForExerciseGroupDTO(long id, ExamMode examMode, @Nullable ZonedDateTime exampleSolutionPublicationDate, @Nullable CourseForExerciseGroupDTO course) {

        /**
         * Builds the slim exam projection from an exam entity.
         *
         * @param exam the exam (with its eager course loaded)
         * @return the slim exam DTO
         */
        public static ExamForExerciseGroupDTO of(Exam exam) {
            CourseForExerciseGroupDTO courseDTO = exam.getCourse() == null ? null : CourseForExerciseGroupDTO.of(exam.getCourse());
            return new ExamForExerciseGroupDTO(exam.getId(), exam.getExamMode(), exam.getExampleSolutionPublicationDate(), courseDTO);
        }
    }

    /**
     * Slim course projection embedded in {@link ExamForExerciseGroupDTO}. Carries only the id and the default
     * programming language, which the programming-exercise editor reads to preselect the language for a new exam
     * programming exercise.
     *
     * @param id                         the id of the course
     * @param defaultProgrammingLanguage the course's default programming language
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseForExerciseGroupDTO(long id, @Nullable ProgrammingLanguage defaultProgrammingLanguage) {

        /**
         * Builds the slim course projection from a course entity.
         *
         * @param course the course
         * @return the slim course DTO
         */
        public static CourseForExerciseGroupDTO of(Course course) {
            return new CourseForExerciseGroupDTO(course.getId(), course.getDefaultProgrammingLanguage());
        }
    }

    /**
     * Builds a single-group response DTO: the nested exam is populated (when present) and the exercises list is omitted.
     * Used by the create, update and get-by-id endpoints, none of which serialized the (lazy) exercises today.
     *
     * @param exerciseGroup the exercise group (with its eager exam / course loaded)
     * @return the DTO with the nested exam and no exercises
     */
    public static ExerciseGroupDTO of(ExerciseGroup exerciseGroup) {
        ExamForExerciseGroupDTO examDTO = exerciseGroup.getExam() == null ? null : ExamForExerciseGroupDTO.of(exerciseGroup.getExam());
        return new ExerciseGroupDTO(exerciseGroup.getId(), exerciseGroup.getTitle(), exerciseGroup.getIsMandatory(), examDTO, null);
    }

    /**
     * Builds a list / import response DTO: the embedded exercise summaries are populated (when the collection was
     * hydrated) and the exam is omitted. The exercises are mapped via {@link java.util.stream.Stream#toList()} only when
     * initialized, never exposing a live Hibernate collection, matching today's wire where an uninitialized collection
     * did not serialize.
     *
     * @param exerciseGroup the exercise group (with its exercises loaded)
     * @return the DTO with the embedded exercise summaries and no exam
     */
    public static ExerciseGroupDTO ofWithExercises(ExerciseGroup exerciseGroup) {
        List<ExerciseForExerciseGroupDTO> exerciseDTOs = null;
        var exercises = exerciseGroup.getExercises();
        if (Hibernate.isInitialized(exercises) && exercises != null && !exercises.isEmpty()) {
            exerciseDTOs = exercises.stream().map(ExerciseForExerciseGroupDTO::of).toList();
        }
        return new ExerciseGroupDTO(exerciseGroup.getId(), exerciseGroup.getTitle(), exerciseGroup.getIsMandatory(), null, exerciseDTOs);
    }
}
