package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.exam.domain.Exam;

/** Settings shared by exam creation, import and update; the exam kind stays immutable on update. */
public interface ExamSettingsDTO {

    String title();

    boolean examWithAttendanceCheck();

    ZonedDateTime visibleDate();

    ZonedDateTime startDate();

    ZonedDateTime endDate();

    ZonedDateTime publishResultsDate();

    ZonedDateTime examStudentReviewStart();

    ZonedDateTime examStudentReviewEnd();

    Integer gracePeriod();

    int workingTime();

    String startText();

    String endText();

    String confirmationStartText();

    String confirmationEndText();

    Integer examMaxPoints();

    Boolean randomizeExerciseOrder();

    Integer numberOfExercisesInExam();

    Integer numberOfCorrectionRoundsInExam();

    String examiner();

    String moduleNumber();

    String courseName();

    ZonedDateTime exampleSolutionPublicationDate();

    ZonedDateTime examSummaryPublicationDate();

    String channelName();

    /**
     * Applies editable settings, retaining the existing grace period when it is absent in the request.
     *
     * @param exam exam to update
     */
    default void applySettingsTo(Exam exam) {
        exam.setTitle(title());
        exam.setExamWithAttendanceCheck(examWithAttendanceCheck());
        exam.setVisibleDate(visibleDate());
        exam.setStartDate(startDate());
        exam.setEndDate(endDate());
        exam.setPublishResultsDate(publishResultsDate());
        exam.setExamStudentReviewStart(examStudentReviewStart());
        exam.setExamStudentReviewEnd(examStudentReviewEnd());
        if (gracePeriod() != null) {
            exam.setGracePeriod(gracePeriod());
        }
        exam.setWorkingTime(workingTime());
        exam.setStartText(startText());
        exam.setEndText(endText());
        exam.setConfirmationStartText(confirmationStartText());
        exam.setConfirmationEndText(confirmationEndText());
        exam.setExamMaxPoints(examMaxPoints());
        exam.setRandomizeExerciseOrder(randomizeExerciseOrder());
        exam.setNumberOfExercisesInExam(numberOfExercisesInExam());
        exam.setNumberOfCorrectionRoundsInExam(numberOfCorrectionRoundsInExam());
        exam.setExaminer(examiner());
        exam.setModuleNumber(moduleNumber());
        exam.setCourseName(courseName());
        exam.setExampleSolutionPublicationDate(exampleSolutionPublicationDate());
        exam.setExamSummaryPublicationDate(examSummaryPublicationDate());
        exam.setChannelName(channelName());
    }
}
