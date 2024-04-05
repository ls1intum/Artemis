package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.CourseInformationSharingConfiguration;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.lecture.LectureUtilService;
import de.tum.in.www1.artemis.post.ConversationUtilService;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRepository;
import de.tum.in.www1.artemis.service.tutorialgroups.TutorialGroupChannelManagementService;
import de.tum.in.www1.artemis.tutorialgroups.TutorialGroupUtilService;
import de.tum.in.www1.artemis.user.UserFactory;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ChannelDTO;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ChannelIdAndNameDTO;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;

class ChannelIntegrationTest extends AbstractConversationTest {

    private static final String TEST_PREFIX = "chtest";

    @Autowired
    TutorialGroupRepository tutorialGroupRepository;

    @Autowired
    TutorialGroupChannelManagementService tutorialGroupChannelManagementService;

    @Autowired
    private TutorialGroupUtilService tutorialGroupUtilService;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @BeforeEach
    void setupTestScenario() throws Exception {
        super.setupTestScenario();
        userUtilService.addUsers(TEST_PREFIX, 2, 2, 1, 2);
        if (userRepository.findOneByLogin(testPrefix + "student42").isEmpty()) {
            userRepository.save(UserFactory.generateActivatedUser(testPrefix + "student42"));
        }
        if (userRepository.findOneByLogin(testPrefix + "tutor42").isEmpty()) {
            userRepository.save(UserFactory.generateActivatedUser(testPrefix + "tutor42"));
        }
        if (userRepository.findOneByLogin(testPrefix + "editor42").isEmpty()) {
            userRepository.save(UserFactory.generateActivatedUser(testPrefix + "editor42"));
        }
        if (userRepository.findOneByLogin(testPrefix + "instructor42").isEmpty()) {
            userRepository.save(UserFactory.generateActivatedUser(testPrefix + "instructor42"));
        }
    }

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createChannel_asInstructor_shouldCreateChannel(boolean isPublicChannel) throws Exception {
        isAllowedToCreateChannelTest(isPublicChannel, "instructor1");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createChannel_asTutor_shouldCreateChannel(boolean isPublicChannel) throws Exception {
        // given
        isAllowedToCreateChannelTest(isPublicChannel, "tutor1");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void createChannel_asEditor_shouldCreateChannel(boolean isPublicChannel) throws Exception {
        // given
        isAllowedToCreateChannelTest(isPublicChannel, "editor1");
    }

    private void isAllowedToCreateChannelTest(boolean isPublicChannel, String loginNameWithoutPrefix) throws Exception {
        // given
        var channelDTO = new ChannelDTO();
        channelDTO.setName(TEST_PREFIX);
        channelDTO.setIsPublic(isPublicChannel);
        channelDTO.setIsAnnouncementChannel(false);
        channelDTO.setDescription("general channel");

        // when
        var chat = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/channels", channelDTO, ChannelDTO.class, HttpStatus.CREATED);
        // then
        this.assertChannelProperties(chat.getId(), channelDTO.getName(), null, channelDTO.getDescription(), channelDTO.getIsPublic(), false);
        var participants = assertParticipants(chat.getId(), 1, loginNameWithoutPrefix);
        // creator is automatically added as channel moderator
        assertThat(participants.stream().findFirst().orElseThrow().getIsModerator()).isTrue();
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.CREATE, chat.getId(), loginNameWithoutPrefix);
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.CREATE);

        // cannot create channels with duplicate names
        expectCreateBadRequest(channelDTO);

        // cleanup
        conversationRepository.deleteById(chat.getId());
    }

