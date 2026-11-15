package de.tum.cit.aet.artemis.notification.service.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.stereotype.Service;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.domain.DataExport;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageDigestDTO;
import de.tum.cit.aet.artemis.admin.repository.DataExportRepository;
import de.tum.cit.aet.artemis.admin.service.FeatureUsageDigestScheduleService;
import de.tum.cit.aet.artemis.admin.service.FeatureUsageDigestService;
import de.tum.cit.aet.artemis.admin.service.export.DataExportCommunicationDataService;
import de.tum.cit.aet.artemis.admin.service.export.DataExportCompetencyProgressService;
import de.tum.cit.aet.artemis.admin.service.export.DataExportCreationService;
import de.tum.cit.aet.artemis.admin.service.export.DataExportExamCreationService;
import de.tum.cit.aet.artemis.admin.service.export.DataExportExerciseCreationService;
import de.tum.cit.aet.artemis.admin.service.export.DataExportIrisService;
import de.tum.cit.aet.artemis.admin.service.export.DataExportLearnerProfileService;
import de.tum.cit.aet.artemis.admin.service.export.DataExportPresentationAssessmentService;
import de.tum.cit.aet.artemis.admin.service.export.DataExportScienceEventService;
import de.tum.cit.aet.artemis.admin.service.export.DataExportTutorialGroupService;
import de.tum.cit.aet.artemis.core.config.ArtemisProperties;
import de.tum.cit.aet.artemis.core.config.FeatureUsageProperties;
import de.tum.cit.aet.artemis.core.config.LoggingAspect;
import de.tum.cit.aet.artemis.core.dto.PasswordResetKeyDTO;
import de.tum.cit.aet.artemis.core.exception.EmailFailedException;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.core.service.ZipFileService;
import de.tum.cit.aet.artemis.iris.config.IrisDashboardProperties;
import de.tum.cit.aet.artemis.iris.service.IrisDashboardEmailService;
import de.tum.cit.aet.artemis.notification.config.MailDeliveryProperties;
import de.tum.cit.aet.artemis.notification.dto.DataExportEmailDTO;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.TestAccountEmailService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@Isolated("Mutates logger levels and appenders")
@Execution(ExecutionMode.SAME_THREAD)
class MailLoggingSafetyTest {

    private static final String EMAIL = "private-recipient@example.org";

    private static final String LOGIN = "private-recipient-login";

    private static final String NAME = "Private Recipient";

    private static final String SUBJECT = "Data export for " + LOGIN;

    private static final String CONTENT = "Mail body for " + EMAIL;

    private static final MailRecipientDTO RECIPIENT = new MailRecipientDTO(EMAIL, "en", LOGIN, NAME, "Private Last Name", "private-activation-key",
            new PasswordResetKeyDTO("reset-key-id", "private-reset-secret"));

    private final List<LoggerState> loggerStates = new ArrayList<>();

    private final List<ListAppender<ILoggingEvent>> appenders = new ArrayList<>();

    private JavaMailSender sender;

    private SpringTemplateEngine templateEngine;

    private MessageSource messageSource;

    private MailSendingService sendingService;

    private MailService mailService;

    private MailRetryQueueService queue;

    private MailDeliveryProperties deliveryProperties;

    @BeforeEach
    void setUp() throws Exception {
        capture(MailService.class);
        capture(MailSendingService.class);
        capture(MailRetryQueueService.class);
        capture(LoggingAspect.class);
        deliveryProperties = new MailDeliveryProperties();
        queue = new MailRetryQueueService(deliveryProperties, new SimpleMeterRegistry());
        sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
        var properties = new ArtemisProperties();
        properties.getMail().setFrom("artemis@example.org");
        messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn(SUBJECT);
        templateEngine = mock(SpringTemplateEngine.class);
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn(CONTENT);
        sendingService = proxied(new MailSendingService(properties, sender, messageSource, templateEngine, proxied(queue), mock(TestAccountEmailService.class)));
        ReflectionTestUtils.setField(sendingService, "artemisServerUrl", URI.create("https://artemis.example.org").toURL());
        mailService = proxied(new MailService(messageSource, templateEngine, sendingService));
    }

    @AfterEach
    void restoreLoggers() {
        loggerStates.forEach(LoggerState::restore);
    }

