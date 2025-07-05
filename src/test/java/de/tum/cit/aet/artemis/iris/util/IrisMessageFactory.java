package de.tum.cit.aet.artemis.iris.util;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;

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
}
