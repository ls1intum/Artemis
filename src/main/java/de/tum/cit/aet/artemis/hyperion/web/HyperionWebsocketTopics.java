package de.tum.cit.aet.artemis.hyperion.web;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicProvider;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketUserTopic;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;

/**
 * The websocket topics of the Hyperion module. Both are per-user topics that report the progress of a job to the user who started it.
 */
@Conditional(HyperionEnabled.class)
@Lazy
@Component
public class HyperionWebsocketTopics implements WebsocketTopicProvider {

    /**
     * Events of a code generation job.
     */
    public static final WebsocketUserTopic CODE_GENERATION_JOB = WebsocketUserTopic.of("/topic/hyperion/code-generation/jobs/{jobId}");

    /**
     * Events of an exercise variant generation job.
     */
    public static final WebsocketUserTopic VARIANT_GENERATION_JOB = WebsocketUserTopic.of("/topic/hyperion/variant-generation/jobs/{jobId}");
}
