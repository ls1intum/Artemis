package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.metis.CourseWideContext;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.repository.metis.MessageRepository;
import de.tum.in.www1.artemis.web.rest.dto.PostContextFilter;
import de.tum.in.www1.artemis.web.websocket.dto.metis.PostDTO;

class MessageIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private MessageRepository messageRepository;

    private List<Post> existingPostsAndConversationPosts;

    private List<Post> existingConversationPosts;

    private List<Post> existingExercisePosts;

    private List<Post> existingLecturePosts;

    private Course course;

    private Long courseId;

    private Validator validator;

    @BeforeEach
    @WithMockUser(username = "student1", roles = "USER")
    void initTestCase() {
        // used to test hibernate validation using custom PostContextConstraintValidator
        validator = Validation.buildDefaultValidatorFactory().getValidator();

        database.addUsers(5, 5, 4, 1);

        // initialize test setup and get all existing posts
        // (there are 4 posts with lecture context, 4 with exercise context, 3 with course-wide context and 3 with conversation initialized): 14 posts in total
        existingPostsAndConversationPosts = database.createPostsWithinCourse();

        List<Post> existingPosts = existingPostsAndConversationPosts.stream().filter(post -> post.getConversation() == null).collect(Collectors.toList());

        // filters existing posts with conversation
        existingConversationPosts = existingPostsAndConversationPosts.stream().filter(post -> post.getConversation() != null).toList();

        // filter existing posts with exercise context
        existingExercisePosts = existingPosts.stream().filter(coursePost -> (coursePost.getExercise() != null)).collect(Collectors.toList());

        // filter existing posts with lecture context
        existingLecturePosts = existingPosts.stream().filter(coursePost -> (coursePost.getLecture() != null)).collect(Collectors.toList());

        course = existingExercisePosts.get(0).getExercise().getCourseViaExerciseGroupOrCourseMember();

        courseId = course.getId();

        SimpMessageSendingOperations simpMessageSendingOperations = mock(SimpMessageSendingOperations.class);
        doNothing().when(simpMessageSendingOperations).convertAndSendToUser(any(), any(), any());
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "USER")
    void testCreateConversationPost() throws Exception {
        Post postToSave = createPostWithConversation();

        Post createdPost = request.postWithResponseBody("/api/courses/" + courseId + "/messages", postToSave, Post.class, HttpStatus.CREATED);
        checkCreatedMessagePost(postToSave, createdPost);
        assertThat(createdPost.getConversation().getId()).isNotNull();

        PostContextFilter postContextFilter = new PostContextFilter();
        postContextFilter.setConversationId(createdPost.getConversation().getId());
        assertThat(messageRepository.findMessages(postContextFilter, Pageable.unpaged())).hasSize(1);

        // both conversation participants should be notified
        verify(messagingTemplate, times(2)).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = "student3", roles = "USER")
    void testCreateConversationPost_forbidden() throws Exception {
        // only participants of a conversation can create posts for it

        Post postToSave = createPostWithConversation();
        // attempt to save new post under someone else's conversation
        postToSave.setConversation(existingConversationPosts.get(0).getConversation());

        Post notCreatedPost = request.postWithResponseBody("/api/courses/" + courseId + "/messages", postToSave, Post.class, HttpStatus.FORBIDDEN);
        assertThat(notCreatedPost).isNull();
        assertThat(messageRepository.count()).isEqualTo(existingPostsAndConversationPosts.size());

        // conversation participants should not be notified
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testValidatePostContextConstraintViolation() throws Exception {
        Post invalidPost = createPostWithConversation();
        invalidPost.setCourseWideContext(CourseWideContext.RANDOM);
        request.postWithResponseBody("/api/courses/" + courseId + "/messages", invalidPost, Post.class, HttpStatus.BAD_REQUEST);
        Set<ConstraintViolation<Post>> constraintViolations = validator.validate(invalidPost);
        assertThat(constraintViolations).hasSize(1);

        invalidPost = createPostWithConversation();
        invalidPost.setLecture(existingLecturePosts.get(0).getLecture());
        request.postWithResponseBody("/api/courses/" + courseId + "/messages", invalidPost, Post.class, HttpStatus.BAD_REQUEST);
        constraintViolations = validator.validate(invalidPost);
        assertThat(constraintViolations).hasSize(1);

        invalidPost = createPostWithConversation();
        invalidPost.setCourseWideContext(CourseWideContext.ORGANIZATION);
        invalidPost.setExercise(existingExercisePosts.get(0).getExercise());
        request.postWithResponseBody("/api/courses/" + courseId + "/messages", invalidPost, Post.class, HttpStatus.BAD_REQUEST);
        constraintViolations = validator.validate(invalidPost);
        assertThat(constraintViolations).hasSize(1);

        invalidPost = createPostWithConversation();
        invalidPost.setLecture(existingLecturePosts.get(0).getLecture());
        invalidPost.setExercise(existingExercisePosts.get(0).getExercise());
        request.postWithResponseBody("/api/courses/" + courseId + "/messages", invalidPost, Post.class, HttpStatus.BAD_REQUEST);
        constraintViolations = validator.validate(invalidPost);
        assertThat(constraintViolations).hasSize(1);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "USER")
    void testGetConversationPost() throws Exception {
        // conversation set will fetch all posts of conversation if the user is involved
        var params = new LinkedMultiValueMap<String, String>();
        params.add("conversationId", existingConversationPosts.get(0).getConversation().getId().toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
        // get amount of posts with that certain
        assertThat(returnedPosts).hasSize(existingConversationPosts.size());
    }

    @Test
    @WithMockUser(username = "tutor1")
    void testEditConversationPost() throws Exception {
        // conversation post of tutor1 must be only editable by them
        Post conversationPostToUpdate = existingConversationPosts.get(0);
        conversationPostToUpdate.setContent("User changes one of their conversation posts");

        Post updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/messages/" + conversationPostToUpdate.getId(), conversationPostToUpdate, Post.class,
                HttpStatus.OK);

        assertThat(conversationPostToUpdate).isEqualTo(updatedPost);

        // both conversation participants should be notified about the update
        verify(messagingTemplate, times(2)).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void testEditConversationPost_forbidden() throws Exception {
        // conversation post of tutor1 must not be editable by tutor2
        Post conversationPostToUpdate = existingConversationPosts.get(0);
        conversationPostToUpdate.setContent("Tutor attempts to change some other user's conversation post");

        Post notUpdatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/messages/" + conversationPostToUpdate.getId(), conversationPostToUpdate, Post.class,
                HttpStatus.FORBIDDEN);

        assertThat(notUpdatedPost).isNull();

        // conversation participants should not be notified
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = "tutor1")
    void testDeleteConversationPost() throws Exception {
        // conversation post of tutor1 must be deletable by them
        Post conversationPostToDelete = existingConversationPosts.get(0);
        request.delete("/api/courses/" + courseId + "/messages/" + conversationPostToDelete.getId(), HttpStatus.OK);

        assertThat(messageRepository.count()).isEqualTo(existingPostsAndConversationPosts.size() - 1);
        // both conversation participants should be notified
        verify(messagingTemplate, times(2)).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void testDeleteConversationPost_forbidden() throws Exception {
        // conversation post of user must not be deletable by tutors
        Post conversationPostToDelete = existingConversationPosts.get(0);
        request.delete("/api/courses/" + courseId + "/messages/" + conversationPostToDelete.getId(), HttpStatus.FORBIDDEN);

        assertThat(messageRepository.count()).isEqualTo(existingPostsAndConversationPosts.size());
        // conversation participants should not be notified
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    private Post createPostWithConversation() {
        Post post = new Post();
        post.setAuthor(database.getUserByLogin("student1"));
        post.setDisplayPriority(DisplayPriority.NONE);
        post.setConversation(ConversationIntegrationTest.directConversationToCreate(course, database.getUserByLogin("student2")));
        return post;
    }

    private void checkCreatedMessagePost(Post expectedMessagePost, Post createdMessagePost) {
        // check if message post was created with id
        assertThat(createdMessagePost).isNotNull();
        assertThat(createdMessagePost.getId()).isNotNull();

        // check if content and creation date are set correctly on creation
        assertThat(createdMessagePost.getContent()).isEqualTo(expectedMessagePost.getContent());
        assertThat(createdMessagePost.getCreationDate()).isNotNull();

        // check if default values are set correctly on creation
        assertThat(createdMessagePost.getAnswers()).isEmpty();
        assertThat(createdMessagePost.getReactions()).isEmpty();
        assertThat(createdMessagePost.getDisplayPriority()).isEqualTo(expectedMessagePost.getDisplayPriority());

        // check if conversation is set correctly on creation
        assertThat(createdMessagePost.getConversation()).isNotNull();
        assertThat(createdMessagePost.getConversation().getId()).isNotNull();

        // conversation posts should not have course initialized
        assertThat(createdMessagePost.getCourse()).isNull();
    }
}
