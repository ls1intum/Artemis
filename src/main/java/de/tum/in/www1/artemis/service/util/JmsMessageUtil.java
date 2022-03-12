package de.tum.in.www1.artemis.service.util;

import javax.jms.JMSException;
import javax.jms.Message;

import org.springframework.jms.core.MessagePostProcessor;

import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

/**
 * Util class for JMS messages.
 */
public class JmsMessageUtil {

    public static final String ERROR_MESSAGE = "There was a problem with the communication between server components. Please try again later!";

    /**
     * Parse the vody of a JMS message accoring to thegiven body type.
     *
     * @param message  the message
     * @param bodyType the body type
     * @param <T>
     * @return the parsed body of the message
     */
    public static <T> T parseBody(Message message, Class<T> bodyType) {
        if (message == null) {
            return null;
        }

        try {
            return message.getBody(bodyType);
        }
        catch (JMSException e) {
            throw new InternalServerErrorException(ERROR_MESSAGE);
        }
    }

    /**
     * Get the correlation id of a JMS message.
     *
     * @param message the message
     * @return the correlation id of the message
     */
    public static String getCorrelationId(Message message) {
        try {
            return message.getJMSCorrelationID();
        }
        catch (JMSException e) {
            throw new InternalServerErrorException(ERROR_MESSAGE);
        }
    }

    /**
     * Process a message and set its correlation id.
     *
     * @param correlationId the correlation id to set
     * @return the message processor
     */
    public static MessagePostProcessor withCorrelationId(String correlationId) {
        return message -> {
            message.setJMSCorrelationID(correlationId);
            return message;
        };
    }

    /**
     * Get JMS message selector for collection id.
     *
     * @param correlationId the correlation id to select
     * @return the selector as string
     */
    public static String getJmsMessageSelector(String correlationId) {
        return "JMSCorrelationID='" + correlationId + "'";
    }
}
