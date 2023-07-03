package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.CourseInformationSharingConfiguration;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.post.ConversationUtilService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.metis.AnswerPostRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.websocket.dto.metis.PostDTO;

class AnswerMessageIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "answermessageint";

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    private List<Post> existingConversationPostsWithAnswers;

    private List<Post> existingPostsWithAnswersCourseWide;

    private Long courseId;

    @BeforeEach
    void initTestCase() {

        userUtilService.addUsers(TEST_PREFIX, 4, 4, 4, 1);

        // initialize test setup and get all existing posts with answers (four posts, one in each context, are initialized with one answer each): 4 answers in total (with author
        // student1)
        List<Post> existingPostsAndConversationPostsWithAnswers = conversationUtilService.createPostsWithAnswerPostsWithinCourse(TEST_PREFIX).stream()
                .filter(coursePost -> (coursePost.getAnswers() != null)).toList();

        List<Post> existingPostsWithAnswers = existingPostsAndConversationPostsWithAnswers.stream().filter(post -> post.getConversation() == null).toList();

        existingConversationPostsWithAnswers = existingPostsAndConversationPostsWithAnswers.stream().filter(post -> post.getConversation() != null).toList();

        // get all existing posts with answers in exercise context
        List<Post> existingPostsWithAnswersInExercise = existingPostsWithAnswers.stream()
                .filter(coursePost -> (coursePost.getAnswers() != null) && coursePost.getExercise() != null).toList();

        // get all existing posts with answers in lecture context
        existingPostsWithAnswersCourseWide = existingPostsWithAnswers.stream().filter(coursePost -> (coursePost.getAnswers() != null) && coursePost.getCourseWideContext() != null)
                .toList();

        Course course = existingPostsWithAnswersInExercise.get(0).getExercise().getCourseViaExerciseGroupOrCourseMember();
        courseUtilService.enableMessagingForCourse(course);
        courseId = course.getId();

        SimpMessageSendingOperations simpMessageSendingOperations = mock(SimpMessageSendingOperations.class);
        doNothing().when(simpMessageSendingOperations).convertAndSendToUser(any(), any(), any());
    }

    // CREATE

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testCreateConversationAnswerPost() throws Exception {
        AnswerPost answerPostToSave = createAnswerPost(existingConversationPostsWithAnswers.get(0));

        var countBefore = answerPostRepository.count();

        AnswerPost createdAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-messages", answerPostToSave, AnswerPost.class, HttpStatus.CREATED);
        conversationUtilService.assertSensitiveInformationHidden(createdAnswerPost);
        // should not be automatically post resolving
        assertThat(createdAnswerPost.doesResolvePost()).isFalse();
        checkCreatedAnswerPost(answerPostToSave, createdAnswerPost);
        assertThat(answerPostRepository.count()).isEqualTo(countBefore + 1);

        // both conversation participants should be notified
        verify(messagingTemplate, times(2)).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testMessagingNotAllowedIfCommunicationOnlySetting() throws Exception {
        messagingFeatureDisabledTest(CourseInformationSharingConfiguration.COMMUNICATION_ONLY);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testMessagingNotAllowedIfDisabledSetting() throws Exception {
        messagingFeatureDisabledTest(CourseInformationSharingConfiguration.DISABLED);
    }

    private void messagingFeatureDisabledTest(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        var persistedCourse = courseRepository.findByIdElseThrow(courseId);
        persistedCourse.setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);
        persistedCourse = courseRepository.saveAndFlush(persistedCourse);
        assertThat(persistedCourse.getCourseInformationSharingConfiguration()).isEqualTo(courseInformationSharingConfiguration);

        var countBefore = answerPostRepository.count();
        AnswerPost postToSave = createAnswerPost(existingConversationPostsWithAnswers.get(0));

        AnswerPost notCreatedAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-messages", postToSave, AnswerPost.class, HttpStatus.BAD_REQUEST);

        assertThat(notCreatedAnswerPost).isNull();
        assertThat(answerPostRepository.count()).isEqualTo(countBefore);

        // conversation participants should not be notified
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));

        // active messaging again
        persistedCourse.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        courseRepository.saveAndFlush(persistedCourse);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateConversationAnswerPost_badRequest() throws Exception {
        AnswerPost answerPostToSave = createAnswerPost(existingConversationPostsWithAnswers.get(0));
        answerPostToSave.setId(999L);

        var countBefore = answerPostRepository.count();

        AnswerPost notCreatedAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-messages", answerPostToSave, AnswerPost.class, HttpStatus.BAD_REQUEST);
        assertThat(notCreatedAnswerPost).isNull();
        assertThat(answerPostRepository.count()).isEqualTo(countBefore);

        // conversation participants should not be notified
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void testCreateConversationAnswerPost_forbidden() throws Exception {
        // only participants of a conversation can create posts for it
        // attempt to save new answerPost under someone elses conversation
        AnswerPost answerPostToSave = createAnswerPost(existingConversationPostsWithAnswers.get(0));

        var countBefore = answerPostRepository.count();

        AnswerPost notCreatedAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-messages", answerPostToSave, AnswerPost.class, HttpStatus.FORBIDDEN);

        assertThat(notCreatedAnswerPost).isNull();
        assertThat(answerPostRepository.count()).isEqualTo(countBefore);

        // conversation participants should not be notified
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    // UPDATE

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
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
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
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
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testEditAnswerPostWithIdIsNull_badRequest() throws Exception {
        // updated answerMessage and provided answerMessageId should match
        AnswerPost conversationAnswerPostToUpdate = existingConversationPostsWithAnswers.get(0).getAnswers().iterator().next();
        conversationAnswerPostToUpdate.setContent("User changes one of their conversation answerPosts");

        var countBefore = answerPostRepository.count();

        AnswerPost notUpdatedAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-messages/" + 999L, conversationAnswerPostToUpdate, AnswerPost.class,
                HttpStatus.BAD_REQUEST);

        assertThat(notUpdatedAnswerPost).isNull();
        assertThat(answerPostRepository.count()).isEqualTo(countBefore);

        // conversation participants should not be notified
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testEditAnswerPostWithInvalidId_badRequest() throws Exception {
        AnswerPost answerPostToUpdate = createAnswerPost(existingPostsWithAnswersCourseWide.get(0));

        AnswerPost updatedAnswerPostServer = request.putWithResponseBody("/api/courses/" + courseId + "/answer-messages/" + answerPostToUpdate.getId(), answerPostToUpdate,
                AnswerPost.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedAnswerPostServer).isNull();

        // conversation participants should not be notified
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testEditAnswerPostWithWrongCourseId_badRequest() throws Exception {
        AnswerPost answerPostToUpdate = createAnswerPost(existingPostsWithAnswersCourseWide.get(0));
        Course dummyCourse = courseUtilService.createCourse();

        AnswerPost updatedAnswerPostServer = request.putWithResponseBody("/api/courses/" + dummyCourse.getId() + "/answer-messages/" + answerPostToUpdate.getId(),
                answerPostToUpdate, AnswerPost.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedAnswerPostServer).isNull();

        // conversation participants should not be notified
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    // DELETE

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeleteAnswerPost_asTutor_notFound() throws Exception {
        var countBefore = answerPostRepository.count();
        request.delete("/api/courses/" + courseId + "/answer-messages/" + 999999999L, HttpStatus.NOT_FOUND);
        assertThat(answerPostRepository.count()).isEqualTo(countBefore);

        // conversation participants should not be notified
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testDeleteConversationPost() throws Exception {
        // conversation post of student1 must be deletable by them
        AnswerPost conversationAnswerPostToDelete = existingConversationPostsWithAnswers.get(0).getAnswers().iterator().next();
        request.delete("/api/courses/" + courseId + "/answer-messages/" + conversationAnswerPostToDelete.getId(), HttpStatus.OK);

        assertThat(answerPostRepository.findById(conversationAnswerPostToDelete.getId())).isEmpty();

        // both conversation participants should be notified
        verify(messagingTemplate, times(2)).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testDeleteConversationPost_forbidden() throws Exception {
        // conversation post of student1 must not be deletable by tutors
        AnswerPost conversationAnswerPostToDelete = existingConversationPostsWithAnswers.get(0).getAnswers().iterator().next();
        request.delete("/api/courses/" + courseId + "/answer-messages/" + conversationAnswerPostToDelete.getId(), HttpStatus.FORBIDDEN);

        assertThat(answerPostRepository.findById(conversationAnswerPostToDelete.getId())).isPresent();

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
