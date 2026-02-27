package de.tum.cit.aet.artemis.globalsearch.dto;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

/**
 * DTO holding exercise data needed for Weaviate synchronization.
 * This DTO is extracted before the async boundary to avoid LazyInitializationException
 * when async methods run outside the original Hibernate session.
 * All fields are primitives, Strings, enums, or immutable types safe to pass across thread boundaries.
 */
public record ExerciseWeaviateDTO(Long exerciseId, Long courseId, String courseTitle, String exerciseTitle, String exerciseType, Double maxPoints, String shortName,
        String problemStatement, ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate, String difficulty, boolean isExamExercise, Long examId,
        Boolean isTestExam, ZonedDateTime examVisibleDate, ZonedDateTime examStartDate, ZonedDateTime examEndDate, String programmingLanguage, String projectType,
        String diagramType, String quizMode, Integer quizDuration, String filePattern) {

    /**
     * Extracts all required data from an Exercise entity and its relationships.
     * MUST be called while the Hibernate session is still active.
     * Will throw LazyInitializationException if required relationships are not eagerly loaded.
     *
     * @param exercise the exercise entity (must have course and exam relationships loaded if applicable)
     * @return the extracted data safe to use in async context
     * @throws org.hibernate.LazyInitializationException if required relationships are not loaded
     */
    public static ExerciseWeaviateDTO fromExercise(Exercise exercise) {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        Exam exam = exercise.isExamExercise() ? exercise.getExam() : null;

        return new ExerciseWeaviateDTO(exercise.getId(), course.getId(), course.getTitle(), exercise.getTitle(), exercise.getExerciseType().name(),
                exercise.getMaxPoints() != null ? exercise.getMaxPoints() : 0.0, exercise.getShortName(), exercise.getProblemStatement(), exercise.getReleaseDate(),
                exercise.getStartDate(), exercise.getDueDate(), exercise.getDifficulty() != null ? exercise.getDifficulty().name() : null, exercise.isExamExercise(),
                exam != null ? exam.getId() : null, exam != null ? exam.isTestExam() : null, exam != null ? exam.getVisibleDate() : null, exam != null ? exam.getStartDate() : null,
                exam != null ? exam.getEndDate() : null,
                exercise instanceof ProgrammingExercise pe && pe.getProgrammingLanguage() != null ? pe.getProgrammingLanguage().name() : null,
                exercise instanceof ProgrammingExercise pe && pe.getProjectType() != null ? pe.getProjectType().name() : null,
                exercise instanceof ModelingExercise me && me.getDiagramType() != null ? me.getDiagramType().name() : null,
                exercise instanceof QuizExercise qe && qe.getQuizMode() != null ? qe.getQuizMode().name() : null, exercise instanceof QuizExercise qe ? qe.getDuration() : null,
                exercise instanceof FileUploadExercise fue ? fue.getFilePattern() : null);
    }
}
