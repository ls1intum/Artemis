package de.tum.cit.aet.artemis.modeling.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseRefDTO;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.util.ModelingDtoCollections;

/**
 * Thin read DTO for listing {@link ModelingExercise} instances (course/exercise list and the cross-course import search).
 * Omits participations and other heavy associations, but carries a light {@code course} reference (id + title) so the
 * client's {@code courseTitle} pipe can render the course column, and a nested {@code exerciseGroup} for exam exercises
 * so the import search table can show the exam-question marker (it checks {@code exercise.exerciseGroup}).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ModelingExerciseListItemDTO(Long id, String title, String shortName, String type, ExerciseType exerciseType, DiagramType diagramType, ZonedDateTime releaseDate,
        ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Double maxPoints, Double bonusPoints, IncludedInOverallScore includedInOverallScore,
        Boolean presentationScoreEnabled, Boolean teamMode, Set<String> categories, List<GradingCriterionDTO> gradingCriteria, Long courseId, CourseRefDTO course, Long examId,
        String examTitle, ModelingExerciseExamGroupDTO exerciseGroup) implements Serializable {

    /**
     * Creates a {@link ModelingExerciseListItemDTO} from the given {@link ModelingExercise}.
     *
     * @param exercise the modeling exercise to convert (may be {@code null})
     * @return the corresponding DTO, or {@code null} if the input was {@code null}
     */
    public static ModelingExerciseListItemDTO of(ModelingExercise exercise) {
        if (exercise == null) {
            return null;
        }

        Long courseId = null;
        Long examId = null;
        String examTitle = null;
        // The cross-course import search table marks exam exercises by the presence of exercise.exerciseGroup; carry a
        // nested reference for exam exercises so that marker keeps working after the entity payload was replaced.
        ModelingExerciseExamGroupDTO exerciseGroup = null;

        if (exercise.isExamExercise()) {
            exerciseGroup = ModelingExerciseExamGroupDTO.of(exercise.getExerciseGroup());
            Exam exam = exercise.getExam();
            if (exam != null) {
                examId = exam.getId();
                examTitle = exam.getTitle();
            }
        }
        else {
            Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
            if (course != null) {
                courseId = course.getId();
            }
        }

        var gradingCriterionDTOs = ModelingDtoCollections.listFromInitializedSet(exercise.getGradingCriteria(), GradingCriterionDTO::of);

        // Light course reference (id + title) so the client courseTitle pipe can render the course column in the list and
        // the cross-course import search; resolves the course for both course and exam exercises.
        CourseRefDTO course = CourseRefDTO.from(exercise.getCourseViaExerciseGroupOrCourseMember());

        // categories is a LAZY @ElementCollection; copy it (guarded) so the DTO never holds the live Hibernate persistent
        // set (a DTO toString via LoggingAspect would otherwise trigger a LazyInitializationException on Exercise.categories).
        Set<String> categories = ModelingDtoCollections.copyInitializedSet(exercise.getCategories());

        return new ModelingExerciseListItemDTO(exercise.getId(), exercise.getTitle(), exercise.getShortName(), exercise.getType(), exercise.getExerciseType(),
                exercise.getDiagramType(), exercise.getReleaseDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getMaxPoints(), exercise.getBonusPoints(),
                exercise.getIncludedInOverallScore(), exercise.getPresentationScoreEnabled(), exercise.getMode() == ExerciseMode.TEAM, categories, gradingCriterionDTOs, courseId,
                course, examId, examTitle, exerciseGroup);
    }
}
