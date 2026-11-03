package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.iris.api.IrisUsageStatisticsApi;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.util.IrisMessageFactory;

class IrisUsageStatisticsApiTest extends AbstractIrisChatSessionTest {

    private static final String TEST_PREFIX = "irisusagestatistics";

    @Autowired
    private IrisUsageStatisticsApi irisUsageStatisticsApi;

    @Autowired
    private IrisChatSessionRepository irisChatSessionRepository;

    @Autowired
    private IrisMessageService irisMessageService;

    @Autowired
    private IrisMessageRepository irisMessageRepository;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    private IrisMessage save(IrisChatSession session, IrisMessageSender sender, boolean intermediate) {
        IrisMessage message = irisMessageService.saveMessage(IrisMessageFactory.createIrisMessageForSessionWithContent(session), session, sender);
        if (intermediate) {
            message.setIntermediate(true);
            irisMessageRepository.save(message);
        }
        return message;
    }

    @Test
    void countsUserMessagesAndFinalAnswersOfTheGivenCoursesOnly() {
        IrisChatSession session = irisChatSessionRepository.save(new IrisChatSession(course, userUtilService.getUserByLogin(TEST_PREFIX + "student1")));
        save(session, IrisMessageSender.USER, false);
        save(session, IrisMessageSender.LLM, false);
        save(session, IrisMessageSender.USER, false);
        // an intermediate status message of Iris, a summary and a command are no messages between a user and Iris
        save(session, IrisMessageSender.LLM, true);
        save(session, IrisMessageSender.SUMMARY, false);
        save(session, IrisMessageSender.COMMAND, false);

        assertThat(irisUsageStatisticsApi.countMessagesInCourses(Set.of(course.getId()))).isEqualTo(3);
        assertThat(irisUsageStatisticsApi.countMessagesInCourses(Set.of(course.getId() + 100_000))).isZero();
    }
}
