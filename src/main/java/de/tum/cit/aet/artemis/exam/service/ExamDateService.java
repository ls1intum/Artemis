package de.tum.cit.aet.artemis.exam.service;

import static de.tum.cit.aet.artemis.core.config.Constants.EXAM_START_WAIT_TIME_MINUTES;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamMode;
import de.tum.cit.aet.artemis.exam.dto.StudentExamWorkingPeriodDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exam.util.ExamDateUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

@Conditional(ExamEnabled.class)
@Lazy
@Service
public class ExamDateService {

    private final ExamRepository examRepository;

    private final StudentExamRepository studentExamRepository;

    public ExamDateService(ExamRepository examRepository, StudentExamRepository studentExamRepository) {
        this.examRepository = examRepository;
        this.studentExamRepository = studentExamRepository;
    }

    /**
     * Returns if the exam is over by checking if the latest individual exam end date plus grace period has passed.
     * See {@link ExamDateService#getLatestIndividualExamEndDate}
     * <p>
     *
     * @param examId the id of the exam
     * @return true if the exam is over and the students cannot submit anymore
     * @throws EntityNotFoundException if no exam with the given examId can be found
     */
    public boolean isExamWithGracePeriodOver(Long examId) {
        final var exam = examRepository.findByIdElseThrow(examId);
        return isExamWithGracePeriodOver(exam);
    }

    /**
     * Returns if the exam is over by checking if the latest individual exam end date plus grace period has passed.
     * See {@link ExamDateService#getLatestIndividualExamEndDate}
     * <p>
     *
     * @param exam the exam
     * @return true if the exam is over and the students cannot submit anymore
     * @throws EntityNotFoundException if no exam with the given examId can be found
     */
    public boolean isExamWithGracePeriodOver(Exam exam) {
        var now = ZonedDateTime.now();
        return getLatestIndividualExamEndDate(exam).plusSeconds(exam.getGracePeriod()).isBefore(now);
    }

    /**
     * Returns <code>true</code> if the exercise working period is over, which is the case when:
     * <ul>
     * <li>For real exams, if no student can hand in their exam anymore</li>
     * <lI>For tests exams, if the student has handed in their own student exam</lI>
     * </ul>
     *
     * @param exercise             the course or exam exercise
     * @param studentParticipation used to find the related student exams for test exams
     * @return <code>true</code> if the exercise is over and students cannot submit (graded) solutions anymore, <code>false</code> otherwise
     * @throws EntityNotFoundException the given exercise is an exam exercise and the exam cannot be found
     */
    public boolean isExerciseWorkingPeriodOver(Exercise exercise, StudentParticipation studentParticipation) {
        if (!exercise.isExamExercise()) {
            throw new IllegalArgumentException("This function should only be used for exam exercises");
        }
        Exam exam = exercise.getExam();
        if (!exam.getExamMode().isReal()) {
            return isIndividualExerciseWorkingPeriodOver(exam, studentParticipation);
        }
        return isExamWithGracePeriodOver(exam);
    }

    /**
     * Returns <code>true</code> if the exercise working period is over for a specific student participation.
     * This is the case as soon as the students hand in their results, or the individual due date is reached.
     *
     * @param exam                 the exam
     * @param studentParticipation used to find the related student exam
     * @return <code>true</code> if the working period is over, <code>false</code> otherwise
     */
    public boolean isIndividualExerciseWorkingPeriodOver(Exam exam, StudentParticipation studentParticipation) {
        return isIndividualExerciseWorkingPeriodOver(exam, studentParticipation.isTestRun(), studentParticipation.getParticipant().getId(), studentParticipation.getId());
    }

    /**
     * Whether the student's working period is over, for a caller that holds only the exam's id.
     * <p>
     * The git request path authorizes a push against a projection of the exercise, so it never loads the exam. Reading
     * the exam here, once and only for an exam exercise that actually reaches the write check, is cheaper than
     * carrying it through the whole authorization path - and keeps this date logic reading the entity it was written
     * against.
     *
     * @param examId               the exam the exercise belongs to
     * @param studentParticipation the participation to check
     * @return true if the working period is over
     */
    public boolean isIndividualExerciseWorkingPeriodOver(long examId, StudentParticipation studentParticipation) {
        return isIndividualExerciseWorkingPeriodOver(examRepository.findByIdElseThrow(examId), studentParticipation);
    }

