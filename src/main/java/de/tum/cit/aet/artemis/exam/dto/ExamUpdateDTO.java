package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.Exam;

/**
 * DTO for creating and updating exams.
 * Uses DTOs instead of entity classes to avoid Hibernate detached entity issues.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamUpdateDTO(@Nullable Long id, @NotNull String title, boolean testExam, boolean examWithAttendanceCheck, @NotNull ZonedDateTime visibleDate,
        @NotNull ZonedDateTime startDate, @NotNull ZonedDateTime endDate, @Nullable ZonedDateTime publishResultsDate, @Nullable ZonedDateTime examStudentReviewStart,
        @Nullable ZonedDateTime examStudentReviewEnd, @Nullable Integer gracePeriod, int workingTime, @Nullable String startText, @Nullable String endText,
        @Nullable String confirmationStartText, @Nullable String confirmationEndText, @Nullable Integer examMaxPoints, @Nullable Boolean randomizeExerciseOrder,
        @Nullable Integer numberOfExercisesInExam, @Nullable Integer numberOfCorrectionRoundsInExam, @Nullable String examiner, @Nullable String moduleNumber,
        @Nullable String courseName, @Nullable ZonedDateTime exampleSolutionPublicationDate, @Nullable String channelName) {

    /**
     * Creates an ExamUpdateDTO from the given Exam domain object.
     *
     * @param exam the exam to convert
     * @return the corresponding DTO
     */
    public static ExamUpdateDTO of(Exam exam) {
        return new ExamUpdateDTO(exam.getId(), exam.getTitle(), exam.isTestExam(), exam.isExamWithAttendanceCheck(), exam.getVisibleDate(), exam.getStartDate(), exam.getEndDate(),
                exam.getPublishResultsDate(), exam.getExamStudentReviewStart(), exam.getExamStudentReviewEnd(), exam.getGracePeriod(), exam.getWorkingTime(), exam.getStartText(),
                exam.getEndText(), exam.getConfirmationStartText(), exam.getConfirmationEndText(), exam.getExamMaxPoints(), exam.getRandomizeExerciseOrder(),
                exam.getNumberOfExercisesInExam(), exam.getNumberOfCorrectionRoundsInExam(), exam.getExaminer(), exam.getModuleNumber(), exam.getCourseName(),
                exam.getExampleSolutionPublicationDate(), exam.getChannelName());
    }

    /**
     * Creates a new Exam entity from this DTO.
     * Used for create operations.
     *
     * @return a new Exam entity
     */
    public Exam toEntity() {
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
        return exam;
    }

    /**
     * Applies the DTO values to an existing Exam entity.
     * This updates the managed entity with values from the DTO.
     *
     * @param exam the existing exam to update
     */
    public void applyTo(Exam exam) {
        exam.setTitle(title);
        // testExam cannot be changed after creation, so we don't set it here
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
    }
}
