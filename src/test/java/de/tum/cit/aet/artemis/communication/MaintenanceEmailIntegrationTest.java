package de.tum.cit.aet.artemis.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

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

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.config.ArtemisProperties;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.repository.UserCourseRoleRepository;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.notification.domain.GlobalNotificationSetting;
import de.tum.cit.aet.artemis.notification.domain.GlobalNotificationType;
import de.tum.cit.aet.artemis.notification.repository.GlobalNotificationSettingRepository;
import de.tum.cit.aet.artemis.notification.repository.MaintenanceEmailRecipientRepository;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

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

    @Autowired
    private MaintenanceEmailRecipientRepository maintenanceEmailRecipientRepository;

    @Autowired
    private UserCourseRoleRepository userCourseRoleRepository;

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

        var mailEnabledProperties = new ArtemisProperties();
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

        testMailService.buildAndSendSync(recipient, "email.notification.maintenance.title", "mail/notification/maintenanceEmail",
                Map.of("notificationTitle", "Server Update v2.0", "notificationText", "We will be updating the server to the latest version.", "formattedStart",
                        formatDate(notificationDate, "en"), "formattedEnd", formatDate(expireDate, "en")));

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

        testMailService.buildAndSendSync(recipient, "email.notification.maintenance.title", "mail/notification/maintenanceEmail",
                Map.of("notificationTitle", "Server-Aktualisierung", "notificationText", "Der Server wird aktualisiert.", "formattedStart", formatDate(notificationDate, "de"),
                        "formattedEnd", formatDate(expireDate, "de")));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Server-Aktualisierung");
        assertThat(body).contains("Der Server wird aktualisiert.");
    }

    @Test
    void maintenanceEmailTemplate_shouldRenderWithoutOptionalText() throws Exception {
        var notificationDate = ZonedDateTime.now();
        var expireDate = ZonedDateTime.now().plusHours(1);

        testMailService.buildAndSendSync(recipient, "email.notification.maintenance.title", "mail/notification/maintenanceEmail",
                Map.of("notificationTitle", "Quick Maintenance", "formattedStart", formatDate(notificationDate, "en"), "formattedEnd", formatDate(expireDate, "en")));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Quick Maintenance");
        assertThat(body).doesNotContain("null");
    }

    @Test
    void maintenanceEmailTemplate_shouldFormatDatesReadably() throws Exception {
        var notificationDate = ZonedDateTime.now();
        var expireDate = ZonedDateTime.now().plusHours(3);

        String start = formatDate(notificationDate, "en");
        String end = formatDate(expireDate, "en");

        testMailService.buildAndSendSync(recipient, "email.notification.maintenance.title", "mail/notification/maintenanceEmail",
                Map.of("notificationTitle", "Date Format Test", "formattedStart", start, "formattedEnd", end));

        String body = getDeliveredEmailBody();
        assertThat(body).contains("Maintenance window:");
        assertThat(body).contains(start);
        assertThat(body).contains(end);
    }

    // ---- Repository query tests ----

    @Test
    void findInstructorRecipients_shouldReturnInstructorsOfOngoingCourse() {
        var now = ZonedDateTime.now();
        // Create ongoing course and enroll instructor1 as INSTRUCTOR
        Course ongoingCourse = CourseFactory.generateCourse(null, now.minusDays(30), now.plusDays(30), new HashSet<>(), TEST_PREFIX + "tumuser", TEST_PREFIX + "tutor",
                TEST_PREFIX + "editor", TEST_PREFIX + "instructor");
        courseRepository.save(ongoingCourse);
        var instructor1 = userTestRepository.findOneWithGroupsByLogin(TEST_PREFIX + "instructor1").orElseThrow();
        userUtilService.enrollUserInCourse(instructor1, ongoingCourse, CourseRole.INSTRUCTOR);

        var recipients = maintenanceEmailRecipientRepository.findInstructorRecipientsForMaintenanceEmail(now);
        assertThat(recipients).isNotEmpty();
        assertThat(recipients).allMatch(r -> r.email() != null);
    }

    @Test
    void findInstructorRecipients_shouldNotIncludeInstructorsOnlyInExpiredCourses() {
        var now = ZonedDateTime.now();
        // Use a unique group name that no other test will use
        var uniqueInstructorGroup = TEST_PREFIX + "expired_only_instr";

        // Enroll instructor1 only in the expired course (not in any ongoing course)
        var expiredOnlyInstructor = userTestRepository.findOneWithGroupsByLogin(TEST_PREFIX + "instructor1").orElseThrow();

        // Create only an expired course
        Course expiredCourse = CourseFactory.generateCourse(null, now.minusDays(60), now.minusDays(1), new HashSet<>(), "someStudents", "someTutors", "someEditors",
                uniqueInstructorGroup);
        courseRepository.save(expiredCourse);
        userUtilService.enrollUserInCourse(expiredOnlyInstructor, expiredCourse, CourseRole.INSTRUCTOR);

        var recipients = maintenanceEmailRecipientRepository.findInstructorRecipientsForMaintenanceEmail(now);
        var recipientIds = recipients.stream().map(r -> r.id()).toList();
        assertThat(recipientIds).doesNotContain(expiredOnlyInstructor.getId());
    }

    @Test
    void findInstructorRecipients_shouldExcludeOptedOutUsers() {
        var now = ZonedDateTime.now();
        Course ongoingCourse = CourseFactory.generateCourse(null, now.minusDays(30), now.plusDays(30), new HashSet<>(), TEST_PREFIX + "tumuser", TEST_PREFIX + "tutor",
                TEST_PREFIX + "editor", TEST_PREFIX + "instructor");
        courseRepository.save(ongoingCourse);

        // Enroll instructor2 so the course has at least one recipient (instructor1 is opted out)
        var instructor2 = userTestRepository.findOneWithGroupsByLogin(TEST_PREFIX + "instructor2").orElseThrow();
        userUtilService.enrollUserInCourse(instructor2, ongoingCourse, CourseRole.INSTRUCTOR);

        // Get instructor1 and opt them out
        var instructorUser = userTestRepository.findOneWithGroupsByLogin(TEST_PREFIX + "instructor1").orElseThrow();
        userUtilService.enrollUserInCourse(instructorUser, ongoingCourse, CourseRole.INSTRUCTOR);
        var setting = new GlobalNotificationSetting();
        setting.setUserId(instructorUser.getId());
        setting.setNotificationType(GlobalNotificationType.MAINTENANCE);
        setting.setEnabled(false);
        globalNotificationSettingRepository.save(setting);

        var recipients = maintenanceEmailRecipientRepository.findInstructorRecipientsForMaintenanceEmail(now);
        assertThat(recipients).noneMatch(r -> r.id().equals(instructorUser.getId()));
    }

    @Test
    void countInstructorRecipients_shouldMatchFindSize() {
        var now = ZonedDateTime.now();
        Course ongoingCourse = CourseFactory.generateCourse(null, now.minusDays(30), now.plusDays(30), new HashSet<>(), TEST_PREFIX + "tumuser", TEST_PREFIX + "tutor",
                TEST_PREFIX + "editor", TEST_PREFIX + "instructor");
        courseRepository.save(ongoingCourse);

        var recipients = maintenanceEmailRecipientRepository.findInstructorRecipientsForMaintenanceEmail(now);
        long count = maintenanceEmailRecipientRepository.countInstructorRecipientsForMaintenanceEmail(now);
        assertThat(count).isEqualTo(recipients.size());
    }

    @Test
    void findInstructorRecipients_shouldIncludeInstructorsOfCourseWithNullDates() {
        var now = ZonedDateTime.now();
        // Course with null start and end dates should be considered ongoing
        Course openCourse = CourseFactory.generateCourse(null, null, null, new HashSet<>(), TEST_PREFIX + "tumuser", TEST_PREFIX + "tutor", TEST_PREFIX + "editor",
                TEST_PREFIX + "instructor");
        courseRepository.save(openCourse);
        var instructor1 = userTestRepository.findOneWithGroupsByLogin(TEST_PREFIX + "instructor1").orElseThrow();
        userUtilService.enrollUserInCourse(instructor1, openCourse, CourseRole.INSTRUCTOR);

        var recipients = maintenanceEmailRecipientRepository.findInstructorRecipientsForMaintenanceEmail(now);
        assertThat(recipients).isNotEmpty();
    }

    @Test
    void findInstructorRecipients_shouldExcludeUsersWithNoEmail() {
        var now = ZonedDateTime.now();
        Course ongoingCourse = CourseFactory.generateCourse(null, now.minusDays(30), now.plusDays(30), new HashSet<>(), TEST_PREFIX + "tumuser", TEST_PREFIX + "tutor",
                TEST_PREFIX + "editor", TEST_PREFIX + "instructor");
        courseRepository.save(ongoingCourse);

        // Remove email from instructor1
        var instructor = userTestRepository.findOneWithGroupsByLogin(TEST_PREFIX + "instructor1").orElseThrow();
        instructor.setEmail(null);
        userTestRepository.save(instructor);

        // Enroll both instructors; instructor1 has no email so should be excluded
        userUtilService.enrollUserInCourse(instructor, ongoingCourse, CourseRole.INSTRUCTOR);
        var instructor2 = userTestRepository.findOneWithGroupsByLogin(TEST_PREFIX + "instructor2").orElseThrow();
        userUtilService.enrollUserInCourse(instructor2, ongoingCourse, CourseRole.INSTRUCTOR);

        var recipients = maintenanceEmailRecipientRepository.findInstructorRecipientsForMaintenanceEmail(now);
        assertThat(recipients).noneMatch(r -> r.id().equals(instructor.getId()));
    }

    private String formatDate(ZonedDateTime dateTime, String langKey) {
        ZonedDateTime localDateTime = dateTime.withZoneSameInstant(ZoneId.systemDefault());
        Locale locale = Locale.forLanguageTag(langKey);
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale);
        DateTimeFormatter zoneFormatter = DateTimeFormatter.ofPattern("z").withLocale(locale);
        return localDateTime.format(formatter) + " " + localDateTime.format(zoneFormatter);
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
