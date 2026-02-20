package de.tum.cit.aet.artemis.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;

import de.tum.cit.aet.artemis.communication.domain.GlobalNotificationSetting;
import de.tum.cit.aet.artemis.communication.domain.GlobalNotificationType;
import de.tum.cit.aet.artemis.communication.repository.GlobalNotificationSettingRepository;
import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import tech.jhipster.config.JHipsterProperties;

/**
 * Integration tests that verify maintenance email notifications are delivered with correct content
 * and that the JPQL recipient queries return correct results.
 * Uses GreenMail (in-process SMTP server) to intercept and inspect emails sent by the application.
 */
@Execution(ExecutionMode.SAME_THREAD)
class MaintenanceEmailIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final int EMAIL_TIMEOUT_MS = 5000;

    private static final String TEST_PREFIX = "maintemailtest";

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP.dynamicPort());

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Autowired
    private GlobalNotificationSettingRepository globalNotificationSettingRepository;

    private MailSendingService testMailService;

    private User recipient;

    @BeforeEach
    void setUp() throws Exception {
        greenMail.reset();

        var greenMailSender = new JavaMailSenderImpl();
        greenMailSender.setHost("127.0.0.1");
        greenMailSender.setPort(greenMail.getSmtp().getPort());

        var mainMessageSource = new ReloadableResourceBundleMessageSource();
        mainMessageSource.setBasename("file:src/main/resources/i18n/messages");
        mainMessageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());

        var testTemplateEngine = new SpringTemplateEngine();
        templateEngine.getTemplateResolvers().forEach(testTemplateEngine::addTemplateResolver);
        testTemplateEngine.setMessageSource(mainMessageSource);

        var mailEnabledProperties = new JHipsterProperties();
        mailEnabledProperties.getMail().setFrom("test@greenmail.test");

        testMailService = new MailSendingService(mailEnabledProperties, greenMailSender, mainMessageSource, testTemplateEngine);
        ReflectionTestUtils.setField(testMailService, "artemisServerUrl", new URL("http://localhost:9000"));

        recipient = new User();
        recipient.setEmail("instructor@greenmail.test");
        recipient.setLangKey("en");
        recipient.setFirstName("John");
        recipient.setLastName("Instructor");

        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 2);
    }

    @AfterEach
    void tearDown() {
        // Clean up entities created during tests to ensure isolation
        globalNotificationSettingRepository.deleteAll();
        courseRepository.findAll().stream().filter(c -> c.getInstructorGroupName() != null && c.getInstructorGroupName().startsWith(TEST_PREFIX)).forEach(courseRepository::delete);
    }

    // ---- Template rendering tests ----

    @Test
    void maintenanceEmailTemplate_shouldRenderAllFieldsAndDeliver() throws Exception {
        var notificationDate = ZonedDateTime.now();
        var expireDate = ZonedDateTime.now().plusHours(4);

        testMailService.buildAndSendSync(recipient, "email.notification.maintenance.title", "mail/notification/maintenanceEmail", Map.of("notificationTitle", "Server Update v2.0",
                "notificationText", "We will be updating the server to the latest version.", "notificationDate", notificationDate, "expireDate", expireDate));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Server Update v2.0");
        assertThat(body).contains("We will be updating the server to the latest version.");
        assertThat(body).contains("John");
        assertThat(body).contains("Instructor");
    }

    @Test
    void maintenanceEmailTemplate_shouldRenderCorrectlyInGerman() throws Exception {
        recipient.setLangKey("de");
        var notificationDate = ZonedDateTime.now();
        var expireDate = ZonedDateTime.now().plusHours(2);

        testMailService.buildAndSendSync(recipient, "email.notification.maintenance.title", "mail/notification/maintenanceEmail", Map.of("notificationTitle",
                "Server-Aktualisierung", "notificationText", "Der Server wird aktualisiert.", "notificationDate", notificationDate, "expireDate", expireDate));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Server-Aktualisierung");
        assertThat(body).contains("Der Server wird aktualisiert.");
    }

    @Test
    void maintenanceEmailTemplate_shouldRenderWithoutOptionalText() throws Exception {
        var notificationDate = ZonedDateTime.now();
        var expireDate = ZonedDateTime.now().plusHours(1);

        // Use HashMap to allow null-free map (notificationText is omitted)
        testMailService.buildAndSendSync(recipient, "email.notification.maintenance.title", "mail/notification/maintenanceEmail",
                Map.of("notificationTitle", "Quick Maintenance", "notificationDate", notificationDate, "expireDate", expireDate));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Quick Maintenance");
        assertThat(body).doesNotContain("null");
    }

    @Test
    void maintenanceEmailTemplate_shouldFormatDatesReadably() throws Exception {
        var notificationDate = ZonedDateTime.now();
        var expireDate = ZonedDateTime.now().plusHours(3);

        testMailService.buildAndSendSync(recipient, "email.notification.maintenance.title", "mail/notification/maintenanceEmail",
                Map.of("notificationTitle", "Date Format Test", "notificationDate", notificationDate, "expireDate", expireDate));

        String body = getDeliveredEmailBody();
        // Verify the date is formatted (not raw ZonedDateTime.toString() output)
        assertThat(body).doesNotContain("[");
        assertThat(body).contains("Maintenance window:");
    }

    // ---- Repository query tests ----

    @Test
    void findInstructorRecipients_shouldReturnInstructorsOfOngoingCourse() {
        var now = ZonedDateTime.now();
        // Create ongoing course with instructor group matching user group
        Course ongoingCourse = CourseFactory.generateCourse(null, now.minusDays(30), now.plusDays(30), new HashSet<>(), TEST_PREFIX + "tumuser", TEST_PREFIX + "tutor",
                TEST_PREFIX + "editor", TEST_PREFIX + "instructor");
        courseRepository.save(ongoingCourse);

        var recipients = userTestRepository.findInstructorRecipientsForMaintenanceEmail(now);
        assertThat(recipients).isNotEmpty();
        assertThat(recipients).allMatch(r -> r.email() != null);
    }

    @Test
    void findInstructorRecipients_shouldNotIncludeInstructorsOnlyInExpiredCourses() {
        var now = ZonedDateTime.now();
        // Use a unique group name that no other test will use
        var uniqueInstructorGroup = TEST_PREFIX + "expired_only_instr";

        // Create a user only in the unique group (not in any generic group)
        var expiredOnlyInstructor = userTestRepository.findOneWithGroupsByLogin(TEST_PREFIX + "instructor1").orElseThrow();
        expiredOnlyInstructor.setGroups(Set.of(uniqueInstructorGroup));
        userTestRepository.save(expiredOnlyInstructor);

        // Create only an expired course using this unique group
        Course expiredCourse = CourseFactory.generateCourse(null, now.minusDays(60), now.minusDays(1), new HashSet<>(), "someStudents", "someTutors", "someEditors",
                uniqueInstructorGroup);
        courseRepository.save(expiredCourse);

        var recipients = userTestRepository.findInstructorRecipientsForMaintenanceEmail(now);
        var recipientIds = recipients.stream().map(r -> r.id()).toList();
        assertThat(recipientIds).doesNotContain(expiredOnlyInstructor.getId());
    }

    @Test
    void findInstructorRecipients_shouldExcludeOptedOutUsers() {
        var now = ZonedDateTime.now();
        Course ongoingCourse = CourseFactory.generateCourse(null, now.minusDays(30), now.plusDays(30), new HashSet<>(), TEST_PREFIX + "tumuser", TEST_PREFIX + "tutor",
                TEST_PREFIX + "editor", TEST_PREFIX + "instructor");
        courseRepository.save(ongoingCourse);

        // Get one instructor and opt them out
        var instructorUser = userTestRepository.findOneWithGroupsByLogin(TEST_PREFIX + "instructor1").orElseThrow();
        var setting = new GlobalNotificationSetting();
        setting.setUserId(instructorUser.getId());
        setting.setNotificationType(GlobalNotificationType.MAINTENANCE);
        setting.setEnabled(false);
        globalNotificationSettingRepository.save(setting);

        var recipients = userTestRepository.findInstructorRecipientsForMaintenanceEmail(now);
        assertThat(recipients).noneMatch(r -> r.id().equals(instructorUser.getId()));
    }

    @Test
    void countInstructorRecipients_shouldMatchFindSize() {
        var now = ZonedDateTime.now();
        Course ongoingCourse = CourseFactory.generateCourse(null, now.minusDays(30), now.plusDays(30), new HashSet<>(), TEST_PREFIX + "tumuser", TEST_PREFIX + "tutor",
                TEST_PREFIX + "editor", TEST_PREFIX + "instructor");
        courseRepository.save(ongoingCourse);

        var recipients = userTestRepository.findInstructorRecipientsForMaintenanceEmail(now);
        long count = userTestRepository.countInstructorRecipientsForMaintenanceEmail(now);
        assertThat(count).isEqualTo(recipients.size());
    }

    @Test
    void findInstructorRecipients_shouldIncludeInstructorsOfCourseWithNullDates() {
        var now = ZonedDateTime.now();
        // Course with null start and end dates should be considered ongoing
        Course openCourse = CourseFactory.generateCourse(null, null, null, new HashSet<>(), TEST_PREFIX + "tumuser", TEST_PREFIX + "tutor", TEST_PREFIX + "editor",
                TEST_PREFIX + "instructor");
        courseRepository.save(openCourse);

        var recipients = userTestRepository.findInstructorRecipientsForMaintenanceEmail(now);
        assertThat(recipients).isNotEmpty();
    }

    @Test
    void findInstructorRecipients_shouldExcludeUsersWithNoEmail() {
        var now = ZonedDateTime.now();
        Course ongoingCourse = CourseFactory.generateCourse(null, now.minusDays(30), now.plusDays(30), new HashSet<>(), TEST_PREFIX + "tumuser", TEST_PREFIX + "tutor",
                TEST_PREFIX + "editor", TEST_PREFIX + "instructor");
        courseRepository.save(ongoingCourse);

        // Remove email from one instructor
        var instructor = userTestRepository.findOneWithGroupsByLogin(TEST_PREFIX + "instructor1").orElseThrow();
        instructor.setEmail(null);
        userTestRepository.save(instructor);

        var recipients = userTestRepository.findInstructorRecipientsForMaintenanceEmail(now);
        assertThat(recipients).noneMatch(r -> r.id().equals(instructor.getId()));
    }

    /**
     * Waits for exactly one email to arrive at GreenMail and returns its decoded body content.
     */
    private String getDeliveredEmailBody() throws Exception {
        assertThat(greenMail.waitForIncomingEmail(EMAIL_TIMEOUT_MS, 1)).isTrue();
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(1);
        return messages[0].getContent().toString();
    }
}
