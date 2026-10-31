package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.api.IrisCourseHomeApi;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

@WithMockUser(username = IrisCourseHomeApiTest.PREFIX + "student1", roles = "USER")
class IrisCourseHomeApiTest extends AbstractSpringIntegrationIndependentTest {

    static final String PREFIX = "homeiris";

    @Autowired
    private IrisCourseHomeApi home;

    @Autowired
    private IrisChatSessionRepository sessions;

    @Autowired
    private UserAiPreferenceService preferences;

    @Autowired
    private IrisSettingsService settings;

    @Autowired
    private jakarta.persistence.EntityManagerFactory entityManagerFactory;

    private Course course;

    private User student;

    private ZonedDateTime base;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(PREFIX, 2, 0, 0, 1);
        course = courseUtilService.createEnrolledCourse(PREFIX);
        student = userUtilService.getUserByLogin(PREFIX + "student1");
        base = ZonedDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        preferences.recordDecision(student.getId(), AiSelectionDecision.CLOUD_AI, base);
        setEnabled(true);
    }

    @Test
    void metadataUsesLatestMessageFromEitherSideAndIsBoundedAndPrivate() {
        var old = session(course, student, base.minusDays(5));
        session(course, student, base.minusDays(3));
        var second = session(course, student, base.minusDays(2));
        var newest = session(course, student, base.minusDays(1));
        var assistant = old.newMessage();
        assistant.setSender(IrisMessageSender.LLM);
        assistant.setSentAt(base.minusMinutes(1));
        old.getMessages().add(assistant);
        sessions.saveAndFlush(old);
        sessions.saveAndFlush(new IrisChatSession(course, student));
        session(course, userUtilService.getUserByLogin(PREFIX + "student2"), base);
        session(courseUtilService.createEnrolledCourse(PREFIX), student, base);
        long count = sessions.count();
        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        var result = home.load(course.getId(), student, Instant.now(), base.minusDays(7).toInstant());
        assertThat(result.items()).extracting(i -> i.id()).containsExactly(old.getId(), newest.getId(), second.getId());
        assertThat(statistics.getEntityStatistics(de.tum.cit.aet.artemis.iris.domain.message.IrisMessage.class.getName()).getLoadCount()).isZero();
        assertThat(statistics.getEntityStatistics(IrisChatSession.class.getName()).getLoadCount()).isZero();
        assertThat(sessions.count()).isEqualTo(count);
        assertThat(request.getObjectMapper().writeValueAsString(result)).doesNotContain("private transcript", "messages", "userId");
    }

    @Test
    void optedOutOrUndecidedAccountsHaveNoHistory() {
        session(course, student, base.minusMinutes(1));
        preferences.recordDecision(student.getId(), AiSelectionDecision.NO_AI, base);
        assertThat(home.load(course.getId(), student, Instant.now(), base.minusDays(7).toInstant()).items()).isEmpty();
        preferences.clearDecision(student.getId());
        assertThat(home.load(course.getId(), student, Instant.now(), base.minusDays(7).toInstant()).items()).isEmpty();
    }

    @Test
    void disabledCourseHasNoHistory() {
        session(course, student, base.minusMinutes(1));
        setEnabled(false);
        assertThat(home.load(course.getId(), student, Instant.now(), base.minusDays(7).toInstant()).status().name()).isEqualTo("DISABLED");
    }

    private void setEnabled(boolean enabled) {
        var current = settings.getSettingsForCourse(course);
        settings.updateCourseSettings(course.getId(), IrisCourseSettings.of(enabled, current.customInstructions(), current.variant(), current.supportLevel(), current.rateLimit(),
                current.proactiveStruggleEnabled(), current.legacyBuildTriggersEnabled()), true);
    }

    private IrisChatSession session(Course owner, User user, ZonedDateTime at) {
        var s = new IrisChatSession(owner, user);
        s.setTitle("Learning question");
        var m = s.newMessage();
        m.setSender(IrisMessageSender.USER);
        m.setSentAt(at);
        var c = new IrisTextMessageContent("private transcript");
        c.setMessage(m);
        m.setContent(new ArrayList<>(List.of(c)));
        s.getMessages().add(m);
        return sessions.saveAndFlush(s);
    }
}
