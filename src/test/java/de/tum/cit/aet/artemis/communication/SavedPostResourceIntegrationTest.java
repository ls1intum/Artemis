package de.tum.cit.aet.artemis.communication;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.SavedPost;
import de.tum.cit.aet.artemis.communication.domain.SavedPostStatus;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.test_repository.SavedPostTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationFactory;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

class SavedPostResourceIntegrationTest extends AbstractConversationTest {

    private static final String TEST_PREFIX = "sptest";

    @Autowired
    private SavedPostTestRepository savedPostRepository;

    private User testUser;

    private Post testPost;

    private Conversation conversation;

    @BeforeEach
    void setUp() {
        testUser = userUtilService.createAndSaveUser(TEST_PREFIX + "student1");
        testPost = ConversationFactory.createBasicPost(1, testUser);
        conversation = ConversationFactory.generatePublicChannel(exampleCourse, "Test Channel", true);
        conversationRepository.save(conversation);
        testPost.setConversation(conversation);
        testPost = conversationMessageRepository.save(testPost);

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
    void shouldReturnBadRequestWhenWrongStatusIsSupplied() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.get("/api/saved-posts/" + exampleCourseId + "/" + "wrong-type")).andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(jsonPath("$.message").value("error.savedPostStatusDoesNotExist"))
                .andExpect(result -> assertInstanceOf(BadRequestAlertException.class, result.getResolvedException()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnCreatedWhenPostIsBookmarkedAndIsNotBookmarkedYet() throws Exception {
        var newTestPost = ConversationFactory.createBasicPost(2, testUser);
        newTestPost.setConversation(conversation);
        conversationMessageRepository.save(newTestPost);

        request.performMvcRequest(MockMvcRequestBuilders.post("/api/saved-posts/{postId}/{type}", newTestPost.getId(), PostingType.POST))
                .andExpect(MockMvcResultMatchers.status().isCreated());

        conversationMessageRepository.delete(newTestPost);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnBadRequestWhenWrongTypeIsSupplied() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.post("/api/saved-posts/{postId}/{type}", testPost.getId(), "invalid_type"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest()).andExpect(jsonPath("$.message").value("error.savedPostTypeDoesNotExist"))
                .andExpect(result -> assertInstanceOf(BadRequestAlertException.class, result.getResolvedException()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnMaxPostReachedWhenSavingAndLimitIsReached() throws Exception {
        int createEntries = 100;
        List<SavedPost> savedPosts = new ArrayList<>();

        for (int i = 0; i < createEntries; i++) {
            SavedPost savedPost = ConversationFactory.generateSavedPost(testUser, testPost, PostingType.POST, SavedPostStatus.IN_PROGRESS);
            savedPostRepository.save(savedPost);
            savedPosts.add(savedPost);
        }

        request.performMvcRequest(MockMvcRequestBuilders.post("/api/saved-posts/{postId}/{type}", testPost.getId(), PostingType.POST))
                .andExpect(MockMvcResultMatchers.status().isBadRequest()).andExpect(jsonPath("$.message").value("error.savedPostMaxReached"))
                .andExpect(result -> assertInstanceOf(BadRequestAlertException.class, result.getResolvedException()));

        // Cleanup
        savedPostRepository.deleteAll(savedPosts);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnOkWhenUpdatingProperStatus() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.put("/api/saved-posts/{postId}/{type}?status={status}", testPost.getId(), PostingType.POST, SavedPostStatus.COMPLETED))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnBadRequestWhenUpdatingAndWrongStatusIsSupplied() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.put("/api/saved-posts/{postId}/{type}?status={status}", testPost.getId(), PostingType.POST, "wrong-status"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest()).andExpect(jsonPath("$.message").value("error.savedPostStatusDoesNotExist"))
                .andExpect(result -> assertInstanceOf(BadRequestAlertException.class, result.getResolvedException()));
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
