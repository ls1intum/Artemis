package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationMessageRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ConversationRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.GroupChatRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.OneToOneChatRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.PostContextFilter;

/**
 * Contains useful methods for testing the channel feature
 */
abstract class AbstractConversationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ConversationRepository conversationRepository;

    @Autowired
    ConversationParticipantRepository conversationParticipantRepository;

    @Autowired
    ChannelRepository channelRepository;

    @Autowired
    GroupChatRepository groupChatRepository;

    @Autowired
    OneToOneChatRepository oneToOneChatRepository;

    @Autowired
    SimpMessageSendingOperations messagingTemplate;

    @Autowired
    ConversationMessageRepository conversationMessageRepository;

    Long exampleCourseId;

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    @BeforeEach
    void setupTestScenario() {
        // creating the users student1-student20, tutor1-tutor10, editor1-editor10 and instructor1-instructor10
        this.database.addUsers(20, 10, 10, 10);

        // Add users that are not in the course
        userRepository.save(ModelFactory.generateActivatedUser("student42"));
        userRepository.save(ModelFactory.generateActivatedUser("tutor42"));
        userRepository.save(ModelFactory.generateActivatedUser("editor42"));
        userRepository.save(ModelFactory.generateActivatedUser("instructor42"));

        var course = this.database.createCourse();
        courseRepository.save(course);

        exampleCourseId = course.getId();
    }

    Set<ConversationParticipant> getParticipants(Long conversationId) {
        return conversationParticipantRepository.findConversationParticipantByConversationId(conversationId);
    }

    void postInConversation(Long conversationId, String authorLogin) throws Exception {
        Post postToSave = createPostWithConversation(conversationId, authorLogin);

        Post createdPost = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/messages", postToSave, Post.class, HttpStatus.CREATED);
        assertThat(createdPost.getConversation().getId()).isEqualTo(conversationId);

        PostContextFilter postContextFilter = new PostContextFilter();
        postContextFilter.setConversationId(createdPost.getConversation().getId());
        assertThat(conversationMessageRepository.findMessages(postContextFilter, Pageable.unpaged())).hasSize(1);
    }

    Post createPostWithConversation(Long conversationId, String authorLogin) {
        Post post = new Post();
        post.setAuthor(database.getUserByLogin(authorLogin));
        post.setDisplayPriority(DisplayPriority.NONE);
        var conv = conversationRepository.findByIdElseThrow(conversationId);
        post.setConversation(conv);
        return post;
    }

    void assertParticipants(Long conversationId, int expectedSize, String... expectedUserLogins) {
        var participants = this.getParticipants(conversationId);
        assertThat(participants).hasSize(expectedSize);
        assertThat(participants).extracting(ConversationParticipant::getUser).extracting(User::getLogin).containsExactlyInAnyOrder(expectedUserLogins);
    }

    // ToDo: Add helper methods to assert the websocket crud methods

}
