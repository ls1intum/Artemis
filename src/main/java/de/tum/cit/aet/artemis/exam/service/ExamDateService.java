package de.tum.cit.aet.artemis.exam.service;

import static de.tum.cit.aet.artemis.core.config.Constants.EXAM_START_WAIT_TIME_MINUTES;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
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
        if (studentParticipation.isTestRun()) {
            return false;
        }
        // Students can participate in a test exam multiple times, meaning there can be multiple student exams for a single exam.
        // For test exams, we aim to find the latest student exam.
        // For real exams, we aim to find the only existing student exam.
        Optional<StudentExam> optionalStudentExam = studentExamRepository.findFirstByExamIdAndUserIdOrderByCreatedDateDesc(exam.getId(),
                studentParticipation.getParticipant().getId());

        if (optionalStudentExam.isPresent()) {
            StudentExam studentExam = optionalStudentExam.get();
            return Boolean.TRUE.equals(studentExam.isSubmitted()) || studentExam.isEnded();
        }

        throw new IllegalStateException("No student exam found for student participation " + studentParticipation.getId());
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
        return Math.max(newNormalWorkingTime + timeAdjustment, 0);
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
     * @return a set of all end dates. May return an empty set, if the exam has no start/end date or student exams cannot be found.
     */
    @Nullable
    public Set<ZonedDateTime> getAllIndividualExamEndDates(Exam exam) {
        if (exam.getStartDate() == null) {
            return null;
        }
        var workingTimes = studentExamRepository.findAllDistinctWorkingTimesByExamId(exam.getId());
        return workingTimes.stream().map(timeInSeconds -> exam.getStartDate().plusSeconds(timeInSeconds)).collect(Collectors.toSet());
    }

    @NonNull
    public static ZonedDateTime getExamProgrammingExerciseUnlockDate(Exam exam) {
        // using start date minus 5 minutes here because unlocking will take some time.
        return exam.getStartDate().minusMinutes(EXAM_START_WAIT_TIME_MINUTES);
    }
}
