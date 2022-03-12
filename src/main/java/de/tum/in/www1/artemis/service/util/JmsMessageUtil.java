package de.tum.in.www1.artemis.service.util;

import javax.jms.JMSException;
import javax.jms.Message;

import org.springframework.jms.core.MessagePostProcessor;

import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

public class JmsMessageUtil {

    public static final String ERROR_MESSAGE = "There was a problem with the communication between server components. Please try again later!";

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

    public static String getCorrelationId(Message message) {
        try {
            return message.getJMSCorrelationID();
        }
        catch (JMSException e) {
            throw new InternalServerErrorException(ERROR_MESSAGE);
        }
    }

    public static MessagePostProcessor withCorrelationId(String correlationId) {
        return message -> {
            message.setJMSCorrelationID(correlationId);
            return message;
        };
    }

    public static String getJmsMessageSelector(String correlationId) {
        return "JMSCorrelationID='" + correlationId + "'";
    }
}
