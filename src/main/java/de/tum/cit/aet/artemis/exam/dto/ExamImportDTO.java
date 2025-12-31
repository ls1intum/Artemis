package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * DTO for importing exams with exercise groups and exercises.
 * Uses DTOs instead of entity classes to avoid Hibernate detached entity issues.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamImportDTO(@NotNull String title, boolean testExam, boolean examWithAttendanceCheck, @NotNull ZonedDateTime visibleDate, @NotNull ZonedDateTime startDate,
        @NotNull ZonedDateTime endDate, @Nullable ZonedDateTime publishResultsDate, @Nullable ZonedDateTime examStudentReviewStart, @Nullable ZonedDateTime examStudentReviewEnd,
        @Nullable Integer gracePeriod, int workingTime, @Nullable String startText, @Nullable String endText, @Nullable String confirmationStartText,
        @Nullable String confirmationEndText, @Nullable Integer examMaxPoints, @Nullable Boolean randomizeExerciseOrder, @Nullable Integer numberOfExercisesInExam,
        @Nullable Integer numberOfCorrectionRoundsInExam, @Nullable String examiner, @Nullable String moduleNumber, @Nullable String courseName,
        @Nullable ZonedDateTime exampleSolutionPublicationDate, @Nullable String channelName, @NotNull Long courseId, @Nullable List<ExerciseGroupImportDTO> exerciseGroups) {

    /**
     * Creates an ExamImportDTO from an existing Exam entity.
     * Useful for tests and converting existing exams to the import format.
     *
     * @param exam     the exam to convert
     * @param courseId the target course ID
     * @return the DTO representation
     */
    public static ExamImportDTO of(Exam exam, Long courseId) {
        List<ExerciseGroupImportDTO> exerciseGroupDTOs = null;
        if (exam.getExerciseGroups() != null && !exam.getExerciseGroups().isEmpty()) {
            exerciseGroupDTOs = exam.getExerciseGroups().stream().map(ExerciseGroupImportDTO::of).toList();
        }

        return new ExamImportDTO(exam.getTitle(), exam.isTestExam(), exam.isExamWithAttendanceCheck(), exam.getVisibleDate(), exam.getStartDate(), exam.getEndDate(),
                exam.getPublishResultsDate(), exam.getExamStudentReviewStart(), exam.getExamStudentReviewEnd(), exam.getGracePeriod(), exam.getWorkingTime(), exam.getStartText(),
                exam.getEndText(), exam.getConfirmationStartText(), exam.getConfirmationEndText(), exam.getExamMaxPoints(), exam.getRandomizeExerciseOrder(),
                exam.getNumberOfExercisesInExam(), exam.getNumberOfCorrectionRoundsInExam(), exam.getExaminer(), exam.getModuleNumber(), exam.getCourseName(),
                exam.getExampleSolutionPublicationDate(), exam.getChannelName(), courseId, exerciseGroupDTOs);
    }

    /**
     * Creates a new Exam entity from this DTO.
     *
     * @param course the course to associate with the exam
     * @return a new Exam entity with exercise groups
     */
    public Exam toEntity(Course course) {
        Exam exam = new Exam();
        exam.setTitle(title);
        exam.setTestExam(testExam);
        exam.setExamWithAttendanceCheck(examWithAttendanceCheck);
        exam.setVisibleDate(visibleDate);
        exam.setStartDate(startDate);
        exam.setEndDate(endDate);
        exam.setPublishResultsDate(publishResultsDate);
        exam.setExamStudentReviewStart(examStudentReviewStart);
        exam.setExamStudentReviewEnd(examStudentReviewEnd);
        if (gracePeriod != null) {
            exam.setGracePeriod(gracePeriod);
        }
        exam.setWorkingTime(workingTime);
        exam.setStartText(startText);
        exam.setEndText(endText);
        exam.setConfirmationStartText(confirmationStartText);
        exam.setConfirmationEndText(confirmationEndText);
        exam.setExamMaxPoints(examMaxPoints);
        exam.setRandomizeExerciseOrder(randomizeExerciseOrder);
        exam.setNumberOfExercisesInExam(numberOfExercisesInExam);
        exam.setNumberOfCorrectionRoundsInExam(numberOfCorrectionRoundsInExam);
        exam.setExaminer(examiner);
        exam.setModuleNumber(moduleNumber);
        exam.setCourseName(courseName);
        exam.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        exam.setChannelName(channelName);
        exam.setCourse(course);

        // Add exercise groups with exercises
        if (exerciseGroups != null) {
            for (ExerciseGroupImportDTO groupDTO : exerciseGroups) {
                ExerciseGroup group = groupDTO.toEntity();
                exam.addExerciseGroup(group);
            }
        }

        return exam;
    }

    /**
     * Gets the list of exercise groups or an empty list if null.
     *
     * @return the exercise groups or empty list
     */
    public List<ExerciseGroupImportDTO> exerciseGroupsOrEmpty() {
        return exerciseGroups != null ? exerciseGroups : new ArrayList<>();
    }

    /**
     * DTO for importing exercise groups.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExerciseGroupImportDTO(@Nullable String title, boolean isMandatory, @Nullable List<ExerciseImportDTO> exercises) {

        /**
         * Creates an ExerciseGroupImportDTO from an existing ExerciseGroup entity.
         *
         * @param group the exercise group to convert
         * @return the DTO representation
         */
        public static ExerciseGroupImportDTO of(ExerciseGroup group) {
            List<ExerciseImportDTO> exerciseDTOs = null;
            if (group.getExercises() != null && !group.getExercises().isEmpty()) {
                exerciseDTOs = group.getExercises().stream().map(ExerciseImportDTO::of).toList();
            }
            return new ExerciseGroupImportDTO(group.getTitle(), group.getIsMandatory(), exerciseDTOs);
        }

        /**
         * Creates a new ExerciseGroup entity from this DTO.
         *
         * @return a new ExerciseGroup entity
         */
        public ExerciseGroup toEntity() {
            ExerciseGroup group = new ExerciseGroup();
            group.setTitle(title);
            group.setIsMandatory(isMandatory);

            // Add exercises
            if (exercises != null) {
                for (ExerciseImportDTO exerciseDTO : exercises) {
                    Exercise exercise = exerciseDTO.toEntity();
                    if (exercise != null) {
                        group.addExercise(exercise);
                    }
                }
            }

            return group;
        }

        /**
         * Gets the list of exercises or an empty list if null.
         *
         * @return the exercises or empty list
         */
        public List<ExerciseImportDTO> exercisesOrEmpty() {
            return exercises != null ? exercises : new ArrayList<>();
        }
    }

    /**
     * DTO for importing exercises. Contains the source exercise ID and optional overrides.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExerciseImportDTO(@NotNull Long id, @NotNull ExerciseType exerciseType, @Nullable String title, @Nullable String shortName, @Nullable Double maxPoints,
            @Nullable Double bonusPoints) {

        /**
         * Creates an ExerciseImportDTO from an existing Exercise entity.
         *
         * @param exercise the exercise to convert
         * @return the DTO representation
         */
        public static ExerciseImportDTO of(Exercise exercise) {
            return new ExerciseImportDTO(exercise.getId(), exercise.getExerciseType(), exercise.getTitle(), exercise.getShortName(), exercise.getMaxPoints(),
                    exercise.getBonusPoints());
        }

        /**
         * Creates a skeleton Exercise entity from this DTO.
         * The actual exercise import will use the ID to look up the source exercise.
         *
         * @return a new Exercise entity with basic properties set
         */
        public Exercise toEntity() {
            Exercise exercise = switch (exerciseType) {
                case MODELING -> new ModelingExercise();
                case TEXT -> new TextExercise();
                case PROGRAMMING -> new ProgrammingExercise();
                case FILE_UPLOAD -> new FileUploadExercise();
                case QUIZ -> new QuizExercise();
            };

            exercise.setId(id);
            if (title != null) {
                exercise.setTitle(title);
            }
            if (shortName != null) {
                exercise.setShortName(shortName);
            }
            if (maxPoints != null) {
                exercise.setMaxPoints(maxPoints);
            }
            if (bonusPoints != null) {
                exercise.setBonusPoints(bonusPoints);
            }

            return exercise;
        }
    }
}
