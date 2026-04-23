package de.tum.cit.aet.artemis.globalsearch.dto.searchableitem;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

/**
 * Snapshot of the data needed to upsert an exercise into the unified {@code SearchableItems}
 * Weaviate collection.
 * <p>
 * This record is extracted before the async boundary to avoid {@code LazyInitializationException}
 * when the async Weaviate write runs outside the original Hibernate session. All fields are
 * primitives, strings, enums, or other immutable types safe to pass across thread boundaries.
 */
public record ExerciseSearchableItemDTO(Long exerciseId, Long courseId, String exerciseTitle, String exerciseType, Double maxPoints, String shortName, String problemStatement,
        ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate, String difficulty, boolean isExamExercise, Long examId, Boolean isTestExam,
        ZonedDateTime examVisibleDate, ZonedDateTime examStartDate, ZonedDateTime examEndDate, String programmingLanguage, String projectType, String diagramType, String quizMode,
        Integer quizDuration, String filePattern) {

    /**
     * Extracts all required data from an {@link Exercise} entity. Must be called while the Hibernate
     * session is still active so that lazily-loaded relationships (course, exam, programming specifics)
     * are materialized before the DTO crosses the async boundary.
     *
     * @param exercise the exercise entity (must have course and exam relationships loaded if applicable)
     * @return the extracted data safe to use in an async context
     * @throws org.hibernate.LazyInitializationException if required relationships are not loaded
     */
    public static ExerciseSearchableItemDTO fromExercise(Exercise exercise) {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        Exam exam = exercise.isExamExercise() ? exercise.getExam() : null;
        return buildDto(exercise, course, exam);
    }

    /**
     * Same as {@link #fromExercise(Exercise)}, but uses the supplied {@link Exam} instance for denormalized
     * exam dates. Use this variant when the caller has a newer view of the exam (e.g. immediately after
     * updating exam dates) than the exercise's stale {@code exam} reference.
     *
     * @param exercise the exercise entity (must have course relationship loaded)
     * @param exam     the exam with the authoritative dates (may be {@code null} for non-exam exercises)
     * @return the extracted data safe to use in an async context
     * @throws org.hibernate.LazyInitializationException if required relationships are not loaded
     */
    public static ExerciseSearchableItemDTO fromExerciseWithExam(Exercise exercise, Exam exam) {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        return buildDto(exercise, course, exam);
    }

    private static ExerciseSearchableItemDTO buildDto(Exercise exercise, Course course, Exam exam) {
        return new ExerciseSearchableItemDTO(exercise.getId(), course.getId(), exercise.getTitle(), exercise.getExerciseType().name(), exercise.getMaxPoints(),
                exercise.getShortName(), exercise.getProblemStatement(), exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate(),
                exercise.getDifficulty() != null ? exercise.getDifficulty().name() : null, exercise.isExamExercise(), exam != null ? exam.getId() : null,
                exam != null ? exam.isTestExam() : null, exam != null ? exam.getVisibleDate() : null, exam != null ? exam.getStartDate() : null,
                exam != null ? exam.getEndDate() : null,
                exercise instanceof ProgrammingExercise pe && pe.getProgrammingLanguage() != null ? pe.getProgrammingLanguage().name() : null,
                exercise instanceof ProgrammingExercise pe && pe.getProjectType() != null ? pe.getProjectType().name() : null,
                exercise instanceof ModelingExercise me && me.getDiagramType() != null ? me.getDiagramType().name() : null,
                exercise instanceof QuizExercise qe && qe.getQuizMode() != null ? qe.getQuizMode().name() : null, exercise instanceof QuizExercise qe ? qe.getDuration() : null,
                exercise instanceof FileUploadExercise fue ? fue.getFilePattern() : null);
    }

    /**
     * Produces the Weaviate property map for this exercise row. Absent/null fields are omitted so the
     * sparse inverted index does not record them.
     *
     * @return the property map keyed by {@link SearchableEntitySchema.Properties}
     */
    public Map<String, Object> toPropertyMap() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.TypeValues.EXERCISE);
        properties.put(SearchableEntitySchema.Properties.ENTITY_ID, exerciseId);
        properties.put(SearchableEntitySchema.Properties.COURSE_ID, courseId);
        properties.put(SearchableEntitySchema.Properties.TITLE, exerciseTitle);
        properties.put(SearchableEntitySchema.Properties.EXERCISE_TYPE, exerciseType);
        properties.put(SearchableEntitySchema.Properties.MAX_POINTS, maxPoints);
        properties.put(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE, isExamExercise);

        putIfNotNull(properties, SearchableEntitySchema.Properties.SHORT_NAME, shortName);
        putIfNotNull(properties, SearchableEntitySchema.Properties.DESCRIPTION, problemStatement);
        putIfNotNull(properties, SearchableEntitySchema.Properties.RELEASE_DATE, formatDate(releaseDate));
        putIfNotNull(properties, SearchableEntitySchema.Properties.START_DATE, formatDate(startDate));
        putIfNotNull(properties, SearchableEntitySchema.Properties.DUE_DATE, formatDate(dueDate));
        putIfNotNull(properties, SearchableEntitySchema.Properties.DIFFICULTY, difficulty);

        if (isExamExercise && examId != null) {
            properties.put(SearchableEntitySchema.Properties.EXAM_ID, examId);
            putIfNotNull(properties, SearchableEntitySchema.Properties.TEST_EXAM, isTestExam);
            putIfNotNull(properties, SearchableEntitySchema.Properties.EXAM_VISIBLE_DATE, formatDate(examVisibleDate));
            putIfNotNull(properties, SearchableEntitySchema.Properties.EXAM_START_DATE, formatDate(examStartDate));
            putIfNotNull(properties, SearchableEntitySchema.Properties.EXAM_END_DATE, formatDate(examEndDate));
        }

        putIfNotNull(properties, SearchableEntitySchema.Properties.PROGRAMMING_LANGUAGE, programmingLanguage);
        putIfNotNull(properties, SearchableEntitySchema.Properties.PROJECT_TYPE, projectType);
        putIfNotNull(properties, SearchableEntitySchema.Properties.DIAGRAM_TYPE, diagramType);
        putIfNotNull(properties, SearchableEntitySchema.Properties.QUIZ_MODE, quizMode);
        putIfNotNull(properties, SearchableEntitySchema.Properties.QUIZ_DURATION, quizDuration);
        putIfNotNull(properties, SearchableEntitySchema.Properties.FILE_PATTERN, filePattern);

        return properties;
    }

    private static void putIfNotNull(Map<String, Object> properties, String key, Object value) {
        if (value != null) {
            properties.put(key, value);
        }
    }

    private static String formatDate(ZonedDateTime dateTime) {
        return dateTime != null ? dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null;
    }
}
