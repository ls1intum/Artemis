package de.tum.cit.aet.artemis.exam.service;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.domain.event.ExamAttendanceCheckEvent;
import de.tum.cit.aet.artemis.exam.domain.event.ExamLiveEvent;
import de.tum.cit.aet.artemis.exam.domain.event.ExamWideAnnouncementEvent;
import de.tum.cit.aet.artemis.exam.domain.event.ProblemStatementUpdateEvent;
import de.tum.cit.aet.artemis.exam.domain.event.WorkingTimeUpdateEvent;
import de.tum.cit.aet.artemis.exam.repository.ExamLiveEventRepository;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * This service handles outgoing events during exams, so we can inform the client about a change or something that happened.
 * Currently, these events include:
 * <ul>
 * <li>Exam Wide Announcements
 * <li>Working Time Updates
 * <li>Exam Attendance Check
 * <li>Problem Statement Updates
 * </ul>
 * <p>
 * In the future, we can incorporate more events here.
 * <p>
 * Additionally, the service will maintain a log of all events that happened during the exam,
 * so we can display them to the user if they reconnect to the exam or join late.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Service
public class ExamLiveEventsService {

    /**
     * The name of the topic for notifying the client about events specific to a student exam.
     */
    public static final String STUDENT_EXAM_EVENT = "/topic/exam-participation/studentExam/%s/events";

    /**
     * The name of the topic for notifying the client about events specific to an entire exam.
     */
    public static final String EXAM_EVENT = "/topic/exam-participation/exam/%s/events";

    private final WebsocketMessagingService websocketMessagingService;

    private final ExamLiveEventRepository examLiveEventRepository;

    private final StudentExamRepository studentExamRepository;

    public ExamLiveEventsService(WebsocketMessagingService websocketMessagingService, ExamLiveEventRepository examLiveEventRepository,
            StudentExamRepository studentExamRepository) {
        this.websocketMessagingService = websocketMessagingService;
        this.examLiveEventRepository = examLiveEventRepository;
        this.studentExamRepository = studentExamRepository;
    }

    /**
     * Sends an announcement to all students in the exam.
     *
     * @param exam    the exam to send the announcement to.
     * @param message The message to send.
     * @return The created event.
     */
    public ExamWideAnnouncementEvent createAndDistributeExamAnnouncementEvent(Exam exam, String message) {
        var event = new ExamWideAnnouncementEvent();

        // Common fields
        event.setExamId(exam.getId());

        // Specific fields
        event.setTextContent(message);

        return this.storeAndDistributeLiveExamEvent(event);
    }

    /**
     * Send an attendance check event to the specified student
     *
     * @param studentExam The student exam the where the popup should be shown
     * @param message     The message to send.
     * @return The created event.
     */
    public ExamAttendanceCheckEvent createAndSendExamAttendanceCheckEvent(StudentExam studentExam, String message) {
        var event = new ExamAttendanceCheckEvent();

        // Common fields
        event.setExamId(studentExam.getExam().getId());
        event.setStudentExamId(studentExam.getId());

        // specific fields
        event.setTextContent(message);

        return this.storeAndDistributeLiveExamEvent(event);
    }

    /**
     * Send a working time update to the specified student.
     *
     * @param studentExam    The student exam the time was updated for
     * @param newWorkingTime The new working time in seconds
     * @param oldWorkingTime The old working time in seconds
     * @param courseWide     set to true if this event is caused by a course wide update that affects all students; false otherwise
     */
    public void createAndSendWorkingTimeUpdateEvent(StudentExam studentExam, int newWorkingTime, int oldWorkingTime, boolean courseWide) {
        var event = new WorkingTimeUpdateEvent();

        // Common fields
        event.setExamId(studentExam.getExam().getId());
        event.setStudentExamId(studentExam.getId());

        // Specific fields
        event.setNewWorkingTime(newWorkingTime);
        event.setOldWorkingTime(oldWorkingTime);
        event.setCourseWide(courseWide);

        this.storeAndDistributeLiveExamEvent(event);
    }

    /**
     * Send a problem statement update to all affected students.
     *
     * @param exercise The exam exercise the problem statement was updated for
     * @param message  The message to send
     */
    public void createAndSendProblemStatementUpdateEvent(Exercise exercise, String message) {
        Exam exam = exercise.getExam();
        studentExamRepository.findAllWithExercisesByExamId(exam.getId()).stream().filter(studentExam -> studentExam.getExercises().contains(exercise))
                .forEach(studentExam -> this.createAndSendProblemStatementUpdateEvent(studentExam, exercise, message));
    }

    /**
     * Send a problem statement update to the specified student.
     *
     * @param studentExam The student exam containing the exercise with updated problem statement
     * @param exercise    The updated exercise
     * @param message     The message to send
     */
    public void createAndSendProblemStatementUpdateEvent(StudentExam studentExam, Exercise exercise, String message) {
        var event = new ProblemStatementUpdateEvent();

        // Common fields
        event.setExamId(studentExam.getExam().getId());
        event.setStudentExamId(studentExam.getId());

        // Specific fields
        event.setTextContent(message);
        event.setProblemStatement(exercise.getProblemStatement());
        event.setExerciseId(exercise.getId());
        event.setExerciseName(exercise.getExerciseGroup().getTitle());

        this.storeAndDistributeLiveExamEvent(event);
    }

    /**
     * Stores the event in the database and sends it either to all student exams of the exam or to a specific student exam,
     * depending on whether the event is for a specific student exam or for the entire exam.
     *
     * @param event The event to store and distribute.
     * @return The stored event.
     */
    private <T extends ExamLiveEvent> T storeAndDistributeLiveExamEvent(T event) {
        var storedEvent = examLiveEventRepository.save(event);

        // If the event is for a specific student exam, only send it to that student exam.
        if (event.getStudentExamId() != null) {
            websocketMessagingService.sendMessage(STUDENT_EXAM_EVENT.formatted(storedEvent.getStudentExamId()), storedEvent.asDTO());
            return storedEvent;
        }

        // Otherwise, send it to all student exams of the exam.
        websocketMessagingService.sendMessage(EXAM_EVENT.formatted(storedEvent.getExamId()), storedEvent.asDTO());

        return storedEvent;
    }
}
