package de.tum.in.www1.artemis.service.iris.websocket;

import de.tum.in.www1.artemis.domain.iris.session.IrisCodeEditorSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import org.springframework.stereotype.Service;

@Service
public class IrisCodeEditorWebsocketService extends IrisWebsocketService {
    
    public IrisCodeEditorWebsocketService(WebsocketMessagingService websocketMessagingService) {
        super(websocketMessagingService, "code-editor-sessions");
    }
    
    @Override
    protected void checkSessionType(IrisSession irisSession) {
        if (!(irisSession instanceof IrisCodeEditorSession)) {
            throw new UnsupportedOperationException("Only IrisCodeEditorSession is supported");
        }
    }
    
    @Override
    protected String getUserLogin(IrisSession irisSession) {
        return ((IrisCodeEditorSession) irisSession).getUser().getLogin();
    }
    
}
