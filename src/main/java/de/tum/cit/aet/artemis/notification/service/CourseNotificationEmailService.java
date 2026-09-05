package de.tum.cit.aet.artemis.notification.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.spring6.SpringTemplateEngine;

import de.tum.cit.aet.artemis.notification.dto.CourseNotificationDTO;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationRecipientDTO;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.notification.service.notifications.MarkdownCustomLinkRendererService;
import de.tum.cit.aet.artemis.notification.service.notifications.MarkdownCustomReferenceRendererService;
import de.tum.cit.aet.artemis.notification.service.notifications.MarkdownCustomRendererService;
import de.tum.cit.aet.artemis.notification.service.notifications.MarkdownImageBlockRendererFactory;
import de.tum.cit.aet.artemis.notification.service.notifications.MarkdownRelativeToAbsolutePathAttributeProvider;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Service responsible for sending course notifications via email to recipients.
 *
 * <p>
 * This implementation of {@link CourseNotificationBroadcastService} handles email-based notifications
 * by generating localized email content from templates and sending them to the appropriate users.
 * Each email is processed asynchronously to improve performance when sending to multiple recipients.
 * </p>
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class CourseNotificationEmailService extends CourseNotificationBroadcastService {

    @Value("${server.url}")
    private URL artemisServerUrl;

    private final MessageSource messageSource;

    private final SpringTemplateEngine templateEngine;

    private final MailSendingService mailSendingService;

    private final List<MarkdownCustomRendererService> markdownCustomRendererServices;

    private static final Logger log = LoggerFactory.getLogger(CourseNotificationEmailService.class);

    private static final String SERVER_URL_KEY = "serverUrl";

    private static final String TYPE_KEY = "notificationType";

    private static final String RECIPIENT_KEY = "recipient";

    private static final String COURSE_ID_KEY = "courseId";

    private static final String PARAMETERS_KEY = "parameters";

    private static final String CREATION_DATE_KEY = "creationDate";

    private static final String CATEGORY_KEY = "category";

    private static final String NOTIFICATION_URL_KEY = "notificationUrl";

    // In case a parameter includes markdown, add the key here to make sure the system renders it properly.
    private static final List<String> MARKDOWN_PARAMETERS = List.of("postMarkdownContent");

    public CourseNotificationEmailService(MessageSource messageSource, SpringTemplateEngine templateEngine, MailSendingService mailSendingService,
            MarkdownCustomLinkRendererService markdownCustomLinkRendererService, MarkdownCustomReferenceRendererService markdownCustomReferenceRendererService) {
        this.messageSource = messageSource;
        this.templateEngine = templateEngine;
        this.mailSendingService = mailSendingService;
        markdownCustomRendererServices = List.of(markdownCustomLinkRendererService, markdownCustomReferenceRendererService);
    }

    /**
     * Sends course notifications via email to all recipients. The emails are sent asynchronously.
     *
     * <p>
     * This method creates a separate process for each email that needs to be sent.
     * It uses the recipient's language preference to localize the content and subject
     * of the email.
     * </p>
     *
     * @param courseNotification The notification data to be sent
     * @param recipients         The list of recipients who should receive the notification
     */
    // Runs on the "mailTaskExecutor" so all mail-sending async work goes through the same executor. In production
    // that executor delegates to the shared task executor (identical behavior to a bare @Async), while in the test
    // profile it is a SyncTaskExecutor. Running mail on the caller's thread in tests keeps the shared JavaMailSender
    // spy from being invoked by a background thread while another test stubs or resets it, which otherwise corrupts
    // Mockito's state and surfaces as a flaky UnfinishedStubbingException.
    @Async("mailTaskExecutor")
    @Override
    protected CompletableFuture<Void> sendCourseNotification(CourseNotificationDTO courseNotification, List<CourseNotificationRecipientDTO> recipients) {
        // A recipient the channel could not reach is skipped rather than aborting the whole send, which is the right
        // behaviour: the others should still get their mail. It is not a success though, and reporting the channel as
        // healthy afterwards would hide a missing locale key, a broken template or an unreachable SMTP server behind a
        // green error rate. Counts both a rendering failure and a send that reported it did not go out.
        var skippedRecipients = new AtomicInteger();
        recipients.forEach(recipient -> {
            String localeKey = recipient.langKey();
            if (localeKey == null) {
                localeKey = "en";
            }
            Locale locale = Locale.forLanguageTag(localeKey);
            Context context = new Context(locale);
            context.setVariable(SERVER_URL_KEY, artemisServerUrl);
            context.setVariable(TYPE_KEY, courseNotification.notificationType());
            context.setVariable(RECIPIENT_KEY, recipient);
            context.setVariable(COURSE_ID_KEY, courseNotification.courseId());
            // The template reads the values by name, so the payload is flattened for the context. The values are
            // typed on the way in, which is what the payload record is for.
            Map<String, Object> values = CourseNotificationPayloads.asMap(courseNotification.payload());
            values.put("courseTitle", courseNotification.courseTitle());
            values.put("courseIconUrl", courseNotification.courseIconUrl());

            var renderedParameters = new HashMap<>();

            for (var entry : values.entrySet()) {
                Object value = entry.getValue();
                if (value != null && MARKDOWN_PARAMETERS.contains(entry.getKey())) {
                    value = renderMarkdown(value.toString());
                }
                renderedParameters.put(entry.getKey(), value);
            }

            context.setVariable(PARAMETERS_KEY, renderedParameters);
            context.setVariable(CREATION_DATE_KEY, courseNotification.creationDate());
            context.setVariable(CATEGORY_KEY, courseNotification.category());
            context.setVariable(NOTIFICATION_URL_KEY, artemisServerUrl.toString() + courseNotification.relativeWebAppUrl());
            String subject;
            String content;
            try {
                subject = messageSource.getMessage(getLocalePrefix(courseNotification) + ".title", null, context.getLocale());
            }
            catch (NoSuchMessageException e) {
                log.error("Subject for e-mail could not be generated. Make sure to create the locale key {}.title.", getLocalePrefix(courseNotification), e);
                skippedRecipients.incrementAndGet();
                return;
            }
            try {
                content = templateEngine.process(getMailTemplateDirectory(courseNotification), context);
            }
            catch (TemplateProcessingException e) {
                log.error("Content for e-mail could not be generated. Make sure to create the template file {}.", getMailTemplateDirectory(courseNotification), e);
                skippedRecipients.incrementAndGet();
                return;
            }

            var mailRecipient = new MailRecipientDTO(recipient.email(), recipient.langKey(), recipient.login(), recipient.firstName(), recipient.lastName(), null, null);
            if (!mailSendingService.sendEmailSync(mailRecipient, subject, content, false, true)) {
                // Mail not configured for this deployment, or the message could not be built or sent. Either way this
                // recipient did not get their notification, which is what the channel's error rate has to reflect.
                skippedRecipients.incrementAndGet();
            }
        });
        if (skippedRecipients.get() > 0) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Could not render the notification e-mail for " + skippedRecipients.get() + " of " + recipients.size() + " recipients"));
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Renders the markdown for a given string and sanitizes it using Jsoup
     *
     * @param preRenderMarkdown The un-rendered markdown string
     * @return A rendered markdown string
     */
    private String renderMarkdown(String preRenderMarkdown) {
        Parser parser = Parser.builder().build();
        // A single newline is a soft break in Markdown, which CommonMark renders as a plain newline and every mail
        // client then collapses into a space, so an announcement arrives with its lines merged. The web client keeps
        // such a break visible, so rendering it as <br> is what makes the e-mail read the way its author wrote it.
        // The tag survives the sanitization below because the safelist allows it.
        HtmlRenderer renderer = HtmlRenderer.builder().softbreak("<br>")
                .attributeProviderFactory(attributeContext -> new MarkdownRelativeToAbsolutePathAttributeProvider(artemisServerUrl.toString()))
                .nodeRendererFactory(new MarkdownImageBlockRendererFactory(artemisServerUrl.toString())).build();
        String renderedHtml = renderer.render(parser.parse(preRenderMarkdown));

        renderedHtml = markdownCustomRendererServices.stream().reduce(renderedHtml, (s, service) -> service.render(s), (s1, s2) -> s2);

        Safelist restrictedList = new Safelist().addTags("p", "br", "b", "i", "em", "strong", "a", "code", "pre", "img").addAttributes(":all", "class")
                .addAttributes("img", "align", "alt", "height", "width", "src").addProtocols("img", "src", "http", "https").addAttributes("a", "href")
                .addProtocols("a", "href", "http", "https");

        return Jsoup.clean(renderedHtml, restrictedList);
    }

    /**
     * Gets the locale prefix for the given notification type.
     *
     * @param courseNotification The notification to get the locale prefix for
     * @return The locale prefix string for message lookups
     */
    private String getLocalePrefix(CourseNotificationDTO courseNotification) {
        return "email.courseNotification." + courseNotification.notificationType();
    }

    /**
     * Gets the mail template directory path for the given notification type.
     *
     * @param courseNotification The notification to get the template directory for
     * @return The path to the email template file
     */
    private String getMailTemplateDirectory(CourseNotificationDTO courseNotification) {
        return "mail/course_notification/" + courseNotification.notificationType();
    }
}
