package de.tum.cit.aet.artemis.admin.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketSubscription;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopic;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicProvider;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;

/**
 * The websocket topics of course and exam archives. Their progress messages can name exercises, files and participants, so only instructors may subscribe.
 */
@Profile(PROFILE_CORE)
@Lazy
@Component
public class ExportWebsocketTopics implements WebsocketTopicProvider {

    /**
     * Progress of archiving a course.
     */
    public static final WebsocketTopic COURSE_ARCHIVE = WebsocketTopic.of("/topic/courses/{courseId}/export-course", WebsocketTopicAccess.atLeastInstructorInCourse("courseId"));

    /**
     * Progress of archiving an exam.
     */
    public static final WebsocketTopic EXAM_ARCHIVE = WebsocketTopic.of("/topic/exams/{examId}/export",
            WebsocketTopicAccess.custom(ExportWebsocketTopics.class, ExportWebsocketTopics::isInstructorOfExam));

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    private final UserRepository userRepository;

    public ExportWebsocketTopics(Optional<ExamRepositoryApi> examRepositoryApi, UserRepository userRepository) {
        this.examRepositoryApi = examRepositoryApi;
        this.userRepository = userRepository;
    }

    private boolean isInstructorOfExam(WebsocketSubscription subscription) {
        long examId = subscription.id("examId");
        return examRepositoryApi.flatMap(api -> api.findCourseIdById(examId)).filter(courseId -> userRepository.isAtLeastInstructorInCourse(subscription.login(), courseId))
                .isPresent() || subscription.hasAdministratorAccess();
    }
}