    @ParameterizedTest
    @EnumSource(value = CourseInformationSharingConfiguration.class, names = { "COMMUNICATION_ONLY", "DISABLED" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createChannel_messagingFeatureDeactivated_shouldReturnForbidden(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        createTest_messagingDeactivated(courseInformationSharingConfiguration);
    }

    void createTest_messagingDeactivated(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);

        // given
        var channelDTO = new ChannelDTO();
        channelDTO.setIsPublic(true);
        channelDTO.setIsAnnouncementChannel(false);
        channelDTO.setName(TEST_PREFIX);
        channelDTO.setDescription("general channel");

        expectCreateForbidden(channelDTO);

        // active messaging again
        setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void update_messagingFeatureDeactivated_shouldReturnForbidden() throws Exception {
        setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.DISABLED);

        // given
        var channelDTO = new ChannelDTO();
        channelDTO.setIsPublic(true);
        channelDTO.setIsAnnouncementChannel(false);
        channelDTO.setName(TEST_PREFIX);
        channelDTO.setDescription("general channel");

        expectUpdateForbidden(1L, channelDTO);

        // active messaging again
        setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void delete_messagingFeatureDeactivated_shouldReturnForbidden() throws Exception {
        setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.DISABLED);

        expectDeleteForbidden(1L);

        // active messaging again
        setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createChannel_descriptionInvalid_shouldReturnBadRequest(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel, TEST_PREFIX);
        channel.setDescription("a".repeat(251));

        // when
        expectCreateBadRequest(channel);
        // then
        verifyNoParticipantTopicWebsocketSent();

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createChannel_asNonCourseInstructorOrTutorOrEditor_shouldReturnForbidden(boolean isPublicChannel) throws Exception {
        // given
        var channelDTO = new ChannelDTO();
        channelDTO.setName(TEST_PREFIX);
        channelDTO.setIsPublic(isPublicChannel);
        channelDTO.setIsAnnouncementChannel(false);
        channelDTO.setDescription("general channel");

        // then
        expectCreateForbidden(channelDTO);
        userUtilService.changeUser(testPrefix + "instructor42");
        expectCreateForbidden(channelDTO);
        userUtilService.changeUser(testPrefix + "tutor42");
        expectCreateForbidden(channelDTO);
        userUtilService.changeUser(testPrefix + "editor42");
        expectCreateForbidden(channelDTO);

        verifyNoParticipantTopicWebsocketSent();
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteChannel_asInstructor_shouldDeleteChannel(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel, TEST_PREFIX);
        // when
        userUtilService.changeUser(testPrefix + "instructor2");
        request.delete("/api/courses/" + exampleCourseId + "/channels/" + channel.getId(), HttpStatus.OK);
        // then
        assertThat(channelRepository.findById(channel.getId())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteTutorialGroupChannel_asInstructor_shouldReturnBadRequest(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel, TEST_PREFIX);
        var tutorialGroup = tutorialGroupUtilService.createTutorialGroup(exampleCourseId, "tg-channel-test", "LoremIpsum", 10, false, "Garching", Language.ENGLISH.name(),
                userRepository.findOneByLogin(testPrefix + "tutor1").orElseThrow(), Set.of());
        var channelFromDatabase = channelRepository.findById(channel.getId()).orElseThrow();

        tutorialGroup.setTutorialGroupChannel(channelFromDatabase);
        tutorialGroup = tutorialGroupRepository.save(tutorialGroup);

        // when
        userUtilService.changeUser(testPrefix + "instructor2");
        request.delete("/api/courses/" + exampleCourseId + "/channels/" + channel.getId(), HttpStatus.BAD_REQUEST);
        // then
        assertThat(channelRepository.findById(channel.getId())).isNotEmpty();
        verifyNoParticipantTopicWebsocketSent();

        // cleanup
        tutorialGroupChannelManagementService.deleteTutorialGroupChannel(tutorialGroup);
        tutorialGroupRepository.deleteById(tutorialGroup.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void deleteChannel_asCreator_shouldDeleteChannel(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel, TEST_PREFIX);
        // when
        request.delete("/api/courses/" + exampleCourseId + "/channels/" + channel.getId(), HttpStatus.OK);
        // then
        assertThat(channelRepository.findById(channel.getId())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteChannel_asNonCourseInstructor_shouldReturnForbidden(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel, TEST_PREFIX);
        addUserAsChannelModerators(channel, "tutor2");

        // then
        userUtilService.changeUser(testPrefix + "student1");
        expectDeleteForbidden(channel.getId());
        userUtilService.changeUser(testPrefix + "tutor1");
        expectDeleteForbidden(channel.getId());
        userUtilService.changeUser(testPrefix + "tutor2");
        expectDeleteForbidden(channel.getId());
        userUtilService.changeUser(testPrefix + "editor1");
        expectDeleteForbidden(channel.getId());
        userUtilService.changeUser(testPrefix + "instructor42");
        expectDeleteForbidden(channel.getId());

        verifyNoParticipantTopicWebsocketSent();

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateChannel_asUserWithChannelModerationRights_shouldUpdateChannel(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel, TEST_PREFIX + "1");
        var channelForDuplicateCheck = createChannel(isPublicChannel, TEST_PREFIX + "duplicate");
        var updateDTO = new ChannelDTO();
        updateDTO.setName(TEST_PREFIX + "2");
        updateDTO.setDescription("new description");
        updateDTO.setTopic("new topic");
        addUserAsChannelModerators(channel, "tutor1");

        // then
        // every instructor automatically has moderation rights for every channel
        userUtilService.changeUser(testPrefix + "instructor2");
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId(), updateDTO, ChannelDTO.class, HttpStatus.OK);
        this.assertChannelProperties(channel.getId(), updateDTO.getName(), updateDTO.getTopic(), updateDTO.getDescription(), isPublicChannel, false);
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.UPDATE, channel.getId(), "instructor1", "tutor1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE);
        resetWebsocketMock();
        // The channel name can not be modified if it matches another existing channel
        updateDTO.setName(channelForDuplicateCheck.getName());
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId(), updateDTO, ChannelDTO.class, HttpStatus.BAD_REQUEST);

        // channel moderators can also update the channel
        updateDTO.setName(TEST_PREFIX + "3");
        updateDTO.setDescription("new description2");
        updateDTO.setTopic("new topic2");
        userUtilService.changeUser(testPrefix + "tutor1");
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId(), updateDTO, ChannelDTO.class, HttpStatus.OK);
        this.assertChannelProperties(channel.getId(), updateDTO.getName(), updateDTO.getTopic(), updateDTO.getDescription(), isPublicChannel, false);
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.UPDATE, channel.getId(), "instructor1", "tutor1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE);
        // The channel name can not be modified if it matches another existing channel
        updateDTO.setName(channelForDuplicateCheck.getName());
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId(), updateDTO, ChannelDTO.class, HttpStatus.BAD_REQUEST);

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateChannel_onArchivedChannel_shouldReturnOk(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel, TEST_PREFIX + "1");
        archiveChannel(channel.getId());

        var updateDTO = new ChannelDTO();
        updateDTO.setName(TEST_PREFIX + "2");
        updateDTO.setDescription("new description");
        updateDTO.setTopic("new topic");
        addUserAsChannelModerators(channel, "tutor1");

        // then
        userUtilService.changeUser(testPrefix + "instructor2");
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId(), updateDTO, ChannelDTO.class, HttpStatus.OK);
        this.assertChannelProperties(channel.getId(), updateDTO.getName(), updateDTO.getTopic(), updateDTO.getDescription(), isPublicChannel, true);
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.UPDATE, channel.getId(), "instructor1", "tutor1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE);

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateChannel_asUserWithoutChannelModerationRights_shouldReturnForbidden(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel, TEST_PREFIX + "1");
        var updateDTO = new ChannelDTO();
        updateDTO.setName(TEST_PREFIX + "2");
        updateDTO.setDescription("new description");
        updateDTO.setTopic("new topic");

        // then
        userUtilService.changeUser(testPrefix + "student1");
        expectUpdateForbidden(channel.getId(), updateDTO);
        userUtilService.changeUser(testPrefix + "tutor1");
        expectUpdateForbidden(channel.getId(), updateDTO);
        userUtilService.changeUser(testPrefix + "editor1");
        expectUpdateForbidden(channel.getId(), updateDTO);
        userUtilService.changeUser(testPrefix + "instructor42");
        expectUpdateForbidden(channel.getId(), updateDTO);

        verifyNoParticipantTopicWebsocketSent();

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void archiveAndUnarchiveChannel_asUserWithoutChannelModerationRights_shouldReturnForbidden(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel, TEST_PREFIX);

        // then
        userUtilService.changeUser(testPrefix + "student1");
        expectArchivalChangeForbidden(channel, isPublicChannel, true);
        expectArchivalChangeForbidden(channel, isPublicChannel, false);
        userUtilService.changeUser(testPrefix + "tutor1");
        expectArchivalChangeForbidden(channel, isPublicChannel, true);
        expectArchivalChangeForbidden(channel, isPublicChannel, false);
        userUtilService.changeUser(testPrefix + "editor1");
        expectArchivalChangeForbidden(channel, isPublicChannel, true);
        expectArchivalChangeForbidden(channel, isPublicChannel, false);
        userUtilService.changeUser(testPrefix + "instructor42");
        expectArchivalChangeForbidden(channel, isPublicChannel, true);
        expectArchivalChangeForbidden(channel, isPublicChannel, false);

        verifyNoParticipantTopicWebsocketSent();

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void archiveAndUnarchiveChannel_messagingFeatureDeactivated_shouldReturnForbidden() throws Exception {
        var channel = createChannel(true, TEST_PREFIX);
        setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.DISABLED);

        // given
        expectArchivalChangeForbidden(channel, true, true);
        expectArchivalChangeForbidden(channel, true, false);

        // active messaging again
        setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void archiveAndUnarchiveChannel_asUserWithChannelModerationRights_shouldArchiveChannel(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel, TEST_PREFIX);
        addUserAsChannelModerators(channel, "tutor1");

        // then
        // every instructor automatically has moderation rights for every channel
        userUtilService.changeUser(testPrefix + "instructor2");
        testArchivalChangeWorks(channel, isPublicChannel, true);
        testArchivalChangeWorks(channel, isPublicChannel, false);

        // channel moderators can also update the channel
        userUtilService.changeUser(testPrefix + "tutor1");
        testArchivalChangeWorks(channel, isPublicChannel, true);
        testArchivalChangeWorks(channel, isPublicChannel, false);

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void grantRevokeChannelModeratorRole_messagingFeatureDeactivated_shouldReturnForbidden() throws Exception {
        var channel = createChannel(true, TEST_PREFIX);

        setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.DISABLED);

        // given
        expectGrantRevokeChannelModeratorRoleForbidden(channel, true);
        expectGrantRevokeChannelModeratorRoleForbidden(channel, false);

        // active messaging again
        setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void grantRevokeChannelModeratorRole_asUserWithChannelModerationRights_shouldGrantRevokeChannelModeratorRole(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel, TEST_PREFIX);
        addUserAsChannelModerators(channel, "tutor1");
        addUsersToConversation(channel.getId(), "student1");
        addUsersToConversation(channel.getId(), "student2");

        // then
        // every instructor automatically has moderation rights for every channel
        userUtilService.changeUser(testPrefix + "instructor2");
        testGrantRevokeChannelModeratorRoleWorks(channel, true);
        testGrantRevokeChannelModeratorRoleWorks(channel, false);

        // channel moderators can also grand and revoke channel moderation rights
        userUtilService.changeUser(testPrefix + "tutor1");
        testGrantRevokeChannelModeratorRoleWorks(channel, true);
        testGrantRevokeChannelModeratorRoleWorks(channel, false);

        // note: you can NOT revoke the channel moderator role of the creator to guarantee that there is always at least one channel moderator
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + "/revoke-channel-moderator", List.of(testPrefix + "instructor1"),
                HttpStatus.BAD_REQUEST);
        assertUsersAreChannelModerators(channel.getId(), "instructor1");

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void grantChannelModeratorRoleToUserWhoIsNotAParticipantInCourseWideChannel() throws Exception {
        Course course = courseUtilService.createCourseWithMessagingEnabled();
        Channel channel = conversationUtilService.createCourseWideChannel(course, "test");

        request.postWithoutResponseBody("/api/courses/" + course.getId() + "/channels/" + channel.getId() + "/grant-channel-moderator",
                List.of(testPrefix + "student1", testPrefix + "student2"), HttpStatus.OK);

        assertUsersAreChannelModerators(channel.getId(), "student1", "student2");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void revokeChannelModeratorRoleToUserWhoIsNotAParticipantInCourseWideChannel() throws Exception {
        Course course = courseUtilService.createCourseWithMessagingEnabled();
        Channel channel = conversationUtilService.createCourseWideChannel(course, "test");

        request.postWithoutResponseBody("/api/courses/" + course.getId() + "/channels/" + channel.getId() + "/revoke-channel-moderator",
                List.of(testPrefix + "student1", testPrefix + "student2"), HttpStatus.OK);

        assertUserAreNotChannelModerators(channel.getId(), "student1", "student2");
        assertUserAreNotConversationMembers(channel.getId(), "student1", "student2");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void grantRevokeChannelModeratorRole_asUserWithoutChannelModerationRights_shouldReturnForbidden(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel, TEST_PREFIX);

        // then
        userUtilService.changeUser(testPrefix + "student1");
        expectGrantRevokeChannelModeratorRoleForbidden(channel, true);
        expectGrantRevokeChannelModeratorRoleForbidden(channel, false);
        userUtilService.changeUser(testPrefix + "tutor1");
        expectGrantRevokeChannelModeratorRoleForbidden(channel, true);
        expectGrantRevokeChannelModeratorRoleForbidden(channel, false);
        userUtilService.changeUser(testPrefix + "editor1");
        expectGrantRevokeChannelModeratorRoleForbidden(channel, true);
        expectGrantRevokeChannelModeratorRoleForbidden(channel, false);
        userUtilService.changeUser(testPrefix + "instructor42");
        expectGrantRevokeChannelModeratorRoleForbidden(channel, true);
        expectGrantRevokeChannelModeratorRoleForbidden(channel, false);
        verifyNoParticipantTopicWebsocketSent();

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @EnumSource(value = CourseInformationSharingConfiguration.class, names = { "COMMUNICATION_ONLY", "DISABLED" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void registerDeregisterUsersToChannel_messagingFeatureDeactivated_shouldReturnForbidden(CourseInformationSharingConfiguration courseInformationSharingConfiguration)
            throws Exception {
        registerUsersToChannel_messagingDeactivated(courseInformationSharingConfiguration);

    }

    void registerUsersToChannel_messagingDeactivated(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        var channel = createChannel(true, TEST_PREFIX);
        setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);
        // given
        expectRegisterDeregisterForbidden(channel, true);
        expectRegisterDeregisterForbidden(channel, false);

        // active messaging again
        setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void registerUsersToChannel_asUserWithChannelModerationRights_shouldRegisterUsersToChannel(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel, TEST_PREFIX);
        addUserAsChannelModerators(channel, "tutor1");

        // then
        // every instructor automatically has moderation rights for every channel
        userUtilService.changeUser(testPrefix + "instructor2");
        testRegisterAndDeregisterUserWorks(channel, true);
        testRegisterAndDeregisterUserWorks(channel, false);

        // channel moderators can also grant and revoke channel moderator role
        userUtilService.changeUser(testPrefix + "tutor1");
        testRegisterAndDeregisterUserWorks(channel, true);
        testRegisterAndDeregisterUserWorks(channel, false);

        removeUsersFromConversation(channel.getId(), "student1", "student2", "tutor1");
        userUtilService.changeUser(testPrefix + "instructor1");
        var params = new LinkedMultiValueMap<String, String>();
        params.add("addAllStudents", String.valueOf(true));
        params.add("addAllTutors", String.valueOf(true));
        params.add("addAllInstructors", String.valueOf(true));

        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + "/register", HttpStatus.OK, params);
        var course = courseRepository.findByIdElseThrow(exampleCourseId);
        var allStudentLogins = userRepository.findAllByIsDeletedIsFalseAndGroupsContains(course.getStudentGroupName()).stream().map(User::getLogin).collect(Collectors.toSet());
        var allTutorLogins = userRepository.findAllByIsDeletedIsFalseAndGroupsContains(course.getTeachingAssistantGroupName()).stream().map(User::getLogin)
                .collect(Collectors.toSet());
        var allEditorLogins = userRepository.findAllByIsDeletedIsFalseAndGroupsContains(course.getEditorGroupName()).stream().map(User::getLogin).collect(Collectors.toSet());
        var allInstructorLogins = userRepository.findAllByIsDeletedIsFalseAndGroupsContains(course.getInstructorGroupName()).stream().map(User::getLogin)
                .collect(Collectors.toSet());
        var allUserLogins = new HashSet<>(allStudentLogins);
        allUserLogins.addAll(allTutorLogins);
        allUserLogins.addAll(allEditorLogins);
        allUserLogins.addAll(allInstructorLogins);
        String[] allUserLoginsArray = allUserLogins.toArray(String[]::new);

        allUserLoginsArray = Arrays.stream(allUserLoginsArray).filter(login -> login.startsWith(testPrefix)).map(login -> login.substring(testPrefix.length()))
                .toArray(String[]::new);

        assertUsersAreConversationMembers(channel.getId(), allUserLoginsArray);

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void registerUsersToChannel_asUserWithoutChannelModerationRights_shouldReturnForbidden(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel, TEST_PREFIX);

        // then
        userUtilService.changeUser(testPrefix + "student1");
        expectRegisterDeregisterForbidden(channel, true);
        expectRegisterDeregisterForbidden(channel, false);
        userUtilService.changeUser(testPrefix + "tutor1");
        expectRegisterDeregisterForbidden(channel, true);
        expectRegisterDeregisterForbidden(channel, false);
        userUtilService.changeUser(testPrefix + "editor1");
        expectRegisterDeregisterForbidden(channel, true);
        expectRegisterDeregisterForbidden(channel, false);
        userUtilService.changeUser(testPrefix + "instructor42");
        expectRegisterDeregisterForbidden(channel, true);
        expectRegisterDeregisterForbidden(channel, false);
        verifyNoParticipantTopicWebsocketSent();

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void leaveChannel_asNormalUser_canLeaveChannel(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel, TEST_PREFIX);
        addUsersToConversation(channel.getId(), "student1");

        // then
        userUtilService.changeUser(testPrefix + "student1");
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + "/deregister", List.of(testPrefix + "student1"), HttpStatus.OK);
        assertUserAreNotConversationMembers(channel.getId(), "student1");
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.UPDATE, channel.getId(), "instructor1");
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.DELETE, channel.getId(), "student1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE, MetisCrudAction.DELETE);

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void leaveChannel_asCreator_shouldReturnBadRequest(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel, TEST_PREFIX);
        // then
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + "/deregister", List.of(testPrefix + "instructor1"),
                HttpStatus.BAD_REQUEST);
        assertUsersAreConversationMembers(channel.getId(), "instructor1");
        verifyNoParticipantTopicWebsocketSent();

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void joinPublicChannel_asNormalUser_canJoinPublicChannel() throws Exception {
        // given
        var channel = createChannel(true, TEST_PREFIX);

        // then
        userUtilService.changeUser(testPrefix + "student1");
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + "/register", List.of(testPrefix + "student1"), HttpStatus.OK);
        assertUsersAreConversationMembers(channel.getId(), "student1");
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.UPDATE, channel.getId(), "instructor1");
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.CREATE, channel.getId(), "student1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE, MetisCrudAction.CREATE);

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void joinPrivateChannel_asNormalUser_canNotJoinPrivateChannel() throws Exception {
        // given
        var channel = createChannel(false, TEST_PREFIX);

        // then
        userUtilService.changeUser(testPrefix + "student1");
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + "/register", List.of(testPrefix + "student1"), HttpStatus.FORBIDDEN);
        assertUserAreNotConversationMembers(channel.getId(), "student1");

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void joinPrivateChannel_asInstructor_canJoinPrivateChannel() throws Exception {
        // given
        var channel = createChannel(false, TEST_PREFIX);

        // then
        userUtilService.changeUser(testPrefix + "instructor2");
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + "/register", List.of(testPrefix + "instructor2"), HttpStatus.OK);
        assertUsersAreConversationMembers(channel.getId(), "instructor2");

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getCourseChannelsOverview_asNormalUser_canSeeAllPublicChannelsAndPrivateChannelsWhereMember() throws Exception {
        // given
        var publicChannelWhereMember = createChannel(true, TEST_PREFIX + "1");
        addUsersToConversation(publicChannelWhereMember.getId(), "student1");
        var publicChannelWhereNotMember = createChannel(true, TEST_PREFIX + "2");
        var privateChannelWhereMember = createChannel(false, TEST_PREFIX + "3");
        addUsersToConversation(privateChannelWhereMember.getId(), "student1");
        var privateChannelWhereNotMember = createChannel(false, TEST_PREFIX + "4");

        // then
        userUtilService.changeUser(testPrefix + "student1");
        var channels = request.getList("/api/courses/" + exampleCourseId + "/channels/overview", HttpStatus.OK, ChannelDTO.class);
        assertThat(channels.stream().map(ChannelDTO::getId).toList()).contains(publicChannelWhereMember.getId(), publicChannelWhereNotMember.getId(),
                privateChannelWhereMember.getId());

        // cleanup
        conversationRepository.deleteById(privateChannelWhereMember.getId());
        conversationRepository.deleteById(publicChannelWhereNotMember.getId());
        conversationRepository.deleteById(publicChannelWhereMember.getId());
        conversationRepository.deleteById(privateChannelWhereNotMember.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getCourseChannelsOverview_asCourseInstructor_canSeeAllPublicChannelsAndAllPrivateChannels() throws Exception {
        // given
        var publicChannelWhereMember = createChannel(true, TEST_PREFIX + "1");
        addUsersToConversation(publicChannelWhereMember.getId(), "student1");
        var publicChannelWhereNotMember = createChannel(true, TEST_PREFIX + "2");
        var privateChannelWhereMember = createChannel(false, TEST_PREFIX + "3");
        addUsersToConversation(privateChannelWhereMember.getId(), "student1");
        var privateChannelWhereNotMember = createChannel(false, TEST_PREFIX + "4");

        // then
        userUtilService.changeUser(testPrefix + "instructor2");
        var channels = request.getList("/api/courses/" + exampleCourseId + "/channels/overview", HttpStatus.OK, ChannelDTO.class);
        assertThat(channels.stream().map(ChannelDTO::getId).toList()).contains(publicChannelWhereMember.getId(), publicChannelWhereNotMember.getId(),
                privateChannelWhereMember.getId(), privateChannelWhereNotMember.getId());

        // cleanup
        conversationRepository.deleteById(privateChannelWhereMember.getId());
        conversationRepository.deleteById(publicChannelWhereNotMember.getId());
        conversationRepository.deleteById(publicChannelWhereMember.getId());
        conversationRepository.deleteById(privateChannelWhereNotMember.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = { "student1", "tutor1", "instructor2" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getCoursePublicChannelsOverview_asNormalUser_canSeeAllPublicChannels(String userLogin) throws Exception {
        // given
        var publicChannelWhereMember = createChannel(true, TEST_PREFIX + "1");
        addUsersToConversation(publicChannelWhereMember.getId(), userLogin);
        var publicChannelWhereNotMember = createChannel(true, TEST_PREFIX + "2");
        var privateChannelWhereMember = createChannel(false, TEST_PREFIX + "3");
        addUsersToConversation(privateChannelWhereMember.getId(), userLogin);
        var privateChannelWhereNotMember = createChannel(false, TEST_PREFIX + "4");
        var courseWideChannelWhereMember = createCourseWideChannel(TEST_PREFIX + "5");
        addUsersToConversation(courseWideChannelWhereMember.getId(), userLogin);
        var courseWideChannelWhereNotMember = createCourseWideChannel(TEST_PREFIX + "6");
        var visibleLecture = lectureUtilService.createLecture(exampleCourse, null);
        var visibleLectureChannel = lectureUtilService.addLectureChannel(visibleLecture);
        var invisibleLecture = lectureUtilService.createLecture(exampleCourse, ZonedDateTime.now().plusDays(1));
        var invisibleLectureChannel = lectureUtilService.addLectureChannel(invisibleLecture);

        // then
        userUtilService.changeUser(testPrefix + userLogin);
        var channels = request.getList("/api/courses/" + exampleCourseId + "/channels/public-overview", HttpStatus.OK, ChannelIdAndNameDTO.class);
        assertThat(channels).hasSize(5);
        assertThat(channels.stream().map(ChannelIdAndNameDTO::id).toList()).contains(publicChannelWhereMember.getId(), publicChannelWhereNotMember.getId(),
                courseWideChannelWhereMember.getId(), courseWideChannelWhereNotMember.getId(), visibleLectureChannel.getId());

        // cleanup
        conversationRepository.deleteById(privateChannelWhereMember.getId());
        conversationRepository.deleteById(publicChannelWhereNotMember.getId());
        conversationRepository.deleteById(publicChannelWhereMember.getId());
        conversationRepository.deleteById(privateChannelWhereNotMember.getId());
        conversationRepository.deleteById(courseWideChannelWhereMember.getId());
        conversationRepository.deleteById(courseWideChannelWhereNotMember.getId());
        conversationRepository.deleteById(visibleLectureChannel.getId());
        conversationRepository.deleteById(invisibleLectureChannel.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getExerciseChannel_asCourseStudent_shouldGetExerciseChannel() throws Exception {
        Course course = courseRepository.findById(exampleCourseId).orElseThrow();
        var exercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(7), ZonedDateTime.now().plusMinutes(14));
        var publicChannelWhereMember = createChannel(true, TEST_PREFIX + "1");
        Channel channel = channelRepository.findById(publicChannelWhereMember.getId()).orElseThrow();
        channel.setExercise(exercise);
        channelRepository.save(channel);
        addUsersToConversation(publicChannelWhereMember.getId(), "student1");
        addUsersToConversation(publicChannelWhereMember.getId(), "student2");

        assertParticipants(publicChannelWhereMember.getId(), 3, "student1", "student2", "instructor1");

        // switch to student1
        userUtilService.changeUser(testPrefix + "student1");

        Channel exerciseChannel = request.get("/api/courses/" + exampleCourseId + "/exercises/" + exercise.getId() + "/channel", HttpStatus.OK, Channel.class);
        assertThat(exerciseChannel.getId()).isEqualTo(publicChannelWhereMember.getId());
        assertThat(exerciseChannel.getExercise()).isNull();

        conversationRepository.deleteById(publicChannelWhereMember.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getLectureChannel_asCourseStudent_IfNotParticipantYet() throws Exception {
        Course course = courseUtilService.createCourse();
        courseUtilService.enableMessagingForCourse(course);
        Lecture lecture = lectureUtilService.createLecture(course, ZonedDateTime.now());
        Channel lectureChannel = lectureUtilService.addLectureChannel(lecture);

        Channel returnedLectureChannel = request.get("/api/courses/" + course.getId() + "/lectures/" + lecture.getId() + "/channel", HttpStatus.OK, Channel.class);

        assertThat(returnedLectureChannel).isNotNull();
        assertThat(returnedLectureChannel.getId()).isEqualTo(lectureChannel.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getExerciseChannel_asCourseStudent_IfNotParticipantYet() throws Exception {
        Course course = courseUtilService.createCourse();
        courseUtilService.enableMessagingForCourse(course);
        TextExercise exercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(1));
        Channel exerciseChannel = exerciseUtilService.addChannelToExercise(exercise);

        Channel returnedExerciseChannel = request.get("/api/courses/" + course.getId() + "/exercises/" + exercise.getId() + "/channel", HttpStatus.OK, Channel.class);

        assertThat(returnedExerciseChannel).isNotNull();
        assertThat(returnedExerciseChannel.getId()).isEqualTo(exerciseChannel.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getLectureChannel_asCourseStudent_shouldGetLectureChannel() throws Exception {
        Course course = courseRepository.findById(exampleCourseId).orElseThrow();
        Lecture lecture = new Lecture();
        lecture.setDescription("Test Lecture");
        lecture.setCourse(course);
        lecture = lectureRepository.save(lecture);
        var publicChannelWhereMember = createChannel(true, TEST_PREFIX + "1");
        Channel channel = channelRepository.findById(publicChannelWhereMember.getId()).orElseThrow();
        channel.setLecture(lecture);
        channelRepository.save(channel);
        addUsersToConversation(publicChannelWhereMember.getId(), "student1");
        addUsersToConversation(publicChannelWhereMember.getId(), "student2");

        assertParticipants(publicChannelWhereMember.getId(), 3, "student1", "student2", "instructor1");

        userUtilService.changeUser(testPrefix + "student1");

        Channel lectureChannel = request.get("/api/courses/" + exampleCourseId + "/lectures/" + lecture.getId() + "/channel", HttpStatus.OK, Channel.class);
        assertThat(lectureChannel.getId()).isEqualTo(publicChannelWhereMember.getId());
        assertThat(lectureChannel.getLecture()).isNull();

        conversationRepository.deleteById(publicChannelWhereMember.getId());
        lectureRepository.deleteById(lecture.getId());
    }

    private void testArchivalChangeWorks(ChannelDTO channel, boolean isPublicChannel, boolean shouldArchive) throws Exception {
        // prepare channel in db
        if (shouldArchive) {
            this.unArchiveChannel(channel.getId());
        }
        else {
            this.archiveChannel(channel.getId());
        }
        var postfix = shouldArchive ? "/archive" : "/unarchive";
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + postfix, HttpStatus.OK, new LinkedMultiValueMap<>());
        this.assertChannelProperties(channel.getId(), channel.getName(), channel.getTopic(), channel.getDescription(), isPublicChannel, shouldArchive);
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.UPDATE, channel.getId(), "instructor1", "tutor1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE);
        resetWebsocketMock();
    }

    private void testRegisterAndDeregisterUserWorks(ChannelDTO channel, boolean shouldRegister) throws Exception {
        // prepare channel in db
        if (shouldRegister) {
            this.removeUsersFromConversation(channel.getId(), "student1", "student2");
        }
        else {
            this.addUsersToConversation(channel.getId(), "student1", "student2");
        }
        var postfix = shouldRegister ? "/register" : "/deregister";

        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + postfix, List.of(testPrefix + "student1", testPrefix + "student2"),
                HttpStatus.OK);
        if (shouldRegister) {
            assertUsersAreConversationMembers(channel.getId(), "student1", "student2");
        }
        else {
            assertUserAreNotConversationMembers(channel.getId(), "student1", "student2");
        }
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.UPDATE, channel.getId(), "instructor1", "tutor1");
        if (shouldRegister) {
            verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.CREATE, channel.getId(), "student1", "student2");
            verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE, MetisCrudAction.CREATE);
        }
        else {
            verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.DELETE, channel.getId(), "student1", "student2");
            verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE, MetisCrudAction.DELETE);
        }
        resetWebsocketMock();
    }

    private void testGrantRevokeChannelModeratorRoleWorks(ChannelDTO channel, boolean shouldGrant) throws Exception {
        // prepare channel in db
        if (shouldGrant) {
            this.revokeChannelModeratorRole(channel.getId(), "student1");
            this.revokeChannelModeratorRole(channel.getId(), "student2");
        }
        else {
            this.grantChannelModeratorRole(channel.getId(), "student1");
            this.grantChannelModeratorRole(channel.getId(), "student2");
        }
        var postfix = shouldGrant ? "/grant-channel-moderator" : "/revoke-channel-moderator";

        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + postfix, List.of(testPrefix + "student1", testPrefix + "student2"),
                HttpStatus.OK);
        if (shouldGrant) {
            assertUsersAreChannelModerators(channel.getId(), "student1", "student2");
        }
        else {
            assertUserAreNotChannelModerators(channel.getId(), "student1", "student2");
        }
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.UPDATE, channel.getId(), "instructor1", "tutor1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE);
        resetWebsocketMock();
    }

    private void expectArchivalChangeForbidden(ChannelDTO channel, boolean isPublicChannel, boolean shouldArchive) throws Exception {
        // prepare channel in db
        if (shouldArchive) {
            this.unArchiveChannel(channel.getId());
        }
        else {
            this.archiveChannel(channel.getId());
        }
        var postfix = shouldArchive ? "/archive" : "/unarchive";
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + postfix, HttpStatus.FORBIDDEN, new LinkedMultiValueMap<>());
        this.assertChannelProperties(channel.getId(), channel.getName(), channel.getTopic(), channel.getDescription(), isPublicChannel, !shouldArchive);
        verifyNoParticipantTopicWebsocketSent();
        resetWebsocketMock();
    }

    private void expectGrantRevokeChannelModeratorRoleForbidden(ChannelDTO channel, boolean shouldGrant) throws Exception {
        // prepare channel in db
        var postfix = shouldGrant ? "/grant-channel-moderator" : "/revoke-channel-moderator";
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + postfix, List.of(testPrefix + "student1", testPrefix + "student2"),
                HttpStatus.FORBIDDEN);
    }

    private void expectRegisterDeregisterForbidden(ChannelDTO channel, boolean shouldRegister) throws Exception {
        // prepare channel in db
        var postfix = shouldRegister ? "/register" : "/deregister";
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + postfix, List.of(testPrefix + "student1", testPrefix + "student2"),
                HttpStatus.FORBIDDEN);
    }

    private void expectCreateBadRequest(ChannelDTO channelDTO) throws Exception {
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/channels", channelDTO, ChannelDTO.class, HttpStatus.BAD_REQUEST);
    }

    private void expectCreateForbidden(ChannelDTO channelDTO) throws Exception {
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/channels", channelDTO, ChannelDTO.class, HttpStatus.FORBIDDEN);
    }

    private void expectDeleteForbidden(Long channelId) throws Exception {
        request.delete("/api/courses/" + exampleCourseId + "/channels/" + channelId, HttpStatus.FORBIDDEN);
    }

    private void expectUpdateForbidden(Long channelId, ChannelDTO updateDTO) throws Exception {
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channelId, updateDTO, ChannelDTO.class, HttpStatus.FORBIDDEN);
    }

    private void assertChannelProperties(Long channelId, String name, String topic, String description, Boolean isPublic, Boolean isArchived) {
        var channel = channelRepository.findById(channelId).orElseThrow();
        assertThat(channel.getName()).isEqualTo(name);
        assertThat(channel.getTopic()).isEqualTo(topic);
        assertThat(channel.getDescription()).isEqualTo(description);
        assertThat(channel.getIsPublic()).isEqualTo(isPublic);
        assertThat(channel.getIsArchived()).isEqualTo(isArchived);
    }

}
