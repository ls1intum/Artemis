package de.tum.cit.aet.artemis.communication;

import static org.hamcrest.Matchers.hasSize;
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
        request.performMvcRequest(
                MockMvcRequestBuilders.get("/api/communication/saved-posts?courseId=" + exampleCourseId + "&status=" + SavedPostStatus.IN_PROGRESS.toString().toLowerCase()))
                .andExpect(MockMvcResultMatchers.status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(testPost.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnCreatedWhenPostIsBookmarkedAndIsNotBookmarkedYet() throws Exception {
        var newTestPost = ConversationFactory.createBasicPost(2, testUser);
        newTestPost.setConversation(conversation);
        conversationMessageRepository.save(newTestPost);

        request.performMvcRequest(
                MockMvcRequestBuilders.post("/api/communication/saved-posts/{postId}?type={type}", newTestPost.getId(), PostingType.POST.toString().toLowerCase()))
                .andExpect(MockMvcResultMatchers.status().isCreated());

        conversationMessageRepository.delete(newTestPost);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnMaxPostReachedWhenSavingAndLimitIsReached() throws Exception {
        int createEntries = 100;
        List<SavedPost> savedPosts = new ArrayList<>();

        for (int i = 0; i < createEntries; i++) {
            var testPostIteration = ConversationFactory.createBasicPost(i + 2, testUser);
            testPostIteration.setConversation(conversation);
            testPostIteration = conversationMessageRepository.save(testPostIteration);
            SavedPost savedPost = ConversationFactory.generateSavedPost(testUser, testPostIteration, PostingType.POST, SavedPostStatus.IN_PROGRESS);
            savedPostRepository.save(savedPost);
            savedPosts.add(savedPost);
        }

        request.performMvcRequest(MockMvcRequestBuilders.post("/api/communication/saved-posts/{postId}?type={type}", testPost.getId(), PostingType.POST.toString().toLowerCase()))
                .andExpect(MockMvcResultMatchers.status().isBadRequest()).andExpect(jsonPath("$.message").value("error.savedPostMaxReached"));

        // Cleanup
        savedPostRepository.deleteAll(savedPosts);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnOkWhenUpdatingProperStatus() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.put("/api/communication/saved-posts/{postId}?status={status}&type={type}", testPost.getId(),
                SavedPostStatus.COMPLETED.toString().toLowerCase(), PostingType.POST.toString().toLowerCase())).andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnNoContentWhenDeletingSavedPost() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.delete("/api/communication/saved-posts/{postId}?type={type}", testPost.getId(), PostingType.POST.toString().toLowerCase()))
                .andExpect(MockMvcResultMatchers.status().isNoContent());
    }

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }
}
