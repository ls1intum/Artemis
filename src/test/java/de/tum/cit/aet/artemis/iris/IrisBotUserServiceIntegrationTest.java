package de.tum.cit.aet.artemis.iris;

import static de.tum.cit.aet.artemis.core.domain.User.IRIS_BOT_LOGIN;
import static de.tum.cit.aet.artemis.iris.service.IrisBotUserService.IRIS_BOT_IMAGE_URL;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.communication.test_repository.ConversationParticipantTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.iris.service.IrisBotUserService;

class IrisBotUserServiceIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irisbotuser";

    @Autowired
    private IrisBotUserService irisBotUserService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private ConversationParticipantTestRepository conversationParticipantRepository;

    private Course course;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        course = courseUtilService.createCourse();
    }

    @Test
    void ensureIrisBotUserExists_createsBot() {
        irisBotUserService.ensureIrisBotUserExists();

        var botUser = userTestRepository.findOneWithGroupsAndAuthoritiesByLogin(IRIS_BOT_LOGIN);
        assertThat(botUser).isPresent();
        assertThat(botUser.get().getFirstName()).isEqualTo("Iris");
        assertThat(botUser.get().getLastName()).isEqualTo("Bot");
        assertThat(botUser.get().getActivated()).isTrue();
        assertThat(botUser.get().isInternal()).isTrue();
        assertThat(botUser.get().isBot()).isTrue();
        assertThat(botUser.get().getImageUrl()).isEqualTo(IRIS_BOT_IMAGE_URL);
    }

    @Test
    void ensureIrisBotUserExists_idempotent() {
        irisBotUserService.ensureIrisBotUserExists();
        User first = userTestRepository.findOneWithGroupsAndAuthoritiesByLogin(IRIS_BOT_LOGIN).orElseThrow();

        irisBotUserService.ensureIrisBotUserExists();
        User second = userTestRepository.findOneWithGroupsAndAuthoritiesByLogin(IRIS_BOT_LOGIN).orElseThrow();

        assertThat(first.getId()).isEqualTo(second.getId());
    }

    @Test
    void getIrisBotUser_returnsBot() {
        irisBotUserService.ensureIrisBotUserExists();

        User botUser = irisBotUserService.getIrisBotUser();
        assertThat(botUser).isNotNull();
        assertThat(botUser.isBot()).isTrue();
    }

    @Test
    void enrollBotInCourseChannels_addsToAllChannels() {
        irisBotUserService.ensureIrisBotUserExists();
        User botUser = irisBotUserService.getIrisBotUser();

        var channel1 = conversationUtilService.createCourseWideChannel(course, "general");
        var channel2 = conversationUtilService.createCourseWideChannel(course, "random");

        irisBotUserService.enrollBotInCourseChannels(course);

        assertThat(conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channel1.getId(), botUser.getId())).isPresent();
        assertThat(conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channel2.getId(), botUser.getId())).isPresent();
    }

    @Test
    void enrollBotInCourseChannels_idempotent() {
        irisBotUserService.ensureIrisBotUserExists();
        User botUser = irisBotUserService.getIrisBotUser();

        var channel = conversationUtilService.createCourseWideChannel(course, "general");

        irisBotUserService.enrollBotInCourseChannels(course);
        irisBotUserService.enrollBotInCourseChannels(course);

        // Verify only one participant entry
        var participants = conversationParticipantRepository.findConversationParticipantsByConversationId(channel.getId());
        long botParticipantCount = participants.stream().filter(p -> p.getUser().getId().equals(botUser.getId())).count();
        assertThat(botParticipantCount).isEqualTo(1);
    }

    @Test
    void removeBotFromCourseChannels() {
        irisBotUserService.ensureIrisBotUserExists();
        User botUser = irisBotUserService.getIrisBotUser();

        var channel = conversationUtilService.createCourseWideChannel(course, "general");

        irisBotUserService.enrollBotInCourseChannels(course);
        assertThat(conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channel.getId(), botUser.getId())).isPresent();

        irisBotUserService.removeBotFromCourseChannels(course);
        assertThat(conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channel.getId(), botUser.getId())).isEmpty();
    }
}
