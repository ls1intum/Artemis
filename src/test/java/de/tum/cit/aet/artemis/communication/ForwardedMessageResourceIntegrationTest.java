package de.tum.cit.aet.artemis.communication;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.ForwardedMessage;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.test_repository.ForwardedMessageTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationFactory;
import de.tum.cit.aet.artemis.core.domain.User;

class ForwardedMessageResourceIntegrationTest extends AbstractConversationTest {

    private static final String TEST_PREFIX = "sptest";

    @Autowired
    private ForwardedMessageTestRepository forwardedMessageRepository;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;

    private Post testPost;

    private AnswerPost testAnswerPost;

    private ForwardedMessage testForwardedMessage;

    private ForwardedMessage forwardedMessageForAnswer;

    @BeforeEach
    void setUp() {
        testUser = userUtilService.createAndSaveUser(TEST_PREFIX + "student1");

        ConversationFactory factory = new ConversationFactory();
        Conversation conversation = factory.generatePublicChannel(exampleCourse, "Test ForwardedMessage Channel", true);
        conversation = conversationRepository.save(conversation);

        testPost = ConversationFactory.createBasicPost(1, testUser);
        testPost.setConversation(conversation);
        testPost = conversationMessageRepository.save(testPost);

        testAnswerPost = new AnswerPost();
        testAnswerPost.setContent("Test Answer Post Content");
        testAnswerPost.setPost(testPost);
        testAnswerPost.setAuthor(testUser);
        testAnswerPost = answerPostRepository.save(testAnswerPost);

        testForwardedMessage = new ForwardedMessage();
        testForwardedMessage.setDestinationPost(testPost);
        testForwardedMessage.setSourceId(testPost.getId());
        testForwardedMessage.setSourceType(PostingType.POST);
        testForwardedMessage = forwardedMessageRepository.save(testForwardedMessage);

        forwardedMessageForAnswer = new ForwardedMessage();
        forwardedMessageForAnswer.setDestinationAnswerPost(testAnswerPost);
        forwardedMessageForAnswer.setSourceId(testPost.getId());
        forwardedMessageForAnswer.setSourceType(PostingType.POST);
        forwardedMessageRepository.save(forwardedMessageForAnswer);
    }

    /**
     * Test creating a new ForwardedMessage.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldCreateForwardedMessage() throws Exception {
        ForwardedMessage newForwardedMessage = new ForwardedMessage();
        newForwardedMessage.setDestinationPost(testPost);
        newForwardedMessage.setSourceId(testPost.getId());
        newForwardedMessage.setSourceType(PostingType.POST);

        URI uri = new URI("/api/forwarded-messages");

        request.performMvcRequest(MockMvcRequestBuilders.post(uri).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(newForwardedMessage)))
                .andExpect(MockMvcResultMatchers.status().isCreated()).andExpect(jsonPath("$.id").isNumber()).andExpect(jsonPath("$.destinationPostId").value(testPost.getId()))
                .andExpect(jsonPath("$.sourceId").value(testPost.getId())).andExpect(jsonPath("$.sourceType").value("POST"));
    }

    /**
     * Test retrieving forwarded messages for destination IDs of type 'post'.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnForwardedMessagesForPosts() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.get("/api/forwarded-messages").param("ids", String.valueOf(testPost.getId())).param("type", "post"))
                .andExpect(MockMvcResultMatchers.status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(testPost.getId()))
                .andExpect(jsonPath("$[0].messages", hasSize(1))).andExpect(jsonPath("$[0].messages[0].id").value(testForwardedMessage.getId()))
                .andExpect(jsonPath("$[0].messages[0].sourceId").value(testForwardedMessage.getSourceId())).andExpect(jsonPath("$[0].messages[0].sourceType").value("POST"));
    }

    /**
     * Test retrieving forwarded messages for destination IDs of type 'answer'.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnForwardedMessagesForAnswers() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.get("/api/forwarded-messages").param("ids", String.valueOf(testAnswerPost.getId())).param("type", "answer"))
                .andExpect(MockMvcResultMatchers.status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(testAnswerPost.getId()))
                .andExpect(jsonPath("$[0].messages", hasSize(1))).andExpect(jsonPath("$[0].messages[0].id").value(forwardedMessageForAnswer.getId()))
                .andExpect(jsonPath("$[0].messages[0].sourceId").value(forwardedMessageForAnswer.getSourceId())).andExpect(jsonPath("$[0].messages[0].sourceType").value("POST"));
    }

    /**
     * Test retrieving forwarded messages with no results.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnEmptyWhenNoForwardedMessagesExist() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.get("/api/forwarded-messages").param("ids", "9999").param("type", "post")).andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    /**
     * Test invalid type handling.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnBadRequestForInvalidType() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.get("/api/forwarded-messages").param("ids", String.valueOf(testPost.getId())).param("type", "invalidType"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    /**
     * Test creating a ForwardedMessage for an AnswerPost.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldCreateForwardedMessageForAnswerPost() throws Exception {
        ForwardedMessage newForwardedMessage = new ForwardedMessage();
        newForwardedMessage.setDestinationAnswerPost(testAnswerPost);
        newForwardedMessage.setSourceId(testPost.getId());
        newForwardedMessage.setSourceType(PostingType.POST);

        URI uri = new URI("/api/forwarded-messages");

        request.performMvcRequest(MockMvcRequestBuilders.post(uri).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(newForwardedMessage)))
                .andExpect(MockMvcResultMatchers.status().isCreated()).andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.destinationAnswerPostId").value(testAnswerPost.getId())).andExpect(jsonPath("$.sourceId").value(testPost.getId()))
                .andExpect(jsonPath("$.sourceType").value("POST"));
    }

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }
}
