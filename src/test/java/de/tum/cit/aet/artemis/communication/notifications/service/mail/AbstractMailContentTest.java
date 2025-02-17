package de.tum.cit.aet.artemis.communication.notifications.service.mail;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.thymeleaf.spring6.SpringTemplateEngine;

import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.communication.service.notifications.MailService;
import de.tum.cit.aet.artemis.communication.service.notifications.MarkdownCustomLinkRendererService;
import de.tum.cit.aet.artemis.communication.service.notifications.MarkdownCustomReferenceRendererService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.TimeService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Abstract class for testing mail content and the correct usage of DTO attributes in the mail content.
 */
class AbstractMailContentTest extends AbstractSpringIntegrationIndependentTest {

    protected MailService mailService;

    @Mock
    protected MailSendingService mailSendingService;

    @Autowired
    protected TimeService timeService;

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    protected MessageSource messageSource;

    @Autowired
    private MarkdownCustomLinkRendererService markdownCustomLinkRendererService;

    @Autowired
    private MarkdownCustomReferenceRendererService markdownCustomReferenceRendererService;

    @BeforeEach
    void setup() {
        mailService = new MailService(messageSource, templateEngine, timeService, mailSendingService, markdownCustomLinkRendererService, markdownCustomReferenceRendererService);
    }

    /**
     * All users attempted to receive a mail should have at least a language key set.
     */
    protected User createMinimalMailRecipientUser() {
        User recipient = new User();
        recipient.setLangKey("de");
        return recipient;
    }

    protected String createExpectedSubject(User recipient, String titleMessageKey, Object... args) {
        return messageSource.getMessage(titleMessageKey, args, Locale.forLanguageTag(recipient.getLangKey()));
    }

    /**
     * Retrieve the content of the interpreted thymeleaf template, which represents the mail content.
     *
     * @param expectedSubject The expected subject of the mail
     */
    protected String getGeneratedEmailTemplateText(String expectedSubject) {
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailSendingService).sendEmail(any(), eq(expectedSubject), contentCaptor.capture(), anyBoolean(), anyBoolean());
        return contentCaptor.getValue();
    }
}
