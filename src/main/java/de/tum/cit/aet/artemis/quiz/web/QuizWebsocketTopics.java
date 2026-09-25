package de.tum.cit.aet.artemis.quiz.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketSubscription;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopic;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicProvider;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketUserTopic;
import de.tum.cit.aet.artemis.quiz.repository.QuizBatchRepository;

/**
 * The websocket topics of the quiz module.
 */
@Profile(PROFILE_CORE)
@Lazy
@Component
public class QuizWebsocketTopics implements WebsocketTopicProvider {

    /**
     * Changes to the visible quizzes of a course: start, end and visibility. The payload carries the questions once a quiz has started and the solutions once it has ended.
     */
    public static final WebsocketTopic COURSE_QUIZ_EXERCISES = WebsocketTopic.of("/topic/courses/{courseId}/quizExercises",
            WebsocketTopicAccess.atLeastStudentInCourse("courseId"));

    /**
     * The start of one batch of a quiz in batched mode, carrying the questions. Only the students who joined that batch may subscribe, and the course staff.
     */
    public static final WebsocketTopic QUIZ_BATCH = WebsocketTopic.of("/topic/courses/{courseId}/quizExercises/{quizBatchId}",
            WebsocketTopicAccess.custom(QuizWebsocketTopics.class, QuizWebsocketTopics::hasJoinedQuizBatchOrIsTutor));

    /**
     * A signal that the statistics of a quiz changed, so the statistics pages reload them.
     */
    public static final WebsocketTopic QUIZ_STATISTICS = WebsocketTopic.of("/topic/statistic/{quizExerciseId}", WebsocketTopicAccess.atLeastTutorInExercise("quizExerciseId"));

    /**
     * The result of a student's quiz participation.
     */
    public static final WebsocketUserTopic QUIZ_PARTICIPATION = WebsocketUserTopic.of("/topic/exercise/{quizExerciseId}/participation");

    private final QuizBatchRepository quizBatchRepository;

    private final UserRepository userRepository;

    public QuizWebsocketTopics(QuizBatchRepository quizBatchRepository, UserRepository userRepository) {
        this.quizBatchRepository = quizBatchRepository;
        this.userRepository = userRepository;
    }

    private boolean hasJoinedQuizBatchOrIsTutor(WebsocketSubscription subscription) {
        long quizBatchId = subscription.id("quizBatchId");
        if (quizBatchRepository.existsByIdAndJoinedStudentLogin(quizBatchId, subscription.login())) {
            return true;
        }
        return quizBatchRepository.findQuizExerciseIdById(quizBatchId).filter(exerciseId -> userRepository.isAtLeastTeachingAssistantInExercise(subscription.login(), exerciseId))
                .isPresent() || subscription.hasAdministratorAccess();
    }
}
