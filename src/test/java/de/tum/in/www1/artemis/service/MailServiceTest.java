package de.tum.in.www1.artemis.service;

import static org.mockito.Mockito.*;

import javax.mail.internet.MimeMessage;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring5.SpringTemplateEngine;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.ArtemisMailException;
import tech.jhipster.config.JHipsterProperties;

/**
 * This is a very basic testing class for the mail service
 * Because this service mostly uses other frameworks/services and loads values/variables into html templates
 * we only test that the correct send method is called
 */
class MailServiceTest {

    private MailService mailService;

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private MimeMessage mimeMessage;

    @Mock
    private JHipsterProperties jHipsterProperties;

    @Mock
    private JHipsterProperties.Mail mail;

    @Mock
    private MessageSource messageSource;

    @Mock
    private SpringTemplateEngine templateEngine;

    @Mock
    private TimeService timeService;

    private User student1;

    private String subject;

    private String content;

    /**
     * Prepares the needed values and objects for testing
     */
    @BeforeEach
    void setUp() {
        student1 = new User();
        student1.setId(555L);
        String EMAIL_ADDRESS_A = "benige8246@omibrown.com";
        student1.setEmail(EMAIL_ADDRESS_A);

        subject = "subject";
        content = "content";

        mimeMessage = mock(MimeMessage.class);

        javaMailSender = mock(JavaMailSender.class);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        mail = mock(JHipsterProperties.Mail.class);
        String EMAIL_ADDRESS_B = "alex2713@gmail.com";
        when(mail.getFrom()).thenReturn(EMAIL_ADDRESS_B);

        jHipsterProperties = mock(JHipsterProperties.class);
        when(jHipsterProperties.getMail()).thenReturn(mail);

        mailService = new MailService(jHipsterProperties, javaMailSender, messageSource, templateEngine, timeService);
    }

    /**
     * Very basic test that checks if the send method for emails is correctly called once
     */
    @Test
    void testSendEmail() {
        mailService.sendEmail(student1, subject, content, false, true);
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    /**
     * When the javaMailSender returns an exception, that exception should be caught and an ArtemisMailException should be thrown instead.
     */
    @Test
    void testThrowException() {
        doThrow(new org.springframework.mail.MailSendException("Some error occurred")).when(javaMailSender).send(any(MimeMessage.class));
        Assertions.assertThrows(ArtemisMailException.class, () -> mailService.sendEmail(student1, subject, content, false, true));
    }
}
