package de.tum.cit.aet.artemis.hyperion.web;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopic;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicProvider;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketUserTopic;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;

/**
 * The websocket topics of the Hyperion module.
 */
@Conditional(HyperionEnabled.class)
@Lazy
@Component
public class HyperionWebsocketTopics implements WebsocketTopicProvider {

    /** Live progress and file changes of an exercise generation job. */
    public static final WebsocketUserTopic EXERCISE_GENERATION_JOB = WebsocketUserTopic.of("/topic/hyperion/exercise-generation/jobs/{jobId}");

    /** Exercise mutation state for editors. */
    public static final WebsocketTopic EXERCISE_GENERATION_STATE = WebsocketTopic.of("/topic/hyperion/exercise-generation/exercises/{exerciseId}/state",
            WebsocketTopicAccess.atLeastEditorInExercise("exerciseId"));

    /** Active generation snapshots for elevated administrators. */
    public static final WebsocketTopic ACTIVE_GENERATIONS = WebsocketTopic.of("/topic/admin/hyperion-generations", WebsocketTopicAccess.administrator());

    /**
     * Events of a code generation job.
     */
    public static final WebsocketUserTopic CODE_GENERATION_JOB = WebsocketUserTopic.of("/topic/hyperion/code-generation/jobs/{jobId}");

    /**
     * Events of an exercise variant generation job.
     */
    public static final WebsocketUserTopic VARIANT_GENERATION_JOB = WebsocketUserTopic.of("/topic/hyperion/variant-generation/jobs/{jobId}");
}
