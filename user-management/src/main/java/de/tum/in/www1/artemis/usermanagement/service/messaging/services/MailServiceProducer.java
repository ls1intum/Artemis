package de.tum.in.www1.artemis.usermanagement.service.messaging.services;

import de.tum.in.www1.artemis.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
@EnableJms
public class MailServiceProducer {
    private final Logger log = LoggerFactory.getLogger(MailServiceProducer.class);

    private static final String USER_MANAGEMENT_QUEUE_SEND_ACTIVATION_MAIL = "user_management_queue.send_activation_mail";
    private static final String USER_MANAGEMENT_QUEUE_SEND_PASSWORD_RESET_MAIL = "user_management_queue.send_password_reset_mail";

    @Autowired
    private final JmsTemplate jmsTemplate;

    public MailServiceProducer(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    public void sendActivationMail(User user)  {
        log.info("Send data {}", user);
        jmsTemplate.convertAndSend(USER_MANAGEMENT_QUEUE_SEND_ACTIVATION_MAIL, user);
    }

    public void sendPasswordResetMail(User user)  {
        log.info("Send data {}", user);
        jmsTemplate.convertAndSend(USER_MANAGEMENT_QUEUE_SEND_PASSWORD_RESET_MAIL, user);
    }
}
