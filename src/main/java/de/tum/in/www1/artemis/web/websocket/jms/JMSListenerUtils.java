package de.tum.in.www1.artemis.web.websocket.jms;

import javax.jms.JMSException;
import javax.jms.Message;

public class JMSListenerUtils {

    public static String extractUsernameFromMessage(Message message) throws JMSException {
        return message.getStringProperty("user-name");
    }
}
