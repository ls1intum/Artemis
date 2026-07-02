package de.tum.cit.aet.artemis.plagiarism.dto;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismCaseExerciseDTO(Long id, String title, @Nullable String shortName, ExerciseType type, @Nullable ZonedDateTime dueDate, @Nullable Long courseId,
        @Nullable String courseTitle, @Nullable Long examId, @Nullable String examTitle, @Nullable Integer continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod) {

    /**
     * JPQL constructor that accepts the raw entity class produced by Hibernate's {@code TYPE(...)} function.
     *
     * @param id                                                             the exercise id
     * @param title                                                          the exercise title
     * @param shortName                                                      the exercise short name
     * @param type                                                           the concrete exercise entity class
     * @param dueDate                                                        the exercise due date
     * @param courseId                                                       the course id
     * @param courseTitle                                                    the course title
     * @param examId                                                         the exam id
     * @param examTitle                                                      the exam title
     * @param continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod the response period configured for continuous plagiarism control cases
     */
    public PlagiarismCaseExerciseDTO(Long id, String title, @Nullable String shortName, Class<? extends Exercise> type, @Nullable ZonedDateTime dueDate, @Nullable Long courseId,
            @Nullable String courseTitle, @Nullable Long examId, @Nullable String examTitle, @Nullable Integer continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod) {
        this(id, title, shortName, ExerciseType.getExerciseTypeFromClass(type), dueDate, courseId, courseTitle, examId, examTitle,
                continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod);
    }

    /**
     * Maps an exercise entity to the plagiarism case exercise DTO.
     *
     * @param exercise the exercise entity
     * @return the DTO representation
     */
    public static @Nullable PlagiarismCaseExerciseDTO fromExercise(@Nullable Exercise exercise) {
        if (exercise == null) {
            return null;
        }

        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        Exam exam = exercise.getExam();
        Integer studentResponsePeriod = Optional.ofNullable(exercise.getPlagiarismDetectionConfig())
                .map(PlagiarismDetectionConfig::getContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod).orElse(null);

        return new PlagiarismCaseExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getShortName(), exercise.getExerciseType(), exercise.getDueDate(),
                course != null ? course.getId() : null, course != null ? course.getTitle() : null, exam != null ? exam.getId() : null, exam != null ? exam.getTitle() : null,
                studentResponsePeriod);
    }
}
