package de.tum.cit.aet.artemis.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.assessment.dto.UserNameAndLoginDTO;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;

class ConversationMentionIntegrationTest extends AbstractConversationTest {

    private static final String TEST_PREFIX = "conversationmentions";

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @BeforeEach
    @Override
    void setupTestScenario() throws Exception {
        super.setupTestScenario();
        userUtilService.addUsers(TEST_PREFIX, 3, 0, 0, 1);
        userUtilService.enrollPrefixedUsersInCourse(exampleCourse, TEST_PREFIX);
    }

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void suggestionsContainOnlyMembersOrderedByLatestMessageOrReply() throws Exception {
        var channel = createChannel(false, "mentions");
        addUsersToConversation(channel.getId(), "student1", "student2");
        var earlier = createPostWithConversation(channel.getId(), "student1");
        earlier.setContent("Earlier message");
        earlier.setCreationDate(ZonedDateTime.now().minusHours(2));
        earlier = conversationMessageRepository.save(earlier);
        var later = createPostWithConversation(channel.getId(), "student2");
        later.setContent("Later message");
        later.setCreationDate(ZonedDateTime.now().minusHours(1));
        conversationMessageRepository.save(later);
        var reply = new AnswerPost();
        reply.setPost(earlier);
        reply.setAuthor(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        reply.setContent("Latest reply");
        reply.setCreationDate(ZonedDateTime.now());
        answerPostRepository.save(reply);

        var suggestions = mentions(channel.getId(), "", HttpStatus.OK);
        assertThat(suggestions).extracting(UserNameAndLoginDTO::login).containsExactly(TEST_PREFIX + "student1", TEST_PREFIX + "student2", TEST_PREFIX + "instructor1");
        assertThat(mentions(channel.getId(), "STUDENT2", HttpStatus.OK)).extracting(UserNameAndLoginDTO::login).containsExactly(TEST_PREFIX + "student2");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void privateChannelMembersCannotBeSearchedByANonmember() throws Exception {
        var channel = createChannel(false, "private");
        userUtilService.changeUser(TEST_PREFIX + "student3");
        mentions(channel.getId(), "", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void instructorsCanSearchGroupChatMembersWithoutJoining() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "student1");
        var groupChat = createGroupChat("student2");
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        assertThat(conversationParticipantRepository.existsByConversationIdAndUserId(groupChat.getId(), userUtilService.getUserByLogin(TEST_PREFIX + "instructor1").getId()))
                .isFalse();
        assertThat(mentions(groupChat.getId(), "", HttpStatus.OK)).extracting(UserNameAndLoginDTO::login).containsExactly(TEST_PREFIX + "student1", TEST_PREFIX + "student2");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void courseWideChannelIncludesImplicitMembersWithoutParticipantRows() throws Exception {
        var channel = createCourseWideChannel("course-wide");
        assertThat(mentions(channel.getId(), "student", HttpStatus.OK)).extracting(UserNameAndLoginDTO::login).containsExactly(TEST_PREFIX + "student1", TEST_PREFIX + "student2",
                TEST_PREFIX + "student3");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void searchesTheAvailableNameWhenTheOtherNamePartIsNull() throws Exception {
        var channel = createCourseWideChannel("partial-names");
        var firstNameOnly = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        firstNameOnly.setFirstName("OnlyFirstName");
        firstNameOnly.setLastName(null);
        userRepository.saveAndFlush(firstNameOnly);
        var lastNameOnly = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        lastNameOnly.setFirstName(null);
        lastNameOnly.setLastName("OnlyLastName");
        userRepository.saveAndFlush(lastNameOnly);
        var nameless = userUtilService.getUserByLogin(TEST_PREFIX + "student3");
        nameless.setFirstName(null);
        nameless.setLastName(" \t");
        userRepository.saveAndFlush(nameless);

        assertThat(mentions(channel.getId(), "onlyfirst", HttpStatus.OK)).containsExactly(new UserNameAndLoginDTO("OnlyFirstName", firstNameOnly.getLogin()));
        assertThat(mentions(channel.getId(), "onlylast", HttpStatus.OK)).containsExactly(new UserNameAndLoginDTO("OnlyLastName", lastNameOnly.getLogin()));
        assertThat(mentions(channel.getId(), nameless.getLogin(), HttpStatus.OK)).containsExactly(new UserNameAndLoginDTO(nameless.getLogin(), nameless.getLogin()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "%", "_", "!" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void searchesWildcardAndEscapeCharactersLiterally(String symbol) throws Exception {
        var channel = createCourseWideChannel("literal-search");
        var matchingUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        matchingUser.setFirstName("Literal" + symbol + "Name");
        matchingUser.setLastName(null);
        userRepository.saveAndFlush(matchingUser);

        assertThat(mentions(channel.getId(), symbol, HttpStatus.OK)).containsExactly(new UserNameAndLoginDTO("Literal" + symbol + "Name", matchingUser.getLogin()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void limitsToTenEncodableNamesInStableLoginOrder() throws Exception {
        var channel = createCourseWideChannel("encodable-names");
        var expectedLogins = new ArrayList<String>();
        for (int index = 1; index <= 22; index++) {
            var login = TEST_PREFIX + String.format(Locale.ROOT, "studentrecipient%02d", index);
            var candidate = userUtilService.createAndSaveUser(login);
            candidate.setFirstName(index <= 10 ? "Candidate" + "[]()".charAt(index % 4) : "Candidate");
            candidate.setLastName("Member");
            userRepository.save(candidate);
            if (index > 10 && index <= 20) {
                expectedLogins.add(login);
            }
        }
        userUtilService.enrollPrefixedUsersInCourse(exampleCourse, TEST_PREFIX);

        assertThat(mentions(channel.getId(), "candidate", HttpStatus.OK)).extracting(UserNameAndLoginDTO::login).containsExactlyElementsOf(expectedLogins);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void ranksReplyOnlyMembersWithoutActivityFromOtherConversations() throws Exception {
        var channel = createChannel(false, "reply-activity");
        addUsersToConversation(channel.getId(), "student1", "student2", "student3");
        var post = createPostWithConversation(channel.getId(), "student1");
        post.setContent("A top-level message");
        post = conversationMessageRepository.save(post);
        var reply = new AnswerPost();
        reply.setPost(post);
        reply.setAuthor(userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
        reply.setContent("A reply without a top-level message");
        reply.setCreationDate(post.getCreationDate().plusMinutes(1));
        answerPostRepository.save(reply);
        var otherChannel = createChannel(false, "other-activity");
        var otherPost = createPostWithConversation(otherChannel.getId(), "student3");
        otherPost.setContent("A newer message in another conversation");
        otherPost.setCreationDate(post.getCreationDate().plusMinutes(2));
        conversationMessageRepository.save(otherPost);

        assertThat(mentions(channel.getId(), "", HttpStatus.OK)).extracting(UserNameAndLoginDTO::login).containsExactly(TEST_PREFIX + "student2", TEST_PREFIX + "student1",
                TEST_PREFIX + "instructor1", TEST_PREFIX + "student3");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void wrongCourseAndDisabledCommunicationAreRejected() throws Exception {
        var channel = createChannel(false, "course-check");
        var otherCourse = courseUtilService.createCourseWithMessagingEnabled();
        userUtilService.enrollUserInCourse(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), otherCourse, CourseRole.INSTRUCTOR);
        request.getList("/api/communication/courses/" + otherCourse.getId() + "/conversations/" + channel.getId() + "/members/mentions?loginOrName=", HttpStatus.FORBIDDEN,
                UserNameAndLoginDTO.class);
        setCourseInformationSharingConfiguration(de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration.DISABLED);
        mentions(channel.getId(), "", HttpStatus.FORBIDDEN);
    }

    private List<UserNameAndLoginDTO> mentions(long conversationId, String query, HttpStatus status) throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("loginOrName", query);
        return request.getList("/api/communication/courses/" + exampleCourseId + "/conversations/" + conversationId + "/members/mentions", status, UserNameAndLoginDTO.class,
                params);
    }
}
