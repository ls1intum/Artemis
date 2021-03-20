package de.tum.in.www1.artemis.service.exam;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

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
     * Returns the latest individual exam end date as determined by the working time of the student exams.
     * <p>
     * If no student exams are available, the exam end date is returned.
     *
     * @param examId the id of the exam
     * @return the latest end date or the exam end date if no student exams are found. May return <code>null</code>, if the exam has no start/end date.
     * @throws EntityNotFoundException if no exam with the given examId can be found
     */
    @NotNull
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
    @NotNull
    public ZonedDateTime getLatestIndividualExamEndDate(Exam exam) {
        var maxWorkingTime = studentExamRepository.findMaxWorkingTimeByExamId(exam.getId());
        return maxWorkingTime.map(timeInSeconds -> exam.getStartDate().plusSeconds(timeInSeconds)).orElse(exam.getEndDate());
    }

    /**
     * Returns the individual exam end date of an individual user
     *
     * @param exam            the exam
     * @param user            the student who participates in the exam
     * @param withGracePeriod include grace period into end date
     * @return the end date or the exam end date if the student exam was not found. May return <code>null</code>, if the exam has no start/end date.
     */
    public ZonedDateTime getIndividualExamEndDateOfUser(Exam exam, User user, boolean withGracePeriod) {
        var optionalStudentExam = studentExamRepository.findByExamIdAndUserId(exam.getId(), user.getId());
        return optionalStudentExam.map(studentExam -> {
            if (studentExam.getWorkingTime() != null && studentExam.getWorkingTime() > 0) {
                return withGracePeriod ? studentExam.getIndividualEndDateWithGracePeriod() : studentExam.getIndividualEndDate();
            }
            return exam.getEndDate();
        }).orElse(exam.getEndDate());
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
    public Set<ZonedDateTime> getAllIndividualExamEndDates(Exam exam) {
        if (exam.getStartDate() == null) {
            return null;
        }
        var workingTimes = studentExamRepository.findAllDistinctWorkingTimesByExamId(exam.getId());
        return workingTimes.stream().map(timeInSeconds -> exam.getStartDate().plusSeconds(timeInSeconds)).collect(Collectors.toSet());
    }
}
