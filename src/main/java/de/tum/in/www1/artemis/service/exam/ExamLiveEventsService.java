package de.tum.in.www1.artemis.service.exam;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.exam.event.*;
import de.tum.in.www1.artemis.repository.ExamLiveEventRepository;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;

/**
 * This service handles outgoing events during exams, so we can inform the client about a change or something that happened.
 * Currently, these events include:
 * <ul>
 * <li>Exam Wide Announcements
 * <li>Working Time Updates
 * <li>Exam Attendance Check
 * </ul>
 * <p>
 * In the future, we can incorporate more events here.
 * <p>
 * Additionally, the service will maintain a log of all events that happened during the exam,
 * so we can display them to the user if they reconnect to the exam or join late.
 */
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

    public ExamLiveEventsService(WebsocketMessagingService websocketMessagingService, ExamLiveEventRepository examLiveEventRepository) {
        this.websocketMessagingService = websocketMessagingService;
        this.examLiveEventRepository = examLiveEventRepository;
    }

    /**
     * Sends an announcement to all students in the exam.
     *
     * @param exam    the exam to send the announcement to.
     * @param message The message to send.
     * @param sentBy  The user who sent the message.
     * @return The created event.
     */
    public ExamWideAnnouncementEvent createAndDistributeExamAnnouncementEvent(Exam exam, String message, User sentBy) {
        var event = new ExamWideAnnouncementEvent();

        // Common fields
        event.setExamId(exam.getId());
        event.setCreatedBy(sentBy.getName());

        // Specific fields
        event.setTextContent(message);

        return this.storeAndDistributeLiveExamEvent(event);
    }

    /**
     * Send an attendance check event to the specified student
     *
     * @param studentExam The student exam the where the popup should be shown
     * @param message     The message to send.
     * @param sentBy      The user who sent the message.
     * @return The created event.
     */
    public ExamAttendanceCheckEvent createAndSendExamAttendanceCheckEvent(StudentExam studentExam, String message, User sentBy) {
        var event = new ExamAttendanceCheckEvent();

        // Common fields
        event.setExamId(studentExam.getExam().getId());
        event.setStudentExamId(studentExam.getId());
        event.setCreatedBy(sentBy.getName());

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
     * @param sentBy         The user who performed the update
     */
    public void createAndSendWorkingTimeUpdateEvent(StudentExam studentExam, int newWorkingTime, int oldWorkingTime, boolean courseWide, User sentBy) {
        var event = new WorkingTimeUpdateEvent();

        // Common fields
        event.setExamId(studentExam.getExam().getId());
        event.setStudentExamId(studentExam.getId());
        event.setCreatedBy(sentBy.getName());

        // Specific fields
        event.setNewWorkingTime(newWorkingTime);
        event.setOldWorkingTime(oldWorkingTime);
        event.setCourseWide(courseWide);

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
