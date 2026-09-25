package de.tum.cit.aet.artemis.core.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopic;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicProvider;

/**
 * The websocket topics of the core module.
 */
@Profile(PROFILE_CORE)
@Lazy
@Component
public class CoreWebsocketTopics implements WebsocketTopicProvider {

    /**
     * The currently enabled features, which every client needs to show or hide functionality.
     */
    public static final WebsocketTopic FEATURE_TOGGLES = WebsocketTopic.of("/topic/management/feature-toggles", WebsocketTopicAccess.anyAuthenticatedUser());
}
