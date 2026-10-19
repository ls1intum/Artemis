package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopic;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicProvider;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketUserTopic;

/**
 * The websocket topics of the programming module.
 */
@Profile(PROFILE_CORE)
@Lazy
@Component
public class ProgrammingWebsocketTopics implements WebsocketTopicProvider {

    /**
     * New submissions of the template and solution participations, and build trigger errors of any participation of the exercise.
     */
    public static final WebsocketTopic EXERCISE_SUBMISSIONS = WebsocketTopic.of("/topic/exercise/{exerciseId}/newSubmissions",
            WebsocketTopicAccess.atLeastTutorInExerciseAndEditorInExamExercise("exerciseId"));

    /**
     * The start of the build of a template or solution submission.
     */
    public static final WebsocketTopic EXERCISE_SUBMISSION_PROCESSING = WebsocketTopic.of("/topic/exercise/{exerciseId}/submissionProcessing",
            WebsocketTopicAccess.atLeastTutorInExerciseAndEditorInExamExercise("exerciseId"));

    /**
     * The test cases of an exercise, including hidden ones, whenever a build result changes them.
     */
    public static final WebsocketTopic TEST_CASES = WebsocketTopic.of("/topic/programming-exercises/{exerciseId}/test-cases",
            WebsocketTopicAccess.atLeastTutorInExercise("exerciseId"));

    /**
     * A signal that the test cases of an exercise changed, so existing results may be outdated.
     */
    public static final WebsocketTopic TEST_CASES_CHANGED = WebsocketTopic.of("/topic/programming-exercises/{exerciseId}/test-cases-changed",
            WebsocketTopicAccess.atLeastTutorInExercise("exerciseId"));

    /**
     * Whether the builds of all participations of an exercise are being triggered.
     */
    public static final WebsocketTopic ALL_BUILDS_TRIGGERED = WebsocketTopic.of("/topic/programming-exercises/{exerciseId}/all-builds-triggered",
            WebsocketTopicAccess.atLeastTutorInExercise("exerciseId"));

    /**
     * New submissions of the user's own participations, and their build trigger errors.
     */
    public static final WebsocketUserTopic NEW_SUBMISSIONS = WebsocketUserTopic.of("/topic/newSubmissions");

    /**
     * The start of the build of a submission of the user's own participations.
     */
    public static final WebsocketUserTopic SUBMISSION_PROCESSING = WebsocketUserTopic.of("/topic/submissionProcessing");
}