    static Stream<Consumer<MailService>> templateMails() {
        var export = new DataExportEmailDTO(42L, LOGIN);
        return Stream.of(service -> service.sendActivationEmail(RECIPIENT), service -> service.sendPasswordResetMail(RECIPIENT),
                service -> service.sendDataExportFailedEmailToAdmin(RECIPIENT, export, new IllegalStateException(EMAIL)),
                service -> service.sendDataExportEmailFailedEmailToAdmin(RECIPIENT, export, new IllegalStateException(EMAIL)),
                service -> service.sendDataExportCreatedEmail(RECIPIENT, export), service -> service.sendSuccessfulDataExportsEmailToAdmin(RECIPIENT, Set.of(export)),
                service -> service.sendBuildAgentSelfPausedEmailToAdmin(RECIPIENT, "build-agent", 3),
                service -> service.sendVulnerabilityScanResultEmail(RECIPIENT, null, null, false), service -> service.sendFeatureUsageDigestEmail(RECIPIENT, null),
                service -> service.sendIrisDashboardDigestEmail(RECIPIENT, null), service -> service.sendIrisDashboardAlertEmail(RECIPIENT, null));
    }

    @ParameterizedTest
    @MethodSource("templateMails")
    void sendingTemplateMailDoesNotLogPersonalData(Consumer<MailService> send) {
        send.accept(mailService);

        assertPrivateDataAbsent();
        assertThat(loggedText()).contains("Sent email");
    }

    @ParameterizedTest
    @MethodSource("smtpReplyCodes")
    void smtpFailuresKeepDiagnosticCategoryWithoutLoggingServerReplyOrThrowable(int replyCode) {
        var smtpFailure = new SMTPSendFailedException("RCPT TO:<" + EMAIL + ">", replyCode, replyCode + " mailbox " + EMAIL + " for " + LOGIN + " unavailable", null, null, null,
                null);
        doThrow(new MailSendException(Map.of(new Object(), smtpFailure))).when(sender).send(any(MimeMessage.class));

        sendingService.sendEmailSync(RECIPIENT, SUBJECT, CONTENT, false, true, MailPriority.TRANSACTIONAL);

        assertPrivateDataAbsent();
        assertThat(loggedText()).contains(replyCode == 550 ? "PERMANENT" : "TRANSIENT");
    }

    static Stream<Integer> smtpReplyCodes() {
        return Stream.of(451, 550);
    }

    @Test
    void retryDeliveryDoesNotLogRenderedSubject() {
        doThrow(new MailSendException(Map.of(new Object(), new SMTPSendFailedException("DATA", 451, "451 " + EMAIL, null, null, null, null)))).when(sender)
                .send(any(MimeMessage.class));
        sendingService.sendEmailSync(RECIPIENT, SUBJECT, CONTENT, false, true, MailPriority.TRANSACTIONAL);
        doNothing().when(sender).send(any(MimeMessage.class));

        sendingService.sendQueuedMails(Instant.now().plusSeconds(90));

        assertPrivateDataAbsent();
        assertThat(loggedText()).contains("after 1 failed attempts");
    }

    @Test
    void connectionCloseFailureDoesNotLogServerReply() {
        doThrow(new MailSendException("Closing connection for " + EMAIL, new IllegalStateException(LOGIN))).when(sender).send(any(MimeMessage.class));

        assertThat(sendingService.sendEmailSync(RECIPIENT, SUBJECT, CONTENT, false, true)).isTrue();

        assertPrivateDataAbsent();
        assertThat(loggedText()).contains("closing the connection");
    }

    @Test
    void failedTemplateDoesNotLogPersonalDataInException() {
        when(templateEngine.process(anyString(), any(Context.class))).thenThrow(new TemplateProcessingException("Cannot render " + EMAIL, new IllegalStateException(LOGIN)));

        assertThat(sendingService.buildAndSendSyncReporting(RECIPIENT, "email.subject", List.of(), "mail/template", Map.of())).isFalse();

        assertPrivateDataAbsent();
        assertThat(loggedText()).contains("TemplateProcessingException");
    }

