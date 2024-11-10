package de.tum.cit.aet.artemis.communication;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.SavedPost;
import de.tum.cit.aet.artemis.communication.domain.SavedPostStatus;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.SavedPostRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationFactory;
import de.tum.cit.aet.artemis.core.domain.User;

class SavedPostResourceIntegrationTest extends AbstractConversationTest {

    private static final String TEST_PREFIX = "sptest";

    @Mock
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private SavedPostRepository savedPostRepository;

    private Post testPost;

    @BeforeEach
    void setUp() {
        User testUser = userUtilService.createAndSaveUser(TEST_PREFIX + "student1");
        testPost = ConversationFactory.createBasicPost(1, testUser);
        AnswerPost testAnswerPost = new AnswerPost();
        testAnswerPost.setPost(testPost);
        Conversation conversation = ConversationFactory.generatePublicChannel(exampleCourse, "Test Channel", true);
        conversationRepository.save(conversation);
        testPost.setConversation(conversation);
        conversationMessageRepository.save(testPost);
        answerPostRepository.save(testAnswerPost);

        SavedPost savedPost = ConversationFactory.generateSavedPost(testUser, testPost, PostingType.POST, SavedPostStatus.IN_PROGRESS);
        savedPostRepository.save(savedPost);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnPostsWhenGetSavedPostIsCalled() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.get("/api/saved-posts/" + exampleCourseId + "/" + SavedPostStatus.IN_PROGRESS.getDatabaseKey()))
                .andExpect(MockMvcResultMatchers.status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(testPost.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnBadRequestWhenWrongTypeIsSupplied() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.post("/api/saved-posts/{postId}/{type}", testPost.getId(), "invalid_type"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnOkWhenUpdatingProperStatus() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.put("/api/saved-posts/{postId}/{type}?status={status}", testPost.getId(), PostingType.POST, SavedPostStatus.COMPLETED))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnNoContentWhenDeletingSavedPost() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.delete("/api/saved-posts/{postId}/{type}", testPost.getId(), PostingType.POST.getDatabaseKey()))
                .andExpect(MockMvcResultMatchers.status().isNoContent());
    }

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }
}
