package de.tum.cit.aet.artemis.exam.dto.conduction;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.dto.CourseForStudentExamDTO;

/**
 * Projection of an {@link Exam} as nested inside the conduction / summary student-exam payload.
 * <p>
 * Carries the exam-level fields the exam-taking client reads to render the cover page, the working-time / date window
 * and the confirmation texts. The nested {@code course} is reduced to {@link CourseForStudentExamDTO} (course-access
 * group names); the conduction client rebuilds the full course from the route, so the slim course is sufficient there.
 * <p>
 * {@code moduleNumber}, {@code courseName} and {@code examiner} are the configured exam metadata:
 * {@code ExamStartInformationComponent} builds an information box per field on the exam cover and
 * {@code ExamGeneralInformationComponent} lists them on the summary. Both render each one only when it is present, so
 * dropping them here removes them from the student's view on a 200 with no error.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamForConductionDTO(long id, String title, String moduleNumber, String courseName, String examiner, boolean testExam, boolean examWithAttendanceCheck,
        ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate, Integer gracePeriod, int workingTime, String startText, String endText,
        String confirmationStartText, String confirmationEndText, int examMaxPoints, Boolean randomizeExerciseOrder, Integer numberOfExercisesInExam,
        Integer numberOfCorrectionRoundsInExam, CourseForStudentExamDTO course) {

    /**
     * Converts an Exam into an ExamForConductionDTO.
     *
     * @param exam the exam to convert
     * @return the converted DTO, or null if the exam is null
     */
    public static ExamForConductionDTO of(Exam exam) {
        if (exam == null) {
            return null;
        }
        return new ExamForConductionDTO(exam.getId(), exam.getTitle(), exam.getModuleNumber(), exam.getCourseName(), exam.getExaminer(), !exam.getExamMode().isReal(),
                exam.isExamWithAttendanceCheck(), exam.getVisibleDate(), exam.getStartDate(), exam.getEndDate(), exam.getGracePeriod(), exam.getWorkingTime(), exam.getStartText(),
                exam.getEndText(), exam.getConfirmationStartText(), exam.getConfirmationEndText(), exam.getExamMaxPoints(), exam.getRandomizeExerciseOrder(),
                exam.getNumberOfExercisesInExam(), exam.getNumberOfCorrectionRoundsInExam(), CourseForStudentExamDTO.of(exam.getCourse()));
    }
}
