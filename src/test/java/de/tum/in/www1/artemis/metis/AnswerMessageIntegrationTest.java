package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.repository.metis.AnswerPostRepository;
import de.tum.in.www1.artemis.web.websocket.dto.metis.PostDTO;

class AnswerMessageIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private AnswerPostRepository answerPostRepository;

    private List<Post> existingConversationPostsWithAnswers;

    private List<Post> existingPostsWithAnswersCourseWide;

    private List<AnswerPost> existingAnswerPosts;

    private Long courseId;

    @BeforeEach
    void initTestCase() {

        database.addUsers(5, 5, 4, 1);

        // initialize test setup and get all existing posts with answers (four posts, one in each context, are initialized with one answer each): 4 answers in total (with author
        // student1)
        List<Post> existingPostsAndConversationPostsWithAnswers = database.createPostsWithAnswerPostsWithinCourse().stream().filter(coursePost -> (coursePost.getAnswers() != null))
                .toList();

        List<Post> existingPostsWithAnswers = existingPostsAndConversationPostsWithAnswers.stream().filter(post -> post.getConversation() == null).collect(Collectors.toList());

        existingConversationPostsWithAnswers = existingPostsAndConversationPostsWithAnswers.stream().filter(post -> post.getConversation() != null).collect(Collectors.toList());

        // get all answerPosts
        existingAnswerPosts = existingPostsAndConversationPostsWithAnswers.stream().map(Post::getAnswers).flatMap(Collection::stream).toList();

        // get all existing posts with answers in exercise context
        List<Post> existingPostsWithAnswersInExercise = existingPostsWithAnswers.stream()
                .filter(coursePost -> (coursePost.getAnswers() != null) && coursePost.getExercise() != null).toList();

        // get all existing posts with answers in lecture context
        existingPostsWithAnswersCourseWide = existingPostsWithAnswers.stream().filter(coursePost -> (coursePost.getAnswers() != null) && coursePost.getCourseWideContext() != null)
                .toList();

        courseId = existingPostsWithAnswersInExercise.get(0).getExercise().getCourseViaExerciseGroupOrCourseMember().getId();

        SimpMessageSendingOperations simpMessageSendingOperations = mock(SimpMessageSendingOperations.class);
        doNothing().when(simpMessageSendingOperations).convertAndSendToUser(any(), any(), any());
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    // CREATE

    @Test
    @WithMockUser(username = "tutor1", roles = "USER")
    void testCreateConversationAnswerPost() throws Exception {
        AnswerPost answerPostToSave = createAnswerPost(existingConversationPostsWithAnswers.get(0));

        AnswerPost createdAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-messages", answerPostToSave, AnswerPost.class, HttpStatus.CREATED);
        database.assertSensitiveInformationHidden(createdAnswerPost);
        // should not be automatically post resolving
        assertThat(createdAnswerPost.doesResolvePost()).isFalse();
        checkCreatedAnswerPost(answerPostToSave, createdAnswerPost);
        assertThat(existingAnswerPosts.size() + 1).isEqualTo(answerPostRepository.count());

        // both conversation participants should be notified
        verify(messagingTemplate, times(2)).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateConversationAnswerPost_badRequest() throws Exception {
        AnswerPost answerPostToSave = createAnswerPost(existingConversationPostsWithAnswers.get(0));
        answerPostToSave.setId(999L);

        AnswerPost notCreatedAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-messages", answerPostToSave, AnswerPost.class, HttpStatus.BAD_REQUEST);
        assertThat(notCreatedAnswerPost).isNull();
        assertThat(answerPostRepository.count()).isEqualTo(existingAnswerPosts.size());

        // conversation participants should not be notified
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = "student3", roles = "USER")
    void testCreateConversationAnswerPost_forbidden() throws Exception {
        // only participants of a conversation can create posts for it
        // attempt to save new answerPost under someone elses conversation
        AnswerPost answerPostToSave = createAnswerPost(existingConversationPostsWithAnswers.get(0));
        AnswerPost notCreatedAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-messages", answerPostToSave, AnswerPost.class, HttpStatus.FORBIDDEN);

        assertThat(notCreatedAnswerPost).isNull();
        assertThat(answerPostRepository.count()).isEqualTo(existingAnswerPosts.size());

        // conversation participants should not be notified
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    // UPDATE

    @Test
    @WithMockUser(username = "student1")
    void testEditConversationAnswerPost() throws Exception {
        // conversation answerPost of student1 must be editable by them
        AnswerPost conversationAnswerPostToUpdate = existingConversationPostsWithAnswers.get(0).getAnswers().iterator().next();
        conversationAnswerPostToUpdate.setContent("User changes one of their conversation answerPosts");

        AnswerPost updatedAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-messages/" + conversationAnswerPostToUpdate.getId(),
                conversationAnswerPostToUpdate, AnswerPost.class, HttpStatus.OK);

        assertThat(conversationAnswerPostToUpdate).isEqualTo(updatedAnswerPost);

        // both conversation participants should be notified
        verify(messagingTemplate, times(2)).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testEditConversationAnswerPost_forbidden() throws Exception {
        // conversation answerPost of student1 must not be editable by tutors
        AnswerPost conversationAnswerPostToUpdate = existingConversationPostsWithAnswers.get(0).getAnswers().iterator().next();
        conversationAnswerPostToUpdate.setContent("Tutor attempts to change some other user's conversation answerPost");

        AnswerPost notUpdatedAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-messages/" + conversationAnswerPostToUpdate.getId(),
                conversationAnswerPostToUpdate, AnswerPost.class, HttpStatus.FORBIDDEN);

        assertThat(notUpdatedAnswerPost).isNull();

        // conversation participants should not be notified
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testEditAnswerPostWithIdIsNull_badRequest() throws Exception {
        // updated answerMessage and provided answerMessageId should match
        AnswerPost conversationAnswerPostToUpdate = existingConversationPostsWithAnswers.get(0).getAnswers().iterator().next();
        conversationAnswerPostToUpdate.setContent("User changes one of their conversation answerPosts");

        AnswerPost notUpdatedAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-messages/" + 999L, conversationAnswerPostToUpdate, AnswerPost.class,
                HttpStatus.BAD_REQUEST);

        assertThat(notUpdatedAnswerPost).isNull();
        assertThat(answerPostRepository.count()).isEqualTo(existingAnswerPosts.size());

        // conversation participants should not be notified
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testEditAnswerPostWithInvalidId_badRequest() throws Exception {
        AnswerPost answerPostToUpdate = createAnswerPost(existingPostsWithAnswersCourseWide.get(0));

        AnswerPost updatedAnswerPostServer = request.putWithResponseBody("/api/courses/" + courseId + "/answer-messages/" + answerPostToUpdate.getId(), answerPostToUpdate,
                AnswerPost.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedAnswerPostServer).isNull();

        // conversation participants should not be notified
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testEditAnswerPostWithWrongCourseId_badRequest() throws Exception {
        AnswerPost answerPostToUpdate = createAnswerPost(existingPostsWithAnswersCourseWide.get(0));
        Course dummyCourse = database.createCourse();

        AnswerPost updatedAnswerPostServer = request.putWithResponseBody("/api/courses/" + dummyCourse.getId() + "/answer-messages/" + answerPostToUpdate.getId(),
                answerPostToUpdate, AnswerPost.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedAnswerPostServer).isNull();

        // conversation participants should not be notified
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    // DELETE

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testDeleteAnswerPost_asTutor_notFound() throws Exception {
        request.delete("/api/courses/" + courseId + "/answer-messages/" + 9999L, HttpStatus.NOT_FOUND);
        assertThat(answerPostRepository.count()).isEqualTo(existingAnswerPosts.size());

        // conversation participants should not be notified
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = "student1")
    void testDeleteConversationPost() throws Exception {
        // conversation post of student1 must be deletable by them
        AnswerPost conversationAnswerPostToDelete = existingConversationPostsWithAnswers.get(0).getAnswers().iterator().next();
        request.delete("/api/courses/" + courseId + "/answer-messages/" + conversationAnswerPostToDelete.getId(), HttpStatus.OK);

        assertThat(answerPostRepository.count()).isEqualTo(existingAnswerPosts.size() - 1);

        // both conversation participants should be notified
        verify(messagingTemplate, times(2)).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testDeleteConversationPost_forbidden() throws Exception {
        // conversation post of student1 must not be deletable by tutors
        AnswerPost conversationAnswerPostToDelete = existingConversationPostsWithAnswers.get(0).getAnswers().iterator().next();
        request.delete("/api/courses/" + courseId + "/answer-messages/" + conversationAnswerPostToDelete.getId(), HttpStatus.FORBIDDEN);

        assertThat(answerPostRepository.count()).isEqualTo(existingAnswerPosts.size());

        // conversation participants should not be notified
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    // HELPER METHODS

    private AnswerPost createAnswerPost(Post post) {
        AnswerPost answerPost = new AnswerPost();
        answerPost.setContent("Content Answer Post");
        answerPost.setPost(post);
        post.addAnswerPost(answerPost);
        return answerPost;
    }

    private void checkCreatedAnswerPost(AnswerPost expectedAnswerPost, AnswerPost createdAnswerPost) {
        // check if answerPost was created with id
        assertThat(createdAnswerPost).isNotNull();
        assertThat(createdAnswerPost.getId()).isNotNull();

        // check if associated post, answerPost content, and creation date are set correctly on creation
        assertThat(createdAnswerPost.getPost()).isEqualTo(expectedAnswerPost.getPost());
        assertThat(createdAnswerPost.getContent()).isEqualTo(expectedAnswerPost.getContent());
        assertThat(createdAnswerPost.getCreationDate()).isNotNull();

        // check if default values are set correctly on creation
        assertThat(createdAnswerPost.getReactions()).isEmpty();
    }
}
