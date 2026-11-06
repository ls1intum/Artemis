package de.tum.cit.aet.artemis.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.dto.UserNameAndLoginDTO;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;

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
    void courseWideChannelIncludesImplicitMembersWithoutParticipantRows() throws Exception {
        var channel = createCourseWideChannel("course-wide");
        assertThat(mentions(channel.getId(), "student", HttpStatus.OK)).extracting(UserNameAndLoginDTO::login).containsExactly(TEST_PREFIX + "student1", TEST_PREFIX + "student2",
                TEST_PREFIX + "student3");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void wrongCourseAndDisabledCommunicationAreRejected() throws Exception {
        var channel = createChannel(false, "course-check");
        var otherCourse = courseUtilService.createCourseWithMessagingEnabled();
        request.getList("/api/communication/courses/" + otherCourse.getId() + "/conversations/" + channel.getId() + "/members/mentions?loginOrName=", HttpStatus.FORBIDDEN,
                UserNameAndLoginDTO.class);
        setCourseInformationSharingConfiguration(de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration.DISABLED);
        mentions(channel.getId(), "", HttpStatus.FORBIDDEN);
    }

    private List<UserNameAndLoginDTO> mentions(long conversationId, String query, HttpStatus status) throws Exception {
        return request.getList("/api/communication/courses/" + exampleCourseId + "/conversations/" + conversationId + "/members/mentions?loginOrName=" + query, status,
                UserNameAndLoginDTO.class);
    }
}
