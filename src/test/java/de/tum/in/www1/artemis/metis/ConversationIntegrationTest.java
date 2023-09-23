package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.CourseInformationSharingConfiguration;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.lecture.LectureUtilService;
import de.tum.in.www1.artemis.post.ConversationUtilService;
import de.tum.in.www1.artemis.user.UserFactory;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.*;

class ConversationIntegrationTest extends AbstractConversationTest {

    private static final String TEST_PREFIX = "cvtest";

    private final TextExerciseUtilService textExerciseUtilService;

    private final ExerciseUtilService exerciseUtilService;

    private final ExamUtilService examUtilService;

    private final LectureUtilService lectureUtilService;

    private final ConversationUtilService conversationUtilService;

    @Autowired
    public ConversationIntegrationTest(TextExerciseUtilService textExerciseUtilService, ExerciseUtilService exerciseUtilService, ExamUtilService examUtilService,
            LectureUtilService lectureUtilService, ConversationUtilService conversationUtilService) {
        this.textExerciseUtilService = textExerciseUtilService;
        this.exerciseUtilService = exerciseUtilService;
        this.examUtilService = examUtilService;
        this.lectureUtilService = lectureUtilService;
        this.conversationUtilService = conversationUtilService;
    }

    @BeforeEach
    void setupTestScenario() throws Exception {
        super.setupTestScenario();
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        if (userRepository.findOneByLogin(testPrefix + "student42").isEmpty()) {
            userRepository.save(UserFactory.generateActivatedUser(testPrefix + "student42"));
        }
    }

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }

    @ParameterizedTest
    @EnumSource(value = CourseInformationSharingConfiguration.class, names = { "COMMUNICATION_ONLY", "DISABLED" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getConversationsOfUser_messagingFeatureDeactivated_shouldReturnForbidden(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        getConversationsOfUser_messagingDeactivated(courseInformationSharingConfiguration);

    }

    void getConversationsOfUser_messagingDeactivated(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);

        request.getList("/api/courses/" + exampleCourseId + "/conversations", HttpStatus.FORBIDDEN, ConversationDTO.class);

        // active messaging again
        setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getConversationsOfUser_shouldReturnConversationsWhereMember() throws Exception {
        // given
        var channel = createChannel(false, TEST_PREFIX + "1");
        addUsersToConversation(channel.getId(), "tutor1");
        var groupChat = createGroupChat("tutor1");
        hideConversation(groupChat.getId(), "tutor1");
        var oneToOneChat = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats/", List.of(testPrefix + "tutor1"), OneToOneChatDTO.class,
                HttpStatus.CREATED);
        var post = this.postInConversation(oneToOneChat.getId(), "instructor1");
        this.resetWebsocketMock();
        favoriteConversation(oneToOneChat.getId(), "tutor1");
        var channel2 = createChannel(false, TEST_PREFIX + "2");

        // then
        userUtilService.changeUser(testPrefix + "tutor1");
        var convOfUsers = request.getList("/api/courses/" + exampleCourseId + "/conversations", HttpStatus.OK, ConversationDTO.class);
        assertThat(convOfUsers).hasSize(3); // the channel2 is not returned because the user is not a member
        assertThat(convOfUsers).extracting(ConversationDTO::getId).containsExactlyInAnyOrder(channel.getId(), groupChat.getId(), oneToOneChat.getId());
        for (var conv : convOfUsers) {
            if (conv.getId().equals(channel.getId())) {
                assertThat(conv.getIsFavorite()).isFalse();
                assertThat(conv.getIsHidden()).isFalse();
            }
            else if (conv.getId().equals(groupChat.getId())) {
                assertThat(conv.getIsFavorite()).isFalse();
                assertThat(conv.getIsHidden()).isTrue();
            }
            else if (conv.getId().equals(oneToOneChat.getId())) {
                assertThat(conv.getIsFavorite()).isTrue();
                assertThat(conv.getIsHidden()).isFalse();
            }
            assertConversationDTOTransientProperties(conv, false, true, false, false);
        }
        grantChannelModeratorRole(channel.getId(), "tutor1");
        convOfUsers = request.getList("/api/courses/" + exampleCourseId + "/conversations", HttpStatus.OK, ConversationDTO.class);
        // check that the channel moderator role is correctly set
        convOfUsers.stream().filter(conv -> conv.getId().equals(channel.getId())).findFirst().ifPresent(conv -> assertThat(((ChannelDTO) conv).getIsChannelModerator()).isTrue());
        // check that creator is correctly set
        userUtilService.changeUser(testPrefix + "instructor1");
        var convOfInstructor = request.getList("/api/courses/" + exampleCourseId + "/conversations", HttpStatus.OK, ConversationDTO.class);
        // should be creator of all conversations
        assertThat(convOfInstructor).hasSize(4);
        assertThat(convOfInstructor).extracting(ConversationDTO::getId).containsExactlyInAnyOrder(channel.getId(), groupChat.getId(), oneToOneChat.getId(), channel2.getId());
        for (var conv : convOfInstructor) {
            assertThat(conv.getIsFavorite()).isFalse();
            assertThat(conv.getIsHidden()).isFalse();
            assertConversationDTOTransientProperties(conv, true, true, true, true);
        }
        // cleanup
        conversationMessageRepository.deleteById(post.getId());
        conversationRepository.deleteById(groupChat.getId());
        conversationRepository.deleteById(oneToOneChat.getId());
        conversationRepository.deleteById(channel.getId());
        conversationRepository.deleteById(channel2.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getConversationsOfUser_onlyFewDatabaseCalls() throws Exception {
        // given
        var channel = createChannel(false, TEST_PREFIX + "1");
        addUsersToConversation(channel.getId(), "tutor1");

        var groupChat = createGroupChat("tutor1");
        hideConversation(groupChat.getId(), "tutor1");

        var oneToOneChat = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats/", List.of(testPrefix + "tutor1"), OneToOneChatDTO.class,
                HttpStatus.CREATED);
        var post = this.postInConversation(oneToOneChat.getId(), "instructor1");
        this.resetWebsocketMock();
        favoriteConversation(oneToOneChat.getId(), "tutor1");

        var courseWideChannel = createChannel(false, TEST_PREFIX + "2");
        conversationUtilService.createCourseWideChannel(exampleCourse, "course-wide");
        // then
        // expected are 11 database calls independent of the number of conversations.
        // 5 calls are for user authentication checks, 6 calls are made for retrieving conversation related data
        assertThatDb(() -> request.getList("/api/courses/" + exampleCourseId + "/conversations", HttpStatus.OK, ConversationDTO.class)).hasBeenCalledTimes(11);

        // cleanup
        conversationMessageRepository.deleteById(post.getId());
        conversationRepository.deleteById(groupChat.getId());
        conversationRepository.deleteById(oneToOneChat.getId());
        conversationRepository.deleteById(channel.getId());
        conversationRepository.deleteById(courseWideChannel.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void shouldReturnChannelIfExerciseOrLectureOrExamHidden_asTutor() throws Exception {
        Course course = courseUtilService.createCourseWithMessagingEnabled();
        createExerciseAndExamAndLectureChannels(course, ZonedDateTime.now().plusDays(1), "tutor1");
        createExerciseAndExamAndLectureChannels(course, ZonedDateTime.now().minusDays(1), "tutor1");

        List<ConversationDTO> channelsOfUser = request.getList("/api/courses/" + course.getId() + "/conversations", HttpStatus.OK, ConversationDTO.class);

        assertThat(channelsOfUser).hasSize(6);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotReturnChannelIfExerciseOrLectureOrExamHidden_asStudent() throws Exception {
        Course course = courseUtilService.createCourseWithMessagingEnabled();
        createExerciseAndExamAndLectureChannels(course, ZonedDateTime.now().plusDays(1), "student1");
        List<Long> visibleChannelIds = createExerciseAndExamAndLectureChannels(course, ZonedDateTime.now().minusDays(1), "student1");

        List<ConversationDTO> channelsOfUser = request.getList("/api/courses/" + course.getId() + "/conversations", HttpStatus.OK, ConversationDTO.class);

        assertThat(channelsOfUser).hasSize(3);
        channelsOfUser.forEach(conv -> assertThat(conv.getId()).isIn(visibleChannelIds));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void switchFavoriteStatus_shouldSwitchFavoriteStatus() throws Exception {
        // given
        var channel = createChannel(false, TEST_PREFIX);
        addUsersToConversation(channel.getId(), "tutor1");
        // then
        userUtilService.changeUser(testPrefix + "tutor1");
        var trueParams = new LinkedMultiValueMap<String, String>();
        trueParams.add("isFavorite", String.valueOf(true));
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/favorite", HttpStatus.OK, trueParams);
        this.assertFavoriteStatus(channel.getId(), "tutor1", true);
        var falseParams = new LinkedMultiValueMap<String, String>();
        falseParams.add("isFavorite", String.valueOf(false));
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/favorite", HttpStatus.OK, falseParams);
        this.assertFavoriteStatus(channel.getId(), "tutor1", false);

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void switchFavoriteStatus_shouldSwitchFavoriteStatus_IfNoParticipant() throws Exception {
        // given
        Channel channel = conversationUtilService.createCourseWideChannel(exampleCourse, "course-wide");
        // then
        var trueParams = new LinkedMultiValueMap<String, String>();
        trueParams.add("isFavorite", String.valueOf(true));
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/favorite", HttpStatus.OK, trueParams);
        this.assertFavoriteStatus(channel.getId(), "student1", true);
        var falseParams = new LinkedMultiValueMap<String, String>();
        falseParams.add("isFavorite", String.valueOf(false));
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/favorite", HttpStatus.OK, falseParams);
        this.assertFavoriteStatus(channel.getId(), "student1", false);

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @EnumSource(value = CourseInformationSharingConfiguration.class, names = { "COMMUNICATION_ONLY", "DISABLED" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void switchFavoriteStatus_messagingFeatureDeactivated_shouldReturnForbidden(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        switchFavoriteStatus_messagingDeactivated(courseInformationSharingConfiguration);

    }

    void switchFavoriteStatus_messagingDeactivated(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        // given
        var channel = createChannel(false, TEST_PREFIX);

        setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);

        var trueParams = new LinkedMultiValueMap<String, String>();
        trueParams.add("isFavorite", String.valueOf(true));
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/favorite", HttpStatus.FORBIDDEN, trueParams);

        // active messaging again
        setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void switchHiddenStatus_shouldSwitchHiddenStatus() throws Exception {
        // given
        var channel = createChannel(false, TEST_PREFIX);
        addUsersToConversation(channel.getId(), "tutor1");
        // then
        userUtilService.changeUser(testPrefix + "tutor1");
        var trueParams = new LinkedMultiValueMap<String, String>();
        trueParams.add("isHidden", String.valueOf(true));
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/hidden", HttpStatus.OK, trueParams);
        this.assertHiddenStatus(channel.getId(), "tutor1", true);
        var falseParams = new LinkedMultiValueMap<String, String>();
        falseParams.add("isHidden", String.valueOf(false));
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/hidden", HttpStatus.OK, falseParams);
        this.assertHiddenStatus(channel.getId(), "tutor1", false);

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void switchHiddenStatus_shouldSwitchHiddenStatus_IfNoParticipant() throws Exception {
        // given
        Channel channel = conversationUtilService.createCourseWideChannel(exampleCourse, "course-wide");
        // then
        var trueParams = new LinkedMultiValueMap<String, String>();
        trueParams.add("isHidden", String.valueOf(true));
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/hidden", HttpStatus.OK, trueParams);
        this.assertHiddenStatus(channel.getId(), "student1", true);
        var falseParams = new LinkedMultiValueMap<String, String>();
        falseParams.add("isHidden", String.valueOf(false));
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/hidden", HttpStatus.OK, falseParams);
        this.assertHiddenStatus(channel.getId(), "student1", false);

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @EnumSource(value = CourseInformationSharingConfiguration.class, names = { "COMMUNICATION_ONLY", "DISABLED" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void switchHiddenStatus_messagingFeatureDeactivated_shouldReturnForbidden(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        switchHiddenStatus_messagingDeactivated(courseInformationSharingConfiguration);

    }

    void switchHiddenStatus_messagingDeactivated(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        // given
        var channel = createChannel(false, TEST_PREFIX);

        setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);

        var trueParams = new LinkedMultiValueMap<String, String>();
        trueParams.add("isHidden", String.valueOf(true));
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/hidden", HttpStatus.FORBIDDEN, trueParams);

        // active messaging again
        setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @EnumSource(value = CourseInformationSharingConfiguration.class, names = { "COMMUNICATION_ONLY", "DISABLED" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void searchConversationMembers_messagingFeatureDeactivated_shouldReturnForbidden(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        searchConversationMembers_messagingDeactivated(courseInformationSharingConfiguration);

    }

    void searchConversationMembers_messagingDeactivated(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        // given
        var channel = createChannel(false, TEST_PREFIX);

        setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);

        var params = new LinkedMultiValueMap<String, String>();
        params.add("loginOrName", "");
        params.add("sort", "firstName,asc");
        params.add("sort", "lastName,asc");
        params.add("page", "0");
        params.add("size", "20");
        request.getList("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/members/search", HttpStatus.FORBIDDEN, ConversationUserDTO.class, params);

        // active messaging again
        setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void searchMembersOfConversation_shouldFindMembersWhereLoginOrNameMatches() throws Exception {
        var channel = createChannel(false, TEST_PREFIX);
        addUsersToConversation(channel.getId(), "student1");
        addUsersToConversation(channel.getId(), "editor1");
        addUsersToConversation(channel.getId(), "tutor1");
        grantChannelModeratorRole(channel.getId(), "tutor1");

        // search for students
        userUtilService.changeUser(testPrefix + "tutor1");
        // <server>/api/courses/:courseId/conversations/:conversationId/members/search?loginOrName=:searchTerm&sort=firstName,asc&sort=lastName,asc&page=0&size=10
        // optional filter attribute to further : filter=INSTRUCTOR or EDITOR or TUTOR or STUDENT or CHANNEL_MODERATOR
        var params = new LinkedMultiValueMap<String, String>();
        params.add("loginOrName", "");
        params.add("sort", "firstName,asc");
        params.add("sort", "lastName,asc");
        params.add("page", "0");
        params.add("size", "20");
        var members = request.getList("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/members/search", HttpStatus.OK, ConversationUserDTO.class,
                params);
        assertThat(members).hasSize(4);
        assertThat(members).extracting(ConversationUserDTO::getLogin).containsExactlyInAnyOrder(testPrefix + "student1", testPrefix + "tutor1", testPrefix + "instructor1",
                testPrefix + "editor1");
        // same request but now we only search for editor1
        params.set("loginOrName", testPrefix + "editor1");
        members = request.getList("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/members/search", HttpStatus.OK, ConversationUserDTO.class, params);
        assertThat(members).hasSize(1);
        assertThat(members).extracting(ConversationUserDTO::getLogin).containsExactlyInAnyOrder(testPrefix + "editor1");
        params.set("loginOrName", "");
        // same request but now we only search for students
        params.set("filter", "STUDENT");
        members = request.getList("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/members/search", HttpStatus.OK, ConversationUserDTO.class, params);
        assertThat(members).hasSize(1);
        assertThat(members).extracting(ConversationUserDTO::getLogin).containsExactlyInAnyOrder(testPrefix + "student1");
        // same request but now we only search for tutors (this will also include editors)
        params.set("filter", "TUTOR");
        members = request.getList("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/members/search", HttpStatus.OK, ConversationUserDTO.class, params);
        assertThat(members).hasSize(2);
        assertThat(members).extracting(ConversationUserDTO::getLogin).containsExactlyInAnyOrder(testPrefix + "tutor1", testPrefix + "editor1");
        // same request but now we only search for instructors
        params.set("filter", "INSTRUCTOR");
        members = request.getList("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/members/search", HttpStatus.OK, ConversationUserDTO.class, params);
        assertThat(members).hasSize(1);
        assertThat(members).extracting(ConversationUserDTO::getLogin).containsExactlyInAnyOrder(testPrefix + "instructor1");
        // same request but now we only search for channel moderators
        params.set("filter", "CHANNEL_MODERATOR");
        members = request.getList("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/members/search", HttpStatus.OK, ConversationUserDTO.class, params);
        assertThat(members).hasSize(2);
        assertThat(members).extracting(ConversationUserDTO::getLogin).containsExactlyInAnyOrder(testPrefix + "tutor1", testPrefix + "instructor1");

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void unreadMessages_shouldReturnCorrectValue_NoMessage() throws Exception {
        boolean unreadMessages = request.get("/api/courses/" + exampleCourseId + "/unread-messages", HttpStatus.OK, Boolean.class);
        assertThat(unreadMessages).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void unreadMessages_shouldReturnCorrectValue_Message() throws Exception {
        var oneToOneChat = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/one-to-one-chats/", List.of(testPrefix + "tutor1"), OneToOneChatDTO.class,
                HttpStatus.CREATED);
        this.postInConversation(oneToOneChat.getId(), "instructor1");

        userUtilService.changeUser(testPrefix + "tutor1");

        boolean unreadMessages = request.get("/api/courses/" + exampleCourseId + "/unread-messages", HttpStatus.OK, Boolean.class);
        assertThat(unreadMessages).isTrue();
    }

    private void assertConversationDTOTransientProperties(ConversationDTO conversationDTO, Boolean isCreator, Boolean isMember, Boolean hasChannelModerationRights,
            Boolean isChannelModerator) {
        assertThat(conversationDTO.getIsCreator()).isEqualTo(isCreator);
        assertThat(conversationDTO.getIsMember()).isEqualTo(isMember);

        if (conversationDTO instanceof ChannelDTO) {
            assertThat(((ChannelDTO) conversationDTO).getHasChannelModerationRights()).isEqualTo(hasChannelModerationRights);
            assertThat(((ChannelDTO) conversationDTO).getIsChannelModerator()).isEqualTo(isChannelModerator);
        }
    }

    private void assertFavoriteStatus(Long channelId, String userLoginWithoutPrefix, Boolean expectedFavoriteStatus) {
        var user = userUtilService.getUserByLogin(testPrefix + userLoginWithoutPrefix);
        var participant = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channelId, user.getId());
        assertThat(participant.orElseThrow().getIsFavorite()).isEqualTo(expectedFavoriteStatus);
    }

    private void assertHiddenStatus(Long channelId, String userLoginWithoutPrefix, Boolean expectedHiddenStatus) {
        var user = userUtilService.getUserByLogin(testPrefix + userLoginWithoutPrefix);
        var participant = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channelId, user.getId());
        assertThat(participant.orElseThrow().getIsHidden()).isEqualTo(expectedHiddenStatus);
    }

    private List<Long> createExerciseAndExamAndLectureChannels(Course course, ZonedDateTime visibleFrom, String userLoginWithoutPrefix) {
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, visibleFrom, visibleFrom, visibleFrom);
        Channel exerciseChannel = exerciseUtilService.addChannelToExercise(textExercise);
        addUsersToConversation(exerciseChannel.getId(), userLoginWithoutPrefix);

        Exam exam = examUtilService.addExam(course, visibleFrom, visibleFrom, visibleFrom);
        Channel examChannel = examUtilService.addExamChannel(exam, "test");
        addUsersToConversation(examChannel.getId(), userLoginWithoutPrefix);

        Lecture lecture = lectureUtilService.createLecture(course, visibleFrom);
        Channel lectureChannel = lectureUtilService.addLectureChannel(lecture);
        addUsersToConversation(lectureChannel.getId(), userLoginWithoutPrefix);

        return List.of(exerciseChannel.getId(), examChannel.getId(), lectureChannel.getId());
    }

}
