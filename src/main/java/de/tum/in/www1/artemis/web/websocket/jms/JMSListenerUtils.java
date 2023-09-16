package de.tum.in.www1.artemis.web.websocket.jms;

import javax.jms.JMSException;
import javax.jms.Message;

/**
 * Common utils for JMS listeners.
 */
public class JMSListenerUtils {

    /**
     * Extract the username from a received JMS message.
     *
     * @param message the message from which the username should be extracted
     * @return the username as string
     * @throws JMSException if the property could not be extracted
     */
    public static String extractUsernameFromMessage(Message message) throws JMSException {
        return message.getStringProperty("user-name");
    }
}
