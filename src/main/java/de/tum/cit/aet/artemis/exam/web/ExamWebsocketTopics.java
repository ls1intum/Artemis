package de.tum.cit.aet.artemis.exam.web;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketSubscription;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopic;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicProvider;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketUserTopic;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;

/**
 * The websocket topics of the exam module.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Component
public class ExamWebsocketTopics implements WebsocketTopicProvider {

    /**
     * Live events of one student exam: problem statement updates, working time changes and attendance checks. Only the student who owns the student exam may subscribe.
     */
    public static final WebsocketTopic STUDENT_EXAM_EVENTS = WebsocketTopic.of("/topic/exam-participation/studentExam/{studentExamId}/events",
            WebsocketTopicAccess.custom(ExamWebsocketTopics.class, ExamWebsocketTopics::isOwnerOfStudentExam));

    /**
     * Live events for all students of an exam, such as announcements.
     */
    public static final WebsocketTopic EXAM_EVENTS = WebsocketTopic.of("/topic/exam-participation/exam/{examId}/events",
            WebsocketTopicAccess.custom(ExamWebsocketTopics.class, ExamWebsocketTopics::isParticipantOrInstructorOfExam));

    /**
     * A signal without content whenever a student starts the exam, counted by the exam overview of the course staff.
     */
    public static final WebsocketTopic EXAM_STARTED = WebsocketTopic.of("/topic/exam/{examId}/started",
            WebsocketTopicAccess.custom(ExamWebsocketTopics.class, ExamWebsocketTopics::isAtLeastTutorOfExam));

    /**
     * A signal without content whenever a student hands in the exam, counted by the exam overview of the course staff.
     */
    public static final WebsocketTopic EXAM_SUBMITTED = WebsocketTopic.of("/topic/exam/{examId}/submitted",
            WebsocketTopicAccess.custom(ExamWebsocketTopics.class, ExamWebsocketTopics::isAtLeastTutorOfExam));

    /**
     * Progress of preparing the exercises of all student exams.
     */
    public static final WebsocketTopic EXERCISE_START_STATUS = WebsocketTopic.of("/topic/exams/{examId}/exercise-start-status",
            WebsocketTopicAccess.custom(ExamWebsocketTopics.class, ExamWebsocketTopics::isInstructorOfExam));

    /**
     * Progress of an exam import, for the instructor who started it.
     */
    public static final WebsocketUserTopic IMPORT_PROGRESS = WebsocketUserTopic.of("/topic/exam-import/{importId}");

    private final StudentExamRepository studentExamRepository;

    private final ExamRepository examRepository;

    private final UserRepository userRepository;

    public ExamWebsocketTopics(StudentExamRepository studentExamRepository, ExamRepository examRepository, UserRepository userRepository) {
        this.studentExamRepository = studentExamRepository;
        this.examRepository = examRepository;
        this.userRepository = userRepository;
    }

    private boolean isOwnerOfStudentExam(WebsocketSubscription subscription) {
        return studentExamRepository.existsByIdAndUserLogin(subscription.id("studentExamId"), subscription.login());
    }

    private boolean isParticipantOrInstructorOfExam(WebsocketSubscription subscription) {
        long examId = subscription.id("examId");
        return studentExamRepository.existsByExamIdAndUserLogin(examId, subscription.login()) || isInstructorInCourseOfExam(examId, subscription);
    }

    private boolean isInstructorOfExam(WebsocketSubscription subscription) {
        return isInstructorInCourseOfExam(subscription.id("examId"), subscription);
    }

    private boolean isAtLeastTutorOfExam(WebsocketSubscription subscription) {
        return examRepository.findCourseIdById(subscription.id("examId")).filter(courseId -> userRepository.isAtLeastTeachingAssistantInCourse(subscription.login(), courseId))
                .isPresent() || subscription.hasAdministratorAccess();
    }

    private boolean isInstructorInCourseOfExam(long examId, WebsocketSubscription subscription) {
        return examRepository.findCourseIdById(examId).filter(courseId -> userRepository.isAtLeastInstructorInCourse(subscription.login(), courseId)).isPresent()
                || subscription.hasAdministratorAccess();
    }
}
