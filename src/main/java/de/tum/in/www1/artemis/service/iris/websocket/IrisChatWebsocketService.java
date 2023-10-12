package de.tum.in.www1.artemis.service.iris.websocket;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;

@Service
public class IrisChatWebsocketService extends IrisWebsocketService {

    public IrisChatWebsocketService(WebsocketMessagingService websocketMessagingService, IrisRateLimitService rateLimitService) {
        // Might want to change topic to "chat-sessions" or something similar
        super(websocketMessagingService, rateLimitService, "sessions");
    }

    @Override
    protected void checkSessionType(IrisSession irisSession) {
        if (!(irisSession instanceof IrisChatSession)) {
            throw new UnsupportedOperationException("Only IrisChatSession is supported");
        }
    }

    @Override
    protected User getUser(IrisSession irisSession) {
        return ((IrisChatSession) irisSession).getUser();
    }

}
