package de.tum.in.www1.artemis.service.iris.websocket;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.iris.session.IrisCodeEditorSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;

@Service
public class IrisCodeEditorWebsocketService extends IrisWebsocketService {

    public IrisCodeEditorWebsocketService(WebsocketMessagingService websocketMessagingService, IrisRateLimitService rateLimitService) {
        super(websocketMessagingService, rateLimitService, "code-editor-sessions");
    }

    @Override
    protected void checkSessionType(IrisSession irisSession) {
        if (!(irisSession instanceof IrisCodeEditorSession)) {
            throw new UnsupportedOperationException("Only IrisCodeEditorSession is supported");
        }
    }

    @Override
    protected User getUser(IrisSession irisSession) {
        return ((IrisCodeEditorSession) irisSession).getUser();
    }

}