    /**
     * Scalar form of {@link #isIndividualExerciseWorkingPeriodOver(Exam, StudentParticipation)} for callers holding a
     * projection of the participation rather than the entity.
     *
     * @param exam            the exam the exercise belongs to
     * @param testRun         whether the participation is an instructor test run
     * @param participantId   the id of the student the participation belongs to
     * @param participationId the id of the participation, named in the error when no student exam exists
     * @return true if the working period is over, false otherwise
     */
    public boolean isIndividualExerciseWorkingPeriodOver(Exam exam, boolean testRun, long participantId, long participationId) {
        if (testRun) {
            return false;
        }
        // Students can participate in a test exam multiple times, meaning there can be multiple student exams for a single exam.
        // For test exams, we aim to find the latest student exam.
        // For real exams, we aim to find the only existing student exam.
        // A projection: the caller already holds the exam, and reading the two values off the student exam entity pulled
        // its eager exam, that exam's course and the course configuration in behind them, on every exam submission.
        Optional<StudentExamWorkingPeriodDTO> workingPeriod = studentExamRepository.findNewestWorkingPeriodByExamIdAndUserId(exam.getId(), participantId);

        if (workingPeriod.isPresent()) {
            return workingPeriod.get().isWorkingPeriodOver(exam);
        }

        throw new IllegalStateException("No student exam found for student participation " + participationId);
    }

    /**
     * Returns the latest individual exam end date as determined by the working time of the student exams.
     * <p>
     * If no student exams are available, the exam end date is returned.
     *
     * @param examId the id of the exam
     * @return the latest end date or the exam end date if no student exams are found. May return <code>null</code>, if the exam has no start/end date.
     * @throws EntityNotFoundException if no exam with the given examId can be found
     */
    @NonNull
    public ZonedDateTime getLatestIndividualExamEndDate(Long examId) {
        final var exam = examRepository.findByIdElseThrow(examId);
        return getLatestIndividualExamEndDate(exam);
    }

    /**
     * Returns the latest individual exam end date as determined by the working time of the student exams.
     * <p>
     * If no student exams are available, the exam end date is returned.
     *
     * @param exam the exam
     * @return the latest end date or the exam end date if no student exams are found. May return <code>null</code>, if the exam has no start/end date.
     */
    @NonNull
    public ZonedDateTime getLatestIndividualExamEndDate(Exam exam) {
        var maxWorkingTime = studentExamRepository.findMaxWorkingTimeByExamId(exam.getId());
        return maxWorkingTime.map(timeInSeconds -> exam.getStartDate().plusSeconds(timeInSeconds)).orElse(exam.getEndDate());
    }

    /**
     * Returns the latest individual exam end date the exam will have once a pending duration change has been applied.
     * <p>
     * Changing the exam duration also rescales existing individual time extensions (see
     * {@link ExamService#updateStudentExamsAndRescheduleExercises}), which happens only after an update has been
     * validated. Callers that must validate the resulting state therefore need the projected end date, not the stored
     * one.
     *
     * @param exam                 the exam, already carrying the new dates
     * @param originalExamDuration the exam duration in seconds before the update
     * @return the latest individual end date after the change, or the exam end date if no student exams exist
     */
    @NonNull
    public ZonedDateTime getLatestIndividualExamEndDateAfterDurationChange(Exam exam, int originalExamDuration) {
        var maxWorkingTime = studentExamRepository.findMaxWorkingTimeByExamId(exam.getId());
        if (maxWorkingTime.isEmpty()) {
            return exam.getEndDate();
        }
        int workingTimeChange = exam.getDuration() - originalExamDuration;
        return exam.getStartDate().plusSeconds(projectWorkingTimeAfterDurationChange(maxWorkingTime.get(), originalExamDuration, workingTimeChange));
    }

    /**
     * Guards the start date of a supervised session against changes once students can conduct it.
     * <p>
     * A supervised session behaves like a real exam: the deadline of its attempts is derived from the start date of the
     * exam. Unlike for real exams, a changed start date is not pushed to the clients of conducting students, so they would
     * keep working towards the old deadline while the server enforces the new one. Therefore the start date can only be
     * changed until the students are allowed to enter the session.
     *
     * @param examMode          the mode of the exam, other modes are not checked
     * @param originalStartDate the start date before the change
     * @param newStartDate      the start date after the change
     * @param now               the current time
     * @throws BadRequestAlertException if the start date changes after the conduction of the supervised session opened
     */
    public void checkSupervisedSessionStartChangeAllowedElseThrow(ExamMode examMode, ZonedDateTime originalStartDate, ZonedDateTime newStartDate, ZonedDateTime now) {
        if (examMode != ExamMode.TEST_WITH_SUPERVISED_SESSION || originalStartDate == null || newStartDate == null
                || ExamDateUtil.truncatedComparator().compare(originalStartDate, newStartDate) == 0) {
            return;
        }
        if (now.isAfter(originalStartDate.minusMinutes(EXAM_START_WAIT_TIME_MINUTES))) {
            throw new BadRequestAlertException("The start date of a supervised session cannot be changed once students can enter it.", "exam", "supervisedSessionStarted");
        }
    }

