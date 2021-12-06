package de.tum.in.www1.artemis.service;

import static org.mockito.Mockito.*;

import javax.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring5.SpringTemplateEngine;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.notifications.NotificationTargetProvider;
import tech.jhipster.config.JHipsterProperties;

/**
 * This is a very basic testing class for the mail service
 * Because this service mostly uses other frameworks/services and loads values/variables into html templates
 * we only test that the correct send method is called
 */

public class MailServiceTest {

    @Autowired
    private static MailService mailService;

    @Mock
    private static JavaMailSender javaMailSender;

    @Mock
    private static MimeMessage mimeMessage;

    @Mock
    private static JHipsterProperties jHipsterProperties;

    @Mock
    private static JHipsterProperties.Mail mail;

    @Mock
    private static MessageSource messageSource;

    @Mock
    private static SpringTemplateEngine templateEngine;

    @Mock
    private static TimeService timeService;

    @Mock
    private static NotificationTargetProvider notificationTargetProvider;

    private static User student1;

    private static final String EMAIL_ADDRESS_A = "benige8246@omibrown.com";

    private static final String EMAIL_ADDRESS_B = "alex2713@gmail.com";

    private static String subject;

    private static String content;

    /**
     * Prepares the needed values and objects for testing
     */
    @BeforeAll
    public static void setUp() {
        student1 = new User();
        student1.setId(555L);
        student1.setEmail(EMAIL_ADDRESS_A);

        subject = "subject";
        content = "content";

        mimeMessage = mock(MimeMessage.class);

        javaMailSender = mock(JavaMailSender.class);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        mail = mock(JHipsterProperties.Mail.class);
        when(mail.getFrom()).thenReturn(EMAIL_ADDRESS_B);

        jHipsterProperties = mock(JHipsterProperties.class);
        when(jHipsterProperties.getMail()).thenReturn(mail);

        mailService = new MailService(jHipsterProperties, javaMailSender, messageSource, templateEngine, timeService, notificationTargetProvider);
    }

    /**
     * Very basic test that checks if the send method for emails is correctly called once
     */
    @Test
    public void testSendEmail() {
        mailService.sendEmail(student1, subject, content, false, true);
        verify(javaMailSender, times(1)).send(mimeMessage);
    }
}
