package de.tum.in.www1.artemis.metis;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ConversationRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.GroupChatRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.OneToOneChatRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

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

    Long exampleCourseId;

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    @BeforeEach
    void setupTestScenario() {
        // creating the users student1-student10, tutor1-tutor10, editor1-editor10 and instructor1-instructor10
        this.database.addUsers(10, 10, 10, 10);

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

}
