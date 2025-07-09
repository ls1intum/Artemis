package de.tum.cit.aet.artemis.communication.notifications.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;

import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.communication.service.notifications.MailService;
import de.tum.cit.aet.artemis.core.domain.User;
import tech.jhipster.config.JHipsterProperties;

/**
 * This is a very basic testing class for the mail service
 * Because this service mostly uses other frameworks/services and loads values/variables into html templates
 * we only test that the correct send method is called
 */
class MailServiceTest {

    private MailSendingService mailSendingService;

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

    private User student1;

    private String subject;

    private String content;

    /**
     * Prepares the needed values and objects for testing
     */
    @BeforeEach
    void setUp() throws MalformedURLException, URISyntaxException {
        student1 = new User();
        student1.setLogin("student1");
        student1.setId(555L);
        student1.setEmail("benige8246@omibrown.com");
        student1.setLangKey("de");

        User student2 = new User();
        student2.setLogin("student2");
        student2.setId(556L);
        student2.setEmail("bege123@abc.com");

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

        messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(any(String.class), any(), any())).thenReturn("test");

        templateEngine = mock(SpringTemplateEngine.class);
        when(templateEngine.process(any(String.class), any())).thenReturn("test");

        mailSendingService = new MailSendingService(jHipsterProperties, javaMailSender, messageSource, templateEngine);

        MailService mailService = new MailService(messageSource, templateEngine, mailSendingService);
        ReflectionTestUtils.setField(mailService, "artemisServerUrl", new URI("http://localhost:8080").toURL());
    }

    /**
     * Very basic test that checks if the send method for emails is correctly called once
     */
    @Test
    void testSendEmail() {
        mailSendingService.sendEmail(student1, subject, content, false, true);
        verify(javaMailSender).send(any(MimeMessage.class));
    }

    /**
     * When the javaMailSender returns an exception, that exception should be caught and should not be thrown instead.
     */
    @Test
    void testNoMailSendExceptionThrown() {
        doThrow(new MailSendException("Some error occurred during mail send")).when(javaMailSender).send(any(MimeMessage.class));
        assertThatNoException().isThrownBy(() -> mailSendingService.sendEmail(student1, subject, content, false, true));
    }
}
