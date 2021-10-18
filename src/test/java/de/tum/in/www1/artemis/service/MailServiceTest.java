package de.tum.in.www1.artemis.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring5.SpringTemplateEngine;

import tech.jhipster.config.JHipsterProperties;

public class MailServiceTest {

    @Autowired
    private static MailService mailService;

    @Mock
    private static JavaMailSender javaMailSender;

    @Mock
    private static JHipsterProperties jHipsterProperties;

    @Mock
    private static MessageSource messageSource;

    @Mock
    private static SpringTemplateEngine templateEngine;

    @Mock
    private static TimeService timeService;

    /**
     * Prepares the needed values and objects for testing
     */
    @BeforeAll
    public static void setUp() {
        mailService = new MailService(jHipsterProperties, javaMailSender, messageSource, templateEngine, timeService);

        javaMailSender = mock(JavaMailSender.class);
    }

    @Test
    public void testSendEmail() {

    }
}
