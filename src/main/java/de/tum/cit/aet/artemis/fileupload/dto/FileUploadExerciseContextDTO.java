package de.tum.cit.aet.artemis.fileupload.dto;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;

/**
 * DTO representing the exercise context for a file upload exercise.
 *
 * @param id                                     the ID of the exercise
 * @param title                                  the title of the exercise
 * @param problemStatement                       the problem statement description
 * @param gradingInstructions                    the grading instructions text
 * @param releaseDate                            the release date of the exercise
 * @param startDate                              the start date of the exercise
 * @param dueDate                                the due date of the exercise
 * @param assessmentDueDate                      the assessment due date of the exercise
 * @param maxPoints                              the maximum points achievable
 * @param bonusPoints                            the bonus points achievable
 * @param assessmentType                         the assessment type (e.g. MANUAL, SEMI_AUTOMATIC)
 * @param allowComplaintsForAutomaticAssessments whether complaints are allowed for automatic assessments
 * @param allowFeedbackRequests                  whether feedback requests are allowed
 * @param type                                   the type of the exercise (should be FILE_UPLOAD)
 * @param filePattern                            the file pattern allowed for upload
 * @param teamMode                               whether the exercise is a team exercise
 * @param isAtLeastTutor                         privilege flag for tutor access level
 * @param isAtLeastEditor                        privilege flag for editor access level
 * @param isAtLeastInstructor                    privilege flag for instructor access level
 * @param course                                 the course context of the exercise
 * @param exerciseGroup                          the exercise group context if the exercise is part of an exam
 * @param gradingCriteria                        the grading criteria defined for the exercise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadExerciseContextDTO(Long id, String title, String problemStatement, String gradingInstructions, ZonedDateTime releaseDate, ZonedDateTime startDate,
        ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Double maxPoints, Double bonusPoints, AssessmentType assessmentType, Boolean allowComplaintsForAutomaticAssessments,
        Boolean allowFeedbackRequests, ExerciseType type, String filePattern, Boolean teamMode, Boolean isAtLeastTutor, Boolean isAtLeastEditor, Boolean isAtLeastInstructor,
        FileUploadCourseContextDTO course, FileUploadExerciseGroupContextDTO exerciseGroup, Set<GradingCriterionDTO> gradingCriteria) {

    /**
     * Factory method to create a {@link FileUploadExerciseContextDTO} from a {@link FileUploadExercise} entity.
     *
     * @param exercise               the file upload exercise entity to map, can be null
     * @param includeGradingCriteria whether to include the grading criteria in the DTO
     * @return the mapped DTO, or null if the input was null
     */
    public static FileUploadExerciseContextDTO of(FileUploadExercise exercise, boolean includeGradingCriteria) {
        if (exercise == null) {
            return null;
        }

        FileUploadCourseContextDTO courseDTO = null;
        if (exercise.isCourseExercise()) {
            Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
            if (course != null && Hibernate.isInitialized(course)) {
                courseDTO = FileUploadCourseContextDTO.of(course);
            }
        }

        ExerciseGroup exerciseGroup = exercise.getExerciseGroup();
        FileUploadExerciseGroupContextDTO exerciseGroupDTO = exerciseGroup != null && Hibernate.isInitialized(exerciseGroup) ? FileUploadExerciseGroupContextDTO.of(exerciseGroup)
                : null;

        Set<GradingCriterionDTO> gradingCriteriaDTOs = null;
        if (includeGradingCriteria && exercise.getGradingCriteria() != null && Hibernate.isInitialized(exercise.getGradingCriteria())) {
            gradingCriteriaDTOs = exercise.getGradingCriteria().stream().map(GradingCriterionDTO::of).collect(Collectors.toSet());
        }

        return new FileUploadExerciseContextDTO(exercise.getId(), exercise.getTitle(), exercise.getProblemStatement(), exercise.getGradingInstructions(), exercise.getReleaseDate(),
                exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getAssessmentType(),
                exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getAllowFeedbackRequests(), exercise.getExerciseType(), exercise.getFilePattern(),
                exercise.isTeamMode(), null, null, null, courseDTO, exerciseGroupDTO, gradingCriteriaDTOs);
    }
}