    /**
     * Guards the end of a supervised session against changes that would reclassify attempts that already exist.
     * <p>
     * The end of the supervised session is derived from the start date and the working time of the exam, and it decides
     * whether an attempt is a supervised or a practice attempt. Moving it after the session has ended would turn practice
     * attempts into supervised ones (shortening their deadline and rejecting their autosaves), and moving it back during
     * the session would turn started supervised attempts into practice attempts. Both are rejected. Moving it later during
     * the session is fine, because no attempt can have been started in the interval that is added to the session.
     *
     * @param examId                       the id of the exam
     * @param examMode                     the mode of the exam, other modes are not checked
     * @param originalSupervisedSessionEnd the end of the supervised session before the change
     * @param newSupervisedSessionEnd      the end of the supervised session after the change
     * @param now                          the current time
     * @throws BadRequestAlertException if the change moves the end of the supervised session after it has ended, or past an attempt that was started
     */
    public void checkSupervisedSessionEndChangeAllowedElseThrow(long examId, ExamMode examMode, ZonedDateTime originalSupervisedSessionEnd, ZonedDateTime newSupervisedSessionEnd,
            ZonedDateTime now) {
        if (examMode != ExamMode.TEST_WITH_SUPERVISED_SESSION || originalSupervisedSessionEnd == null || newSupervisedSessionEnd == null
                || ExamDateUtil.truncatedComparator().compare(originalSupervisedSessionEnd, newSupervisedSessionEnd) == 0) {
            return;
        }
        if (!now.isBefore(originalSupervisedSessionEnd)) {
            throw new BadRequestAlertException("The start date and the working time cannot be changed after the supervised session has ended.", "exam", "supervisedSessionEnded");
        }
        ZonedDateTime from = originalSupervisedSessionEnd.isBefore(newSupervisedSessionEnd) ? originalSupervisedSessionEnd : newSupervisedSessionEnd;
        ZonedDateTime to = originalSupervisedSessionEnd.isBefore(newSupervisedSessionEnd) ? newSupervisedSessionEnd : originalSupervisedSessionEnd;
        if (studentExamRepository.existsAttemptStartedBetween(examId, from, to)) {
            throw new BadRequestAlertException("The supervised session cannot end before an attempt that was already started.", "exam", "supervisedSessionAttemptStarted");
        }
    }

    /**
     * Projects the working time a student exam gets when the exam duration changes, taking an existing individual time
     * extension into account: a student without an extension simply follows the duration change, while an extension is
     * rescaled proportionally so it keeps its share of the (new) regular working time.
     * <p>
     * {@link ExamService#updateStudentExamsAndRescheduleExercises} applies this to every student exam; validation that
     * has to run before that mutation uses the same function so the two cannot drift apart. Monotonically
     * non-decreasing in {@code currentWorkingTime}, so projecting the largest current working time yields the largest
     * new one.
     *
     * @param currentWorkingTime   the student exam's working time in seconds before the change
     * @param originalExamDuration the exam duration in seconds before the change
     * @param workingTimeChange    the change to the exam duration in seconds (may be negative)
     * @return the working time in seconds the student exam will have after the change
     * @throws ArithmeticException if the time adjustment or resulting working time exceeds the supported integer range
     */
    public static int projectWorkingTimeAfterDurationChange(int currentWorkingTime, int originalExamDuration, int workingTimeChange) {
        if (workingTimeChange == 0) {
            return currentWorkingTime;
        }
        int originalTimeExtension = currentWorkingTime - originalExamDuration;
        // NOTE: take the original working time extensions into account
        if (originalTimeExtension == 0) {
            return currentWorkingTime + workingTimeChange;
        }
        double relativeTimeExtension = (double) originalTimeExtension / (double) originalExamDuration;
        int newNormalWorkingTime = originalExamDuration + workingTimeChange;
        int timeAdjustment = Math.toIntExact(Math.round(newNormalWorkingTime * relativeTimeExtension));
        return Math.max(Math.addExact(newNormalWorkingTime, timeAdjustment), 0);
    }

    /**
     * Returns all individual exam end dates as determined by the working time of the student exams.
     * <p>
     * If no student exams are available, an empty set returned.
     *
     * @param examId the id of the exam
     * @return a set of all end dates. May return an empty set, if the exam has no start/end date or student exams cannot be found.
     * @throws EntityNotFoundException if no exam with the given examId can be found
     */
    public Set<ZonedDateTime> getAllIndividualExamEndDates(Long examId) {
        final var exam = examRepository.findByIdElseThrow(examId);
        return getAllIndividualExamEndDates(exam);
    }

    /**
     * Returns all individual exam end dates as determined by the working time of the student exams.
     * <p>
     * If no student exams are available, an empty set returned.
     *
     * @param exam the exam
     * @return a set of all end dates. May return an empty set, if student exams cannot be found.
     */
    public Set<ZonedDateTime> getAllIndividualExamEndDates(Exam exam) {
        var workingTimes = studentExamRepository.findAllDistinctWorkingTimesByExamId(exam.getId());
        return workingTimes.stream().map(timeInSeconds -> exam.getStartDate().plusSeconds(timeInSeconds)).collect(Collectors.toSet());
    }

    @NonNull
    public static ZonedDateTime getExamProgrammingExerciseUnlockDate(Exam exam) {
        // using start date minus 5 minutes here because unlocking will take some time.
        return exam.getStartDate().minusMinutes(EXAM_START_WAIT_TIME_MINUTES);
    }
}
