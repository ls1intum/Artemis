package de.tum.cit.aet.artemis.exam.dto;

import java.time.ZonedDateTime;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.Exam;

/**
 * Rich exam projection nested inside {@link StudentExamForConductionDTO}, returned by the own-student-exam fetch
 * ({@code GET courses/{courseId}/exams/{examId}/own-student-exam},
 * {@link de.tum.cit.aet.artemis.exam.web.ExamResource#getOwnStudentExam}) that starts exam conduction.
 * <p>
 * This is deliberately richer than the management-side {@link ExamForStudentExamDTO} (id/title/testExam/workingTime/course):
 * the exam-conduction entry screen (exam-participation-cover + exam-start-information components) and the exam navbar read
 * the full cover metadata off this exam — the markdown start/end/confirmation texts, the visible/start/end dates, the grace
 * period and default working time, the attendance-check flag, and the exam-start information box fields
 * ({@code examMaxPoints}, {@code moduleNumber}, {@code courseName}, {@code examiner}, {@code numberOfExercisesInExam}). Only
 * {@code course.id} is read (the attendance-check API links and the local-storage key), but the shared student-facing
 * {@link CourseForStudentExamDTO} leaf is reused for consistency with the ported StudentExam DTO family.
 * <p>
 * A dedicated projection (rather than enriching {@link ExamForStudentExamDTO}) keeps the management/test-run endpoints that
 * nest the slim exam from newly serializing the exam cover texts — those consumers read only {@code exam.course}, so folding
 * the conduction fields into the shared slim projection would leak the markdown cover content onto the student-exams list.
 * <p>
 * {@code examSummaryPublicationDate} and {@code publishResultsDate} are required by the client-side summary gate
 * ({@code isExamSummaryPublished} in {@code exam.utils.ts}), which the exam-participation component evaluates against the exam
 * it takes from this projection after a hand-in. That helper treats a missing {@code examSummaryPublicationDate} as
 * "published", so omitting either field here silently opens the gate and shows the submission overview even while it is
 * supposed to be withheld.
 *
 * @param id                         the id of the exam
 * @param title                      the title shown on the exam cover
 * @param testExam                   whether this is a test exam (drives the test-exam conduction branch)
 * @param examWithAttendanceCheck    whether an attendance check is enabled (the cover renders the attendance confirmation)
 * @param visibleDate                the date the exam becomes visible
 * @param startDate                  the exam start date (individual end date + waiting-for-start are computed from it)
 * @param endDate                    the exam end date
 * @param gracePeriod                the grace period in seconds
 * @param workingTime                the regular working time in seconds
 * @param startText                  the markdown start text
 * @param endText                    the markdown end text
 * @param confirmationStartText      the markdown confirmation start text (start consent)
 * @param confirmationEndText        the markdown confirmation end text (end consent)
 * @param examMaxPoints              the maximum achievable points (exam-start information box)
 * @param numberOfExercisesInExam    the number of exercises drawn per student exam (exam-start information box)
 * @param examiner                   the examiner (exam-start information box)
 * @param moduleNumber               the module number (exam-start information box)
 * @param courseName                 the course name shown on the exam cover (exam-start information box)
 * @param examSummaryPublicationDate the date the submission overview becomes visible, or {@code null} when it is not delayed
 * @param publishResultsDate         the date the results are published (the summary gate also opens once results are out)
 * @param course                     the slim student-facing course projection (only {@code id} is read)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamForConductionDTO(long id, @Nullable String title, boolean testExam, boolean examWithAttendanceCheck, @Nullable ZonedDateTime visibleDate,
        @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime endDate, @Nullable Integer gracePeriod, int workingTime, @Nullable String startText, @Nullable String endText,
        @Nullable String confirmationStartText, @Nullable String confirmationEndText, int examMaxPoints, @Nullable Integer numberOfExercisesInExam, @Nullable String examiner,
        @Nullable String moduleNumber, @Nullable String courseName, @Nullable ZonedDateTime examSummaryPublicationDate, @Nullable ZonedDateTime publishResultsDate,
        @Nullable CourseForStudentExamDTO course) {

    /**
     * Converts an Exam into an ExamForConductionDTO.
     *
     * @param exam the exam to convert (with its eager course loaded)
     * @return the converted DTO, or {@code null} if the exam is {@code null}
     */
    @Nullable
    public static ExamForConductionDTO of(@Nullable Exam exam) {
        if (exam == null) {
            return null;
        }
        // The exam's course is @ManyToOne EAGER, so it is loaded on the conduction fetch; guard defensively so a
        // detached lazy proxy never forces a load outside the transaction.
        var course = exam.getCourse();
        CourseForStudentExamDTO courseDTO = Hibernate.isInitialized(course) ? CourseForStudentExamDTO.of(course) : null;
        return new ExamForConductionDTO(exam.getId(), exam.getTitle(), exam.isTestExam(), exam.isExamWithAttendanceCheck(), exam.getVisibleDate(), exam.getStartDate(),
                exam.getEndDate(), exam.getGracePeriod(), exam.getWorkingTime(), exam.getStartText(), exam.getEndText(), exam.getConfirmationStartText(),
                exam.getConfirmationEndText(), exam.getExamMaxPoints(), exam.getNumberOfExercisesInExam(), exam.getExaminer(), exam.getModuleNumber(), exam.getCourseName(),
                exam.getExamSummaryPublicationDate(), exam.getPublishResultsDate(), courseDTO);
    }
}
