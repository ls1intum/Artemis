package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.AbstractIrisIntegrationTest;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageOrigin;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.util.IrisChatSessionUtilService;

class IrisRateLimitOriginExclusionTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "struggleratelimit";

    @Autowired
    private IrisMessageRepository irisMessageRepository;

    @Autowired
    private IrisMessageService irisMessageService;

    @Autowired
    private IrisChatSessionUtilService irisChatSessionUtilService;

    private IrisChatSession session;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        Course course = courseUtilService.addEmptyCourse();
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        session = irisChatSessionUtilService.createAndSaveBareCourseSessionForUser(course, student);
    }

    @Test
    void proactiveStruggleMessagesAreNotCounted() {
        long userId = session.getUserId();

        var normal = new IrisMessage();
        normal.addContent(new IrisTextMessageContent("normal answer"));
        irisMessageService.saveMessage(normal, session, IrisMessageSender.LLM);

        var proactive = new IrisMessage();
        proactive.addContent(new IrisTextMessageContent("proactive nudge"));
        proactive.setOrigin(IrisMessageOrigin.PROACTIVE_STRUGGLE);
        irisMessageService.saveMessage(proactive, session, IrisMessageSender.LLM);

        var start = ZonedDateTime.now().minusHours(1);
        var end = ZonedDateTime.now().plusMinutes(1);

        int count = irisMessageRepository.countLlmResponsesOfUserWithinTimeframe(userId, start, end);
        assertThat(count).isEqualTo(1);
    }
}
