package de.tum.in.www1.artemis.plagiarism;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.CourseInformationSharingConfiguration;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.UserRole;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.post.ConversationUtilService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationMessageRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.PostContextFilter;
import de.tum.in.www1.artemis.web.websocket.dto.metis.PostDTO;

class PlagiarismPostIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "postintegration";

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ConversationMessageRepository conversationMessageRepository;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    private List<Post> existingPosts;

    private List<Post> existingPlagiarismPosts;

    private List<Post> postsBelongingToExercise;

    private Long courseId;

    private Exercise exercise;

    private Channel exerciseChannel;

    private Long plagiarismCaseId;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 5, 5, 4, 4);

        // initialize test setup and get all existing posts (there are 8 posts with lecture context (4 per lecture), 8 with exercise context (4 per exercise),
        // 1 plagiarism case, 4 with course-wide context and 3 with conversation initialized - initialized): 24 posts in total
        List<Post> existingPostsAndConversationPosts = conversationUtilService.createPostsWithinCourse(TEST_PREFIX);

        existingPosts = existingPostsAndConversationPosts.stream().filter(post -> post.getConversation() == null).toList();

        List<Post> existingExercisePosts = existingPostsAndConversationPosts.stream()
                .filter(coursePost -> (coursePost.getConversation() instanceof Channel channel && channel.getExercise() != null)).toList();

        // filter existing posts with first exercise context
        exerciseChannel = ((Channel) existingExercisePosts.get(0).getConversation());
        exercise = exerciseChannel.getExercise();
        postsBelongingToExercise = existingExercisePosts.stream().filter(post -> (post.getConversation().getId().equals(exerciseChannel.getId()))).toList();

        // filter existing posts with plagiarism context
        existingPlagiarismPosts = existingPostsAndConversationPosts.stream().filter(coursePost -> coursePost.getPlagiarismCase() != null).toList();

        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();

        course.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.DISABLED);
        course = courseRepository.saveAndFlush(course);
        assertThat(course.getCourseInformationSharingConfiguration()).isEqualTo(CourseInformationSharingConfiguration.DISABLED);

        courseId = course.getId();

        plagiarismCaseId = existingPlagiarismPosts.get(0).getPlagiarismCase().getId();

        GroupNotificationService groupNotificationService = mock(GroupNotificationService.class);
        doNothing().when(groupNotificationService).notifyAllGroupsAboutNewAnnouncement(any(), any());

        // We do not need the stub and it leads to flakyness
        reset(javaMailSender);
    }

    // POST

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testPostingNotAllowedIfDisabledSetting() throws Exception {
        var persistedCourse = courseRepository.findByIdElseThrow(courseId);
        persistedCourse.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.DISABLED);
        persistedCourse = courseRepository.saveAndFlush(persistedCourse);
        assertThat(persistedCourse.getCourseInformationSharingConfiguration()).isEqualTo(CourseInformationSharingConfiguration.DISABLED);

        Post postToSave = createPostWithoutContext();

        Post notCreatedPost = request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.BAD_REQUEST);

        assertThat(notCreatedPost).isNull();
        PostContextFilter postContextFilter = new PostContextFilter(courseId);
        postContextFilter.setConversationId(exerciseChannel.getId());
        assertThat(postsBelongingToExercise).hasSameSizeAs(conversationMessageRepository.findMessages(postContextFilter, Pageable.unpaged(), 1));

        // conversation participants should not be notified
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));

        // active messaging again
        persistedCourse.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        courseRepository.saveAndFlush(persistedCourse);
    }

    @ParameterizedTest
    @MethodSource("userMentionProvider")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateCoursePostWithUserMention(String userMention, boolean isUserMentionValid) throws Exception {
        doNothing().when(singleUserNotificationService).notifyUserAboutNewPlagiarismCase(any(), any());

        Post postToSave = createPostWithoutContext();
        var plagiarismCase = new PlagiarismCase();
        plagiarismCase.setExercise(exercise);
        plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);
        postToSave.setPlagiarismCase(plagiarismCase);
        postToSave.setContent(userMention);

        if (!isUserMentionValid) {
            request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.BAD_REQUEST);
            return;
        }

        Post createdPost = request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.CREATED);
        conversationUtilService.assertSensitiveInformationHidden(createdPost);
        checkCreatedPost(postToSave, createdPost);

        List<Post> plagiarismPosts = postRepository.findPostsByPlagiarismCaseId(createdPost.getPlagiarismCase().getId());
        assertThat(plagiarismPosts).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreatePlagiarismPost() throws Exception {
        doNothing().when(singleUserNotificationService).notifyUserAboutNewPlagiarismCase(any(), any());

        Post postToSave = createPostWithoutContext();
        var plagiarismCase = new PlagiarismCase();
        plagiarismCase.setExercise(exercise);
        plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);
        postToSave.setPlagiarismCase(plagiarismCase);

        request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.CREATED);

        List<Post> updatedPlagiarismCasePosts = postRepository.findPostsByPlagiarismCaseId(plagiarismCase.getId());
        assertThat(updatedPlagiarismCasePosts).hasSize(1);
        verify(singleUserNotificationService).notifyUserAboutNewPlagiarismCase(any(), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExistingPost_badRequest() throws Exception {
        Post existingPostToSave = existingPosts.get(0);

        var sizeBefore = postRepository.findAll().size();

        request.postWithResponseBody("/api/courses/" + courseId + "/posts", existingPostToSave, Post.class, HttpStatus.BAD_REQUEST);
        assertThat(postRepository.findAll()).hasSize(sizeBefore);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreatePostWithoutPlagiarismCase_badRequest() throws Exception {
        Course course = conversationUtilService.createCourseWithPostsDisabled();
        courseId = course.getId();
        Post postToSave = createPostWithoutContext();

        request.postWithResponseBody("/api/courses/" + courseId + "/posts", postToSave, Post.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testValidatePostContextConstraintViolation() throws Exception {
        Post invalidPost = createPostWithoutContext();
        request.postWithResponseBody("/api/courses/" + courseId + "/posts", invalidPost, Post.class, HttpStatus.BAD_REQUEST);
    }

    // UPDATE

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testEditPost_asTutor() throws Exception {
        Post postToUpdate = editExistingPost(existingPlagiarismPosts.get(0));
        Post notUpdatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts/" + postToUpdate.getId(), postToUpdate, Post.class, HttpStatus.FORBIDDEN);
        assertThat(notUpdatedPost).isNull();
    }

    @ParameterizedTest
    @MethodSource("userMentionProvider")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testEditPostWithUserMention(String userMention, boolean isUserMentionValid) throws Exception {
        // update post of student1 (index 0)--> OK
        Post postToUpdate = editExistingPost(existingPosts.get(0));
        postToUpdate.setContent(userMention);

        if (!isUserMentionValid) {
            request.putWithResponseBody("/api/courses/" + courseId + "/posts/" + postToUpdate.getId(), postToUpdate, Post.class, HttpStatus.BAD_REQUEST);
            return;
        }

        Post updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts/" + postToUpdate.getId(), postToUpdate, Post.class, HttpStatus.OK);
        conversationUtilService.assertSensitiveInformationHidden(updatedPost);
        assertThat(updatedPost).isEqualTo(postToUpdate);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testEditPost_asStudent_forbidden() throws Exception {
        // update post from another student (index 1)--> forbidden
        Post postToNotUpdate = editExistingPost(existingPosts.get(0));

        Post notUpdatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts/" + postToNotUpdate.getId(), postToNotUpdate, Post.class, HttpStatus.FORBIDDEN);
        assertThat(notUpdatedPost).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testEditPostWithIdIsNull_badRequest() throws Exception {
        Post postToUpdate = existingPosts.get(0);
        postToUpdate.setId(null);

        Post updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/posts/" + postToUpdate.getId(), postToUpdate, Post.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedPost).isNull();
    }

    // GET

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetPostsForCourse_WithPlagiarismCaseIdRequestParam_asInstructor() throws Exception {
        // request param plagiarismCaseId will fetch all posts that match this context filter
        var params = new LinkedMultiValueMap<String, String>();
        params.add("plagiarismCaseId", plagiarismCaseId.toString());

        List<Post> returnedPosts = getPosts(params);
        // get amount of posts with certain plagiarism context
        assertThat(returnedPosts).hasSameSizeAs(existingPlagiarismPosts);
        assertThat(returnedPosts.get(0).getAuthorRole()).isEqualTo(UserRole.INSTRUCTOR);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPostsForCourse_WithPlagiarismCaseIdRequestParam_asStudent() throws Exception {
        // request param plagiarismCaseId will fetch all posts that match this context filter
        var params = new LinkedMultiValueMap<String, String>();
        params.add("plagiarismCaseId", plagiarismCaseId.toString());

        List<Post> returnedPosts = getPosts(params);
        // get amount of posts with certain plagiarism context
        assertThat(returnedPosts).hasSameSizeAs(existingPlagiarismPosts);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetPostsForCourse_WithPlagiarismCaseIdRequestParam_asStudent_Forbidden() throws Exception {
        // request param plagiarismCaseId will fetch all posts that match this context filter
        var params = new LinkedMultiValueMap<String, String>();
        params.add("plagiarismCaseId", plagiarismCaseId.toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.FORBIDDEN, Post.class, params);
        assertThat(returnedPosts).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testGetPostsForCourse_WithInvalidRequestParams_badRequest() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.BAD_REQUEST, Post.class, params);
        assertThat(returnedPosts).isNull();
    }

    // DELETE

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testDeletePost_asTutor_forbidden() throws Exception {
        Post postToNotDelete = existingPlagiarismPosts.get(0);

        request.delete("/api/courses/" + courseId + "/posts/" + postToNotDelete.getId(), HttpStatus.FORBIDDEN);
        assertThat(postRepository.findById(postToNotDelete.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeletePost_asInstructor() throws Exception {
        Post postToNotDelete = existingPlagiarismPosts.get(0);

        request.delete("/api/courses/" + courseId + "/posts/" + postToNotDelete.getId(), HttpStatus.OK);
        assertThat(postRepository.findById(postToNotDelete.getId())).isNotPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteNonExistentPosts_asTutor_notFound() throws Exception {
        // try to delete non-existing post
        request.delete("/api/courses/" + courseId + "/posts/" + 9999L, HttpStatus.NOT_FOUND);
    }

    // HELPER METHODS

    private Post createPostWithoutContext() {
        Post post = new Post();
        post.setTitle("Title Post");
        post.setContent("Content Post");
        post.setVisibleForStudents(true);
        post.setDisplayPriority(DisplayPriority.NONE);
        post.addTag("Tag");
        return post;
    }

    private Post editExistingPost(Post postToUpdate) {
        postToUpdate.setTitle("New Title");
        postToUpdate.setContent("New Test Post");
        postToUpdate.setVisibleForStudents(false);
        postToUpdate.addTag("New Tag");
        return postToUpdate;
    }

    private void checkCreatedPost(Post expectedPost, Post createdPost) {
        // check if post was created with id
        assertThat(createdPost).isNotNull();
        assertThat(createdPost.getId()).isNotNull();

        // check if title, content, creation date, and tags are set correctly on creation
        assertThat(createdPost.getTitle()).isEqualTo(expectedPost.getTitle());
        assertThat(createdPost.getContent()).isEqualTo(expectedPost.getContent());
        assertThat(createdPost.getCreationDate()).isNotNull();
        assertThat(createdPost.getTags()).isEqualTo(expectedPost.getTags());

        // check if default values are set correctly on creation
        assertThat(createdPost.getAnswers()).isEmpty();
        assertThat(createdPost.getReactions()).isEmpty();
        assertThat(createdPost.getDisplayPriority()).isEqualTo(expectedPost.getDisplayPriority());

        // check if plagiarismCase is set correctly on creation
        assertThat(createdPost.getPlagiarismCase()).isEqualTo(expectedPost.getPlagiarismCase());
    }

    @NotNull
    private List<Post> getPosts(LinkedMultiValueMap<String, String> params) throws Exception {
        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/posts", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        return returnedPosts;
    }

    protected static List<Arguments> userMentionProvider() {
        return userMentionProvider(TEST_PREFIX + "student1", TEST_PREFIX + "student2");
    }

}
