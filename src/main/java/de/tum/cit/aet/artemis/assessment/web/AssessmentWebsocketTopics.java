package de.tum.cit.aet.artemis.assessment.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopic;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicProvider;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketUserTopic;

/**
 * The websocket topics of the assessment module.
 */
@Profile(PROFILE_CORE)
@Lazy
@Component
public class AssessmentWebsocketTopics implements WebsocketTopicProvider {

    /**
     * Every new result of an exercise with its full feedback, for the staff pages of the exercise.
     */
    public static final WebsocketTopic EXERCISE_RESULTS = WebsocketTopic.of("/topic/exercise/{exerciseId}/newResults",
            WebsocketTopicAccess.atLeastTutorInExerciseAndInstructorInExamExercise("exerciseId"));

    /**
     * New results of the user's own participations, with the feedback the user may see.
     */
    public static final WebsocketUserTopic NEW_RESULTS = WebsocketUserTopic.of("/topic/newResults");
}
