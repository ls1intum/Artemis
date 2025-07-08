package de.tum.cit.aet.artemis.iris.util;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;

public class IrisMessageFactory {

    /**
     * This is a utility class that should not be instantiated, which is why the constructor is private.
     */
    private IrisMessageFactory() {
    }

    public static IrisMessage createIrisMessage(IrisMessageSender irisMessageSender) {
        IrisMessage message = new IrisMessage();
        message.setSender(irisMessageSender);
        message.setContent(IrisMessageContentFactory.createIrisMessageContents());
        return message;
    }

    public static IrisMessage createIrisMessageForSession(IrisSession irisSession) {
        IrisMessage message = new IrisMessage();
        message.setSession(irisSession);
        return message;
    }

    public static IrisMessage createIrisMessageForSessionWithContent(IrisSession irisSession) {
        IrisMessage message = createIrisMessageForSession(irisSession);
        message.setContent(IrisMessageContentFactory.createIrisMessageContents());
        return message;
    }
}
