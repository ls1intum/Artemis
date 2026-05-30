package de.tum.cit.aet.artemis.quiz.dto.exercise;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

/**
 * DTO returned by the paged quiz exercise search backing the exercise import table.
 * <p>
 * Structure-preserving on the wire: exposes {@code id}, {@code title}, the polymorphic {@code type} discriminator
 * ("quiz", matching {@code Exercise}'s {@code @JsonTypeInfo}), and a nested course-title holder reachable via either
 * {@code course.title} (course exercises) or {@code exerciseGroup.exam.course.title} (exam exercises), matching what
 * the shared exercise import renderer consumes. It deliberately avoids any {@code @Entity} references and only carries
 * {@link Long}/{@link String} plus minimal nested DTO record holders.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseForSearchDTO(String type, Long id, String title, QuizSearchCourseDTO course, QuizSearchExerciseGroupDTO exerciseGroup) {

    /**
     * Polymorphic type discriminator matching {@code @JsonSubTypes} name for {@link QuizExercise} on {@code Exercise}.
     */
    private static final String TYPE = "quiz";

    /**
     * Maps a {@link QuizExercise} to a structure-preserving search DTO that exposes only the id, title and the
     * resolvable course title.
     *
     * @param quizExercise the quiz exercise to map
     * @return the corresponding search DTO
     */
    public static QuizExerciseForSearchDTO of(QuizExercise quizExercise) {
        if (quizExercise.isExamExercise()) {
            Exam exam = quizExercise.getExerciseGroup() != null ? quizExercise.getExerciseGroup().getExam() : null;
            Course course = exam != null ? exam.getCourse() : null;
            QuizSearchCourseDTO courseDTO = course != null ? new QuizSearchCourseDTO(course.getTitle()) : null;
            return new QuizExerciseForSearchDTO(TYPE, quizExercise.getId(), quizExercise.getTitle(), null,
                    new QuizSearchExerciseGroupDTO(new QuizSearchExamDTO(exam != null ? exam.getTitle() : null, courseDTO)));
        }
        Course course = quizExercise.getCourseViaExerciseGroupOrCourseMember();
        return new QuizExerciseForSearchDTO(TYPE, quizExercise.getId(), quizExercise.getTitle(), course != null ? new QuizSearchCourseDTO(course.getTitle()) : null, null);
    }
}