    @Test
    void templateFailureInFacadeDoesNotLeakThroughExceptionAspect() {
        when(templateEngine.process(anyString(), any(Context.class))).thenThrow(new TemplateProcessingException("Cannot render " + EMAIL, new IllegalStateException(LOGIN)));

        assertThatThrownBy(() -> mailService.sendActivationEmail(RECIPIENT)).isInstanceOf(MailPreparationException.class).hasNoCause();

        assertPrivateDataAbsent();
        assertThat(loggedText()).contains("sendActivationEmail", "TemplateProcessingException");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void mailPreparationFailureDoesNotLeakThroughCallingService(boolean subjectFails) {
        var caller = proxied(new OrdinaryMailCallerService(mailService));
        if (subjectFails) {
            when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenThrow(new NoSuchMessageException("Subject for " + EMAIL + " " + LOGIN));
        }
        else {
            when(templateEngine.process(anyString(), any(Context.class))).thenThrow(new TemplateProcessingException("Cannot render " + EMAIL, new IllegalStateException(LOGIN)));
        }

        Throwable failure = catchThrowable(caller::sendPasswordResetMail);

        assertPrivateDataAbsent();
        assertThat(failure).isInstanceOf(MailPreparationException.class).hasNoCause();
        assertThat(loggedText()).contains("OrdinaryMailCallerService.sendPasswordResetMail", "MailPreparationException");
    }

    @Test
    void fullAndExpiredRetryQueueDoesNotLogRecipientOrRenderedSubject() {
        deliveryProperties.getRetry().setMaxQueueSize(1);
        var proxiedQueue = proxied(queue);
        Instant now = Instant.now();
        var first = proxiedQueue.newPendingMail(EMAIL, SUBJECT, CONTENT, false, true, MailPriority.BULK, now);
        var second = proxiedQueue.newPendingMail(EMAIL, SUBJECT, CONTENT, false, true, MailPriority.TRANSACTIONAL, now);
        assertThat(proxiedQueue.offer(first, now)).isTrue();
        assertThat(proxiedQueue.offer(second, now)).isTrue();
        proxiedQueue.purgeExpired(now.plus(deliveryProperties.getRetry().getTransactionalMaxAge()).plusSeconds(1));

        assertPrivateDataAbsent();
        assertThat(loggedText()).contains("queue is full", "too old");
    }

    @Test
    void rejectedDigestDoesNotLogRecipientInTheActualCaller() {
        capture(FeatureUsageDigestScheduleService.class);
        var digestService = mock(FeatureUsageDigestService.class);
        when(digestService.buildWeeklyDigest()).thenReturn(mock(FeatureUsageDigestDTO.class));
        var properties = new FeatureUsageProperties(true, 400, new FeatureUsageProperties.Digest(true, List.of(EMAIL)));
        var caller = proxied(new FeatureUsageDigestScheduleService(digestService, properties, mailService, mock(ProfileService.class), Runnable::run));
        doThrow(new MailSendException(Map.of(new Object(), new SMTPSendFailedException("DATA", 550, "550 " + EMAIL, null, null, null, null)))).when(sender)
                .send(any(MimeMessage.class));

        assertThat(caller.sendDigestEmail()).isFalse();

        assertPrivateDataAbsent();
        assertThat(loggedText()).contains("could not be sent", "PERMANENT");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void failedIrisTemplateDoesNotLogRecipientInTheActualCaller(boolean alert) {
        capture(IrisDashboardEmailService.class);
        var properties = new IrisDashboardProperties();
        properties.getDigest().setRecipients(List.of(EMAIL));
        var caller = proxied(new IrisDashboardEmailService(mailService, sendingService, properties));
        caller.init();
        when(templateEngine.process(anyString(), any(Context.class))).thenThrow(new TemplateProcessingException("Cannot render " + EMAIL, new IllegalStateException(LOGIN)));

        assertThat(alert ? caller.sendAlert(null) : caller.sendDigest(null)).isZero();

        assertPrivateDataAbsent();
        assertThat(loggedText()).contains("Failed to send Iris", "MailPreparationException");
    }

    @Test
    void rejectedIrisRecipientsDoNotLogAddressesDuringInitialization() {
        capture(IrisDashboardEmailService.class);
        var properties = new IrisDashboardProperties();
        properties.getDigest().setRecipients(List.of(NAME + " <" + EMAIL + ">", EMAIL + " invalid"));
        var caller = proxied(new IrisDashboardEmailService(mailService, sendingService, properties));

        caller.init();

        assertThat(caller.canSendDigest()).isFalse();
        assertPrivateDataAbsent();
        assertThat(loggedText()).contains("recipient rejected", "display-name form", "invalid address");
    }

    @Test
    void failedExportTemplateDoesNotReintroduceRecipientIntoLogsOrException() throws Exception {
        capture(DataExportCreationService.class);
        var repository = mock(DataExportRepository.class);
        when(repository.save(any(DataExport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var caller = new DataExportCreationService(Path.of("."), mock(ZipFileService.class), mock(FileService.class), repository, mailService,
                mock(DataExportExerciseCreationService.class), mock(DataExportExamCreationService.class), mock(DataExportCommunicationDataService.class),
                mock(DataExportScienceEventService.class), mock(DataExportIrisService.class), mock(DataExportLearnerProfileService.class),
                mock(DataExportCompetencyProgressService.class), mock(DataExportTutorialGroupService.class), mock(DataExportPresentationAssessmentService.class),
                mock(ResourceLoaderService.class), mock(TempFileUtilService.class));
        var user = new User();
        user.setLogin(LOGIN);
        user.setEmail(EMAIL);
        user.setLangKey("en");
        var export = new DataExport();
        export.setId(42L);
        export.setUser(user);
        when(templateEngine.process(anyString(), any(Context.class))).thenThrow(new TemplateProcessingException("Cannot render " + EMAIL, new IllegalStateException(LOGIN)));
        // Isolate the completed export's notification phase from archive creation and its unrelated filesystem work.
        var completion = DataExportCreationService.class.getDeclaredMethod("finishDataExportCreation", DataExport.class, Path.class);
        completion.setAccessible(true);

        Throwable failure = catchThrowable(() -> completion.invoke(caller, export, Path.of("data-export.zip")));

        assertPrivateDataAbsent();
        assertThat(failure).cause().isInstanceOf(EmailFailedException.class).hasMessageNotContaining(EMAIL).hasMessageNotContaining(LOGIN);
        assertThat(loggedText()).contains("Failed to send data export created email", "MailPreparationException");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void realTemplateFailureDoesNotLogRecipientBeforeTheSafeMailDiagnostic(boolean facade) throws Exception {
        capture(TemplateEngine.class);
        SpringTemplateEngine engine = failingTemplateEngine();
        ReflectionTestUtils.setField(mailService, "templateEngine", engine);
        ReflectionTestUtils.setField(sendingService, "templateEngine", engine);

        withProductionLogFilters(() -> {
            if (facade) {
                assertThatThrownBy(() -> mailService.sendEmailFromTemplate(RECIPIENT, "mail/mailLoggingFailure", "email.subject")).isInstanceOf(MailPreparationException.class)
                        .hasNoCause();
            }
            else {
                assertThat(sendingService.buildAndSendSyncReporting(RECIPIENT, "email.subject", List.of(), "mail/mailLoggingFailure", Map.of())).isFalse();
            }
        });

        assertPrivateDataAbsent();
        assertThat(loggedText()).contains("TemplateProcessingException");
    }

    @Test
    void realTemplateFailureOutsideMailRetainsItsDiagnostics() throws Exception {
        capture(TemplateEngine.class);
        SpringTemplateEngine engine = failingTemplateEngine();
        var context = new Context(Locale.ENGLISH);
        context.setVariable("user", MailRecipientDTO.forUnnamed("synthetic-unparseable-value", "en", "synthetic-login"));

        withProductionLogFilters(() -> assertThatThrownBy(() -> engine.process("diagnostic/non-mail", context)).isInstanceOf(TemplateProcessingException.class));

        assertThat(loggedText()).contains("diagnostic/non-mail", "synthetic-unparseable-value", "NumberFormatException");
    }

    private static SpringTemplateEngine failingTemplateEngine() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateAliases(Map.of("diagnostic/non-mail", "mail/mailLoggingFailure"));
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static void withProductionLogFilters(Runnable operation) throws Exception {
        var configuredContext = new LoggerContext();
        var configurator = new JoranConfigurator();
        configurator.setContext(configuredContext);
        configurator.doConfigure(Path.of("src/main/resources/logback-spring.xml").toFile());
        var activeContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        var previousFilters = new ArrayList<>(activeContext.getTurboFilterList());
        activeContext.getTurboFilterList().clear();
        configuredContext.getTurboFilterList().forEach(activeContext::addTurboFilter);
        try {
            operation.run();
        }
        finally {
            activeContext.getTurboFilterList().clear();
            previousFilters.forEach(activeContext::addTurboFilter);
            configuredContext.stop();
        }
    }

    private void assertPrivateDataAbsent() {
        assertThat(loggedText()).doesNotContain(EMAIL, LOGIN, NAME, "Private Last Name", "private-activation-key", "private-reset-secret", CONTENT);
    }

    private String loggedText() {
        return appenders.stream().flatMap(appender -> appender.list.stream())
                .map(event -> event.getFormattedMessage() + (event.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy())))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private void capture(Class<?> type) {
        Logger logger = (Logger) LoggerFactory.getLogger(type);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        loggerStates.add(new LoggerState(logger, logger.getLevel(), logger.isAdditive(), appender));
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        logger.addAppender(appender);
        appenders.add(appender);
    }

    private static <T> T proxied(T target) {
        var factory = new AspectJProxyFactory(target);
        factory.addAspect(new LoggingAspect(new MockEnvironment().withProperty("spring.profiles.active", "dev")));
        @SuppressWarnings("unchecked")
        T proxy = (T) factory.getProxy();
        return proxy;
    }

    @Service
    @Lazy
    @Profile("mail-logging-safety-test")
    static class OrdinaryMailCallerService {

        private final MailService mailService;

        OrdinaryMailCallerService(MailService mailService) {
            this.mailService = mailService;
        }

        public void sendPasswordResetMail() {
            mailService.sendPasswordResetMail(RECIPIENT);
        }
    }

    private record LoggerState(Logger logger, Level level, boolean additive, ListAppender<ILoggingEvent> appender) {

        private void restore() {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(level);
            logger.setAdditive(additive);
        }
    }
}
