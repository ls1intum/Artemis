package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.config.ArtemisProperties;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.notification.domain.GlobalNotificationSetting;
import de.tum.cit.aet.artemis.notification.domain.GlobalNotificationType;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.repository.GlobalNotificationSettingRepository;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

@Execution(ExecutionMode.SAME_THREAD)
class MavenCentralRateLimitNotificationServiceIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "mavenratelimit";

    private static final String TEMPLATE = "mail/notification/mavenCentralRateLimitEmail";

    private static final String SUBJECT_KEY = "email.notification.mavenCentralRateLimit.title";

    private static final int EMAIL_TIMEOUT_MS = 5000;

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP.dynamicPort());

    @Autowired
    private MavenCentralRateLimitNotificationService mavenCentralRateLimitNotificationService;

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private GlobalNotificationSettingRepository globalNotificationSettingRepository;

    private ProgrammingExercise exercise;

    private GlobalNotificationSetting optOutSetting;

    @BeforeEach
    void setUp() {
        greenMail.reset();
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 2);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        course.setInstructorGroupName(TEST_PREFIX + "instructor");
        courseRepository.save(course);
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        doReturn(true).when(mailSendingService).isMailConfigured();
    }

    @Test
    void shouldNotifyEachInstructorOnceAndDeduplicateSubsequentDetections() {
        mavenCentralRateLimitNotificationService.notifyInstructorsIfBuildWasRateLimited(exercise.getId(), exercise.getProgrammingLanguage(), rateLimitedBuildLogs());
        // A second failing build of the same exercise within the notification interval must not trigger additional emails
        mavenCentralRateLimitNotificationService.notifyInstructorsIfBuildWasRateLimited(exercise.getId(), exercise.getProgrammingLanguage(), rateLimitedBuildLogs());

        var recipientCaptor = ArgumentCaptor.forClass(MailRecipientDTO.class);
        verify(mailSendingService, timeout(2000).times(2)).buildAndSendAsync(recipientCaptor.capture(), eq(SUBJECT_KEY), eq(List.of(exercise.getTitle())), eq(TEMPLATE), anyMap());
        assertThat(recipientCaptor.getAllValues()).extracting(MailRecipientDTO::login).containsExactlyInAnyOrder(TEST_PREFIX + "instructor1", TEST_PREFIX + "instructor2");
    }

    @AfterEach
    void tearDown() {
        if (optOutSetting != null) {
            globalNotificationSettingRepository.delete(optOutSetting);
            optOutSetting = null;
        }
    }

    @Test
    void shouldNotNotifyInstructorsWhoOptedOut() {
        var optedOutInstructor = userTestRepository.findOneWithGroupsByLogin(TEST_PREFIX + "instructor1").orElseThrow();
        optOutSetting = new GlobalNotificationSetting();
        optOutSetting.setUserId(optedOutInstructor.getId());
        optOutSetting.setNotificationType(GlobalNotificationType.MAVEN_CENTRAL_RATE_LIMIT);
        optOutSetting.setEnabled(false);
        optOutSetting = globalNotificationSettingRepository.save(optOutSetting);

        mavenCentralRateLimitNotificationService.notifyInstructorsIfBuildWasRateLimited(exercise.getId(), exercise.getProgrammingLanguage(), rateLimitedBuildLogs());

        var recipientCaptor = ArgumentCaptor.forClass(MailRecipientDTO.class);
        verify(mailSendingService, timeout(2000).times(1)).buildAndSendAsync(recipientCaptor.capture(), eq(SUBJECT_KEY), eq(List.of(exercise.getTitle())), eq(TEMPLATE), anyMap());
        assertThat(recipientCaptor.getValue().login()).isEqualTo(TEST_PREFIX + "instructor2");
    }

    @Test
    void shouldNotifyInstructorsForExamExercises() {
        var examExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise("Maven Rate Limit Exam Exercise", "MVN429EXAM", false);
        var examCourse = examExercise.getCourseViaExerciseGroupOrCourseMember();
        examCourse.setInstructorGroupName(TEST_PREFIX + "instructor");
        courseRepository.save(examCourse);

        mavenCentralRateLimitNotificationService.notifyInstructorsIfBuildWasRateLimited(examExercise.getId(), examExercise.getProgrammingLanguage(), rateLimitedBuildLogs());

        verify(mailSendingService, timeout(2000).times(2)).buildAndSendAsync(any(MailRecipientDTO.class), eq(SUBJECT_KEY), eq(List.of(examExercise.getTitle())), eq(TEMPLATE),
                argThat(context -> "/course-management/%d/exams/%d/exercise-groups/%d/programming-exercises/%d/code-editor/TESTS/test"
                        .formatted(examCourse.getId(), examExercise.getExerciseGroup().getExam().getId(), examExercise.getExerciseGroup().getId(), examExercise.getId())
                        .equals(context.get("editorPath"))));
    }

    @Test
    void shouldNotifyInstructorsForMavenErrorFormat() {
        // Maven (Maven Resolver) reports rate limiting with a different wording than Gradle
        var buildLogs = List.of("[ERROR] Failed to execute goal on project test: Could not resolve dependencies for project de.tum.in.ase:test:jar:1.0: "
                + "Could not transfer artifact de.tum.in.ase:artemis-java-test-sandbox:pom:1.11.3 from/to central (https://repo.maven.apache.org/maven2): "
                + "status code: 429, reason phrase: Too Many Requests (429)");

        mavenCentralRateLimitNotificationService.notifyInstructorsIfBuildWasRateLimited(exercise.getId(), exercise.getProgrammingLanguage(), buildLogs);

        verify(mailSendingService, timeout(2000).times(2)).buildAndSendAsync(any(MailRecipientDTO.class), eq(SUBJECT_KEY), eq(List.of(exercise.getTitle())), eq(TEMPLATE),
                anyMap());
    }

    @Test
    void shouldNotNotifyWhenBuildLogsDoNotContainRateLimitError() {
        var buildLogs = List.of("[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.13.0:compile: Compilation failure");

        mavenCentralRateLimitNotificationService.notifyInstructorsIfBuildWasRateLimited(exercise.getId(), exercise.getProgrammingLanguage(), buildLogs);

        verify(mailSendingService, after(1000).never()).buildAndSendAsync(any(), anyString(), anyList(), eq(TEMPLATE), anyMap());
    }

    @Test
    void shouldNotNotifyWhenRateLimitErrorIsNotRelatedToMaven() {
        var buildLogs = List.of("Could not GET 'https://registry.example.com/some/package'. Received status code 429 from server: Too Many Requests");

        mavenCentralRateLimitNotificationService.notifyInstructorsIfBuildWasRateLimited(exercise.getId(), exercise.getProgrammingLanguage(), buildLogs);

        verify(mailSendingService, after(1000).never()).buildAndSendAsync(any(), anyString(), anyList(), eq(TEMPLATE), anyMap());
    }

    @Test
    void shouldNotNotifyForRateLimitFromPrivateMavenRegistry() {
        var buildLogs = List.of("Downloaded from central: https://repo.maven.apache.org/maven2/com/example/other/1.0/other-1.0.pom",
                "Could not GET 'https://gitlab.example.com/api/v4/projects/42/packages/maven/com/example/library/1.0/library-1.0.pom'. Received status code 429 from server: Too Many Requests");

        mavenCentralRateLimitNotificationService.notifyInstructorsIfBuildWasRateLimited(exercise.getId(), exercise.getProgrammingLanguage(), buildLogs);

        verify(mailSendingService, after(1000).never()).buildAndSendAsync(any(), anyString(), anyList(), eq(TEMPLATE), anyMap());
    }

    @Test
    void shouldNotNotifyForOtherProgrammingLanguages() {
        mavenCentralRateLimitNotificationService.notifyInstructorsIfBuildWasRateLimited(exercise.getId(), ProgrammingLanguage.PYTHON, rateLimitedBuildLogs());

        verify(mailSendingService, after(1000).never()).buildAndSendAsync(any(), anyString(), anyList(), eq(TEMPLATE), anyMap());
    }

    @Test
    void shouldRenderEmailWithDocumentationAndEditorLinks() throws Exception {
        var testMailService = createGreenMailSendingService();

        testMailService.buildAndSendSync(MailRecipientDTO.from(createRecipient("en")), SUBJECT_KEY, TEMPLATE,
                Map.of("exerciseTitle", "My Java Exercise", "courseTitle", "My Course", "editorPath", "/course-management/7/programming-exercises/42/code-editor/TESTS/test",
                        "documentationUrl", MavenCentralRateLimitNotificationService.DOCUMENTATION_URL));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("My Java Exercise");
        assertThat(body).contains("My Course");
        assertThat(body).contains("http://localhost:9000/course-management/7/programming-exercises/42/code-editor/TESTS/test");
        assertThat(body).contains(MavenCentralRateLimitNotificationService.DOCUMENTATION_URL);
        assertThat(body).contains("Maven Central");
    }

    @Test
    void shouldRenderEmailWithExamEditorLink() throws Exception {
        var testMailService = createGreenMailSendingService();
        var examEditorPath = "/course-management/7/exams/8/exercise-groups/9/programming-exercises/42/code-editor/TESTS/test";

        testMailService.buildAndSendSync(MailRecipientDTO.from(createRecipient("en")), SUBJECT_KEY, TEMPLATE, Map.of("exerciseTitle", "My Exam Exercise", "courseTitle",
                "My Course", "editorPath", examEditorPath, "documentationUrl", MavenCentralRateLimitNotificationService.DOCUMENTATION_URL));

        assertThat(getDeliveredEmailBody()).contains("http://localhost:9000" + examEditorPath);
    }

    @Test
    void shouldRenderEmailInGerman() throws Exception {
        var testMailService = createGreenMailSendingService();

        testMailService.buildAndSendSync(MailRecipientDTO.from(createRecipient("de")), SUBJECT_KEY, TEMPLATE,
                Map.of("exerciseTitle", "Meine Java-Aufgabe", "courseTitle", "Mein Kurs", "editorPath", "/course-management/7/programming-exercises/42/code-editor/TESTS/test",
                        "documentationUrl", MavenCentralRateLimitNotificationService.DOCUMENTATION_URL));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Meine Java-Aufgabe");
        assertThat(body).contains("Maven-Repository-Mirror");
    }

    private static List<String> rateLimitedBuildLogs() {
        return List.of("> Task :compileJava FAILED",
                "Could not GET 'https://repo.maven.apache.org/maven2/de/tum/in/ase/artemis-java-test-sandbox/1.11.3/artemis-java-test-sandbox-1.11.3.pom'. Received status code 429 from server: Too Many Requests");
    }

    private static User createRecipient(String langKey) {
        var recipient = new User();
        recipient.setEmail("instructor@greenmail.test");
        recipient.setLangKey(langKey);
        recipient.setFirstName("John");
        recipient.setLastName("Instructor");
        return recipient;
    }

    /**
     * Creates a {@link MailSendingService} that actually delivers emails to the in-process GreenMail SMTP server, so the rendered template content can be asserted.
     */
    private MailSendingService createGreenMailSendingService() throws Exception {
        var greenMailSender = new JavaMailSenderImpl();
        greenMailSender.setHost("127.0.0.1");
        greenMailSender.setPort(greenMail.getSmtp().getPort());

        var mainMessageSource = new ReloadableResourceBundleMessageSource();
        mainMessageSource.setBasename("file:src/main/resources/i18n/messages");
        mainMessageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());

        var testTemplateEngine = new SpringTemplateEngine();
        templateEngine.getTemplateResolvers().forEach(testTemplateEngine::addTemplateResolver);
        testTemplateEngine.setMessageSource(mainMessageSource);

        var mailEnabledProperties = new ArtemisProperties();
        mailEnabledProperties.getMail().setFrom("test@greenmail.test");

        var testMailService = new MailSendingService(mailEnabledProperties, greenMailSender, mainMessageSource, testTemplateEngine);
        ReflectionTestUtils.setField(testMailService, "artemisServerUrl", URI.create("http://localhost:9000").toURL());
        return testMailService;
    }

    private String getDeliveredEmailBody() throws Exception {
        assertThat(greenMail.waitForIncomingEmail(EMAIL_TIMEOUT_MS, 1)).isTrue();
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(1);
        return messages[0].getContent().toString();
    }
}
