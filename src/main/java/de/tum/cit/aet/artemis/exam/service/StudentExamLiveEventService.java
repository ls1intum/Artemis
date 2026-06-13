package de.tum.cit.aet.artemis.exam.service;

import static java.time.ZonedDateTime.now;

import java.time.ZonedDateTime;
import java.util.Optional;

import jakarta.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.examevent.ExamAttendanceCheckEventDTO;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.localci.service.AutomaticAfterDueDateService;

@Conditional(ExamEnabled.class)
@Lazy
@Service
public class StudentExamLiveEventService {

    private static final Logger log = LoggerFactory.getLogger(StudentExamLiveEventService.class);

    private final UserRepository userRepository;

    private final StudentExamRepository studentExamRepository;

    private final ExamAccessService examAccessService;

    private final ExamService examService;

    private final ExamLiveEventsService examLiveEventsService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final Optional<AutomaticAfterDueDateService> automaticAfterDueDateService;

    public StudentExamLiveEventService(UserRepository userRepository, StudentExamRepository studentExamRepository, ExamAccessService examAccessService, ExamService examService,
            ExamLiveEventsService examLiveEventsService, InstanceMessageSendService instanceMessageSendService,
            Optional<AutomaticAfterDueDateService> automaticAfterDueDateService) {
        this.userRepository = userRepository;
        this.studentExamRepository = studentExamRepository;
        this.examAccessService = examAccessService;
        this.examService = examService;
        this.examLiveEventsService = examLiveEventsService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.automaticAfterDueDateService = automaticAfterDueDateService;
    }

    /**
     * Updates the working time of a student exam and sends the corresponding live event if the exam is already visible.
     *
     * @param examId        the id of the exam
     * @param studentExamId the id of the student exam
     * @param workingTime   the new working time in seconds
     * @return the updated student exam
     */
    public StudentExam updateWorkingTime(Long examId, Long studentExamId, Integer workingTime) {
        var now = now();
        if (workingTime <= 0) {
            throw new BadRequestException();
        }
        StudentExam studentExam = studentExamRepository.findByIdWithExercisesElseThrow(studentExamId);
        var originalWorkingTime = studentExam.getWorkingTime();

        final Exam exam = examService.findByIdWithExerciseGroupsAndExercisesElseThrow(examId, false);
        final ZonedDateTime originalLatestExamEndDateWithGrace = automaticAfterDueDateService.map(service -> service.getLatestExamEndDateWithGrace(exam)).orElse(null);

        studentExam.setWorkingTime(workingTime);
        var savedStudentExam = studentExamRepository.save(studentExam);

        if (!savedStudentExam.isTestRun()) {
            if (now.isAfter(exam.getVisibleDate())) {
                examLiveEventsService.createAndSendWorkingTimeUpdateEvent(savedStudentExam, workingTime, originalWorkingTime, false);
            }
            if (automaticAfterDueDateService.isPresent()) {
                try {
                    automaticAfterDueDateService.orElseThrow().updateAndSaveBuildAndTestDateInProgrammingExercisesOfExam(exam, originalLatestExamEndDateWithGrace)
                            .forEach(instanceMessageSendService::sendProgrammingExerciseSchedule);
                }
                catch (JsonProcessingException e) {
                    log.error("The build plan configuration is invalid for a programming exercise in exam {}", exam.getId());
                }
            }
        }

        return savedStudentExam;
    }

    /**
     * Creates an attendance check event for the student exam of the given student in the exam.
     *
     * @param examId       the id of the exam
     * @param studentLogin the login of the student
     * @param message      the optional message to display to the student
     * @return the created attendance check event
     */
    public ExamAttendanceCheckEventDTO createAttendanceCheckEvent(Long examId, String studentLogin, String message) {
        var student = userRepository.getUserByLoginElseThrow(studentLogin);
        StudentExam studentExam = studentExamRepository.findWithExercisesByUserIdAndExamId(student.getId(), examId, false).orElseThrow();

        examAccessService.checkStudentExamExistsAndBelongsToExamElseThrow(studentExam.getId(), examId);

        var exam = studentExam.getExam();
        if (!exam.isVisibleToStudents()) {
            throw new BadRequestAlertException("Exam is not visible to students", "exam", "examNotVisible");
        }

        var event = examLiveEventsService.createAndSendExamAttendanceCheckEvent(studentExam, message);

        return event.asDTO();
    }
}
