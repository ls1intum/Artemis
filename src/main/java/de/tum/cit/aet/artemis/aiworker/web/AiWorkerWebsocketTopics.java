package de.tum.cit.aet.artemis.aiworker.web;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.aiworker.config.AiWorkerEnabled;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopic;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicProvider;

/** Administrative AI worker updates. */
@Conditional(AiWorkerEnabled.class)
@Lazy
@Component
public class AiWorkerWebsocketTopics implements WebsocketTopicProvider {

    /** Worker status updates for elevated administrators. */
    public static final WebsocketTopic WORKERS = WebsocketTopic.of("/topic/admin/ai-workers", WebsocketTopicAccess.administrator());
}
