package de.tum.in.www1.artemis.service.messaging.services;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.MailService;

/**
 * Consumer which consumes all messages that are sent from other serices realted to sending emails to the user.
 */
@Component
@EnableJms
public class MailServiceConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailServiceConsumer.class);

    private static final String USER_MANAGEMENT_QUEUE_SEND_ACTIVATION_MAIL = "user_management_queue.send_activation_mail";

    private static final String USER_MANAGEMENT_QUEUE_SEND_PASSWORD_RESET_MAIL = "user_management_queue.send_password_reset_mail";

    private MailService mailService;

    public MailServiceConsumer(MailService mailService) {
        this.mailService = mailService;
    }

    /**
     * Consumes messages related to sending account activation emails and sends the actual email.
     *
     * @param message the message coming from the message broker
     */
    @JmsListener(destination = USER_MANAGEMENT_QUEUE_SEND_ACTIVATION_MAIL)
    public void sendActivationEmail(Message message) {
        Optional<User> user = readUserValue(message.getPayload().toString());
        if (user.isPresent()) {
            mailService.sendActivationEmail(user.get());
        }
        else {
            LOGGER.error("Email is not sent because the user was not present");
        }
    }

    /**
     * Consumes messages related to sending password reset emails and sends the actual email.
     *
     * @param message the message coming from the message broker
     */
    @JmsListener(destination = USER_MANAGEMENT_QUEUE_SEND_PASSWORD_RESET_MAIL)
    public void sendPasswordResetEmail(Message message) {
        Optional<User> user = readUserValue(message.getPayload().toString());
        if (user.isPresent()) {
            mailService.sendPasswordResetMail(user.get());
        }
        else {
            LOGGER.error("Email is not sent because the user was not present");
        }
    }

    private Optional<User> readUserValue(String value) {
        ObjectMapper objectMapper = new ObjectMapper();
        User user;
        try {
            user = objectMapper.readValue(value, User.class);
        }
        catch (JsonProcessingException e) {
            LOGGER.error("User data could not be extracted.");
            return Optional.empty();
        }
        return Optional.of(user);
    }
}
