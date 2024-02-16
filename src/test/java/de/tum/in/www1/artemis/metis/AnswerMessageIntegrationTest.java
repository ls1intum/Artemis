package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.CourseInformationSharingConfiguration;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.notification.SingleUserNotification;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.lecture.LectureUtilService;
import de.tum.in.www1.artemis.post.ConversationUtilService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.metis.AnswerPostRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationMessageRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.websocket.dto.metis.PostDTO;

class AnswerMessageIntegrationTest extends AbstractSpringIntegrationIndependentTest {

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

    @Autowired
    private ConversationMessageRepository conversationMessageRepository;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    private List<Post> existingConversationPostsWithAnswers;

    private List<Post> existingPostsWithAnswersCourseWide;

    private List<Post> existingCourseWideMessages;

    private Long courseId;

    private User student1;

    @BeforeEach
    void initTestCase() {

        userUtilService.addUsers(TEST_PREFIX, 4, 4, 4, 1);
        student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        // initialize test setup and get all existing posts with answers (four posts, one in each context, are initialized with one answer each): 4 answers in total (with author
        // student1)
        List<Post> existingPostsAndConversationPosts = conversationUtilService.createPostsWithAnswerPostsWithinCourse(TEST_PREFIX);

        List<Post> existingPostsAndConversationPostsWithAnswers = existingPostsAndConversationPosts.stream()
                .filter(coursePost -> coursePost.getAnswers() != null && !coursePost.getAnswers().isEmpty()).toList();

        existingConversationPostsWithAnswers = existingPostsAndConversationPostsWithAnswers.stream().filter(post -> post.getConversation() != null).toList();

        existingCourseWideMessages = existingPostsAndConversationPosts.stream().filter(post -> post.getConversation() instanceof Channel channel && channel.getIsCourseWide())
                .toList();

        // get all existing posts with answers in exercise context
        List<Post> existingPostsWithAnswersInExercise = existingConversationPostsWithAnswers.stream()
                .filter(coursePost -> coursePost.getConversation() instanceof Channel channel && channel.getExercise() != null).toList();

        // get all existing posts with answers in lecture context
        existingPostsWithAnswersCourseWide = existingConversationPostsWithAnswers.stream()
                .filter(coursePost -> coursePost.getConversation() instanceof Channel channel && channel.getIsCourseWide()).toList();

        Course course = ((Channel) existingPostsWithAnswersInExercise.get(0).getConversation()).getExercise().getCourseViaExerciseGroupOrCourseMember();
        courseUtilService.enableMessagingForCourse(course);
        courseId = course.getId();
    }

    // CREATE

    @ParameterizedTest
    @MethodSource("courseInformationSharingConfigurationProvider")
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testCreateConversationAnswerPost(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        var persistedCourse = courseRepository.findByIdElseThrow(courseId);
        persistedCourse.setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);
        persistedCourse = courseRepository.saveAndFlush(persistedCourse);
        assertThat(persistedCourse.getCourseInformationSharingConfiguration()).isEqualTo(courseInformationSharingConfiguration);

        AnswerPost answerPostToSave = createAnswerPost(existingConversationPostsWithAnswers.get(2));

        var countBefore = answerPostRepository.count();

        AnswerPost createdAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-messages", answerPostToSave, AnswerPost.class, HttpStatus.CREATED);
        conversationUtilService.assertSensitiveInformationHidden(createdAnswerPost);
        // should not be automatically post resolving
        assertThat(createdAnswerPost.doesResolvePost()).isFalse();
        checkCreatedAnswerPost(answerPostToSave, createdAnswerPost);
        assertThat(answerPostRepository.count()).isEqualTo(countBefore + 1);

        // both conversation participants should be notified
        verify(websocketMessagingService, timeout(2000).times(2)).sendMessage(anyString(),
                (Object) argThat(argument -> argument instanceof PostDTO postDTO && postDTO.post().equals(existingConversationPostsWithAnswers.get(2))));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateAnswerInGeneralCourseWideChannel() throws Exception {
        testCreateChannelAnswer((Channel) existingConversationPostsWithAnswers.get(3).getConversation(), NotificationType.NEW_REPLY_FOR_COURSE_POST, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateAnswerInLectureChannel() throws Exception {
        Course course = courseRepository.findByIdElseThrow(courseId);
        Lecture lecture = lectureUtilService.createLecture(course, ZonedDateTime.now());
        Channel channel = lectureUtilService.addLectureChannel(lecture);
        testCreateChannelAnswer(channel, NotificationType.NEW_REPLY_FOR_LECTURE_POST, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateAnswerInExerciseChannel() throws Exception {
        Course course = courseRepository.findWithEagerExercisesById(courseId);
        Exercise exercise = course.getExercises().stream().findFirst().orElseThrow();
        Channel channel = exerciseUtilService.addChannelToExercise(exercise);
        testCreateChannelAnswer(channel, NotificationType.NEW_REPLY_FOR_EXERCISE_POST, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateAnswerInExamChannel() throws Exception {
        Course course = courseRepository.findByIdElseThrow(courseId);
        Exam exam = examUtilService.addExam(course);
        Channel channel = examUtilService.addExamChannel(exam, "exam channel");
        testCreateChannelAnswer(channel, NotificationType.NEW_REPLY_FOR_EXAM_POST, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateAnswerInPublicChannel() throws Exception {
        Course course = courseRepository.findByIdElseThrow(courseId);
        Channel channel = conversationUtilService.createPublicChannel(course, "test");
        conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1");
        conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student2");
        testCreateChannelAnswer(channel, NotificationType.CONVERSATION_NEW_REPLY_MESSAGE, 2);
    }

    private void testCreateChannelAnswer(Channel channel, NotificationType notificationType, int wantedNumberOfWSMessages) throws Exception {
        Post message = existingConversationPostsWithAnswers.get(0);
        message.setConversation(channel);
        Post savedMessage = conversationMessageRepository.save(message);

        AnswerPost answerPostToSave = createAnswerPost(savedMessage);

        var countBefore = answerPostRepository.count();

        AnswerPost createdAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-messages", answerPostToSave, AnswerPost.class, HttpStatus.CREATED);
        conversationUtilService.assertSensitiveInformationHidden(createdAnswerPost);
        // should not be automatically post resolving
        assertThat(createdAnswerPost.doesResolvePost()).isFalse();
        checkCreatedAnswerPost(answerPostToSave, createdAnswerPost);
        assertThat(answerPostRepository.count()).isEqualTo(countBefore + 1);

        // conversation participants should be notified
        verify(websocketMessagingService, timeout(2000).times(wantedNumberOfWSMessages)).sendMessage(anyString(),
                (Object) argThat(argument -> argument instanceof PostDTO postDTO && postDTO.post().equals(savedMessage)));

        verify(singleUserNotificationService, timeout(2000).times(1)).notifyUserAboutNewMessageReply(eq(createdAnswerPost), any(), any(), any(), eq(notificationType));
    }

    @ParameterizedTest
    @MethodSource("userMentionProvider")
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testCreateConversationAnswerPostWithUserMention(String userMention, boolean isUserMentionValid) throws Exception {
        AnswerPost answerPostToSave = createAnswerPost(existingConversationPostsWithAnswers.get(2));
        answerPostToSave.setContent(userMention);

        var countBefore = answerPostRepository.count();

        if (!isUserMentionValid) {
            request.postWithResponseBody("/api/courses/" + courseId + "/answer-messages", answerPostToSave, AnswerPost.class, HttpStatus.BAD_REQUEST);
            verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
            return;
        }

        AnswerPost createdAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-messages", answerPostToSave, AnswerPost.class, HttpStatus.CREATED);
        conversationUtilService.assertSensitiveInformationHidden(createdAnswerPost);
        // should not be automatically post resolving
        assertThat(createdAnswerPost.doesResolvePost()).isFalse();
        checkCreatedAnswerPost(answerPostToSave, createdAnswerPost);
        assertThat(answerPostRepository.count()).isEqualTo(countBefore + 1);

        // both conversation participants should be notified
        verify(websocketMessagingService, timeout(2000).times(2)).sendMessage(anyString(),
                (Object) argThat(argument -> argument instanceof PostDTO postDTO && postDTO.post().equals(existingConversationPostsWithAnswers.get(2))));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testCreateConversationAnswerPostWithUserMentionOfUserNotInConversation() throws Exception {
        AnswerPost answerPostToSave = createAnswerPost(existingConversationPostsWithAnswers.get(0));
        User mentionedUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        answerPostToSave.setContent("[user]" + mentionedUser.getName() + "(" + mentionedUser.getLogin() + ")[/user]");

        var countBefore = answerPostRepository.count();

        AnswerPost createdAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-messages", answerPostToSave, AnswerPost.class, HttpStatus.CREATED);
        conversationUtilService.assertSensitiveInformationHidden(createdAnswerPost);
        // should not be automatically post resolving
        assertThat(createdAnswerPost.doesResolvePost()).isFalse();
        checkCreatedAnswerPost(answerPostToSave, createdAnswerPost);
        assertThat(answerPostRepository.count()).isEqualTo(countBefore + 1);

        // mentioned user is not a member of the conversation and should not be notified
        verify(websocketMessagingService, never()).sendMessage(eq("/topic/user/" + mentionedUser.getId() + "/notifications"), any(SingleUserNotification.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testMessagingNotAllowedIfDisabledSetting() throws Exception {
        var persistedCourse = courseRepository.findByIdElseThrow(courseId);
        persistedCourse.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.DISABLED);
        persistedCourse = courseRepository.saveAndFlush(persistedCourse);
        assertThat(persistedCourse.getCourseInformationSharingConfiguration()).isEqualTo(CourseInformationSharingConfiguration.DISABLED);

        var countBefore = answerPostRepository.count();
        AnswerPost postToSave = createAnswerPost(existingConversationPostsWithAnswers.get(0));

        AnswerPost notCreatedAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-messages", postToSave, AnswerPost.class, HttpStatus.BAD_REQUEST);

        assertThat(notCreatedAnswerPost).isNull();
        assertThat(answerPostRepository.count()).isEqualTo(countBefore);

        // conversation participants should not be notified
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));

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
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void testCreateConversationAnswerPost_forbidden() throws Exception {
        // only participants of a conversation can create posts for it
        // attempt to save new answerPost under someone elses conversation
        AnswerPost answerPostToSave = createAnswerPost(existingConversationPostsWithAnswers.get(2));

        var countBefore = answerPostRepository.count();

        AnswerPost notCreatedAnswerPost = request.postWithResponseBody("/api/courses/" + courseId + "/answer-messages", answerPostToSave, AnswerPost.class, HttpStatus.FORBIDDEN);

        assertThat(notCreatedAnswerPost).isNull();
        assertThat(answerPostRepository.count()).isEqualTo(countBefore);

        // conversation participants should not be notified
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
    }

    // UPDATE

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testEditConversationAnswerPost() throws Exception {
        // conversation answerPost of student1 must be editable by them
        AnswerPost conversationAnswerPostToUpdate = existingConversationPostsWithAnswers.get(2).getAnswers().iterator().next();
        conversationAnswerPostToUpdate.setContent("User changes one of their conversation answerPosts");

        AnswerPost updatedAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-messages/" + conversationAnswerPostToUpdate.getId(),
                conversationAnswerPostToUpdate, AnswerPost.class, HttpStatus.OK);

        assertThat(conversationAnswerPostToUpdate).isEqualTo(updatedAnswerPost);

        // both conversation participants should be notified
        verify(websocketMessagingService, timeout(2000).times(2)).sendMessage(anyString(),
                (Object) argThat(argument -> argument instanceof PostDTO postDTO && postDTO.post().equals(updatedAnswerPost.getPost())));
    }

    @ParameterizedTest
    @MethodSource("userMentionProvider")
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testEditConversationAnswerPostWithUserMention(String userMention, boolean isUserMentionValid) throws Exception {
        // conversation answerPost of student1 must be editable by them
        AnswerPost conversationAnswerPostToUpdate = existingConversationPostsWithAnswers.get(0).getAnswers().iterator().next();
        conversationAnswerPostToUpdate.setContent("User changes one of their conversation answerPosts" + userMention);

        if (!isUserMentionValid) {
            request.putWithResponseBody("/api/courses/" + courseId + "/answer-messages/" + conversationAnswerPostToUpdate.getId(), conversationAnswerPostToUpdate, AnswerPost.class,
                    HttpStatus.BAD_REQUEST);
            verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
            return;
        }

        AnswerPost updatedAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-messages/" + conversationAnswerPostToUpdate.getId(),
                conversationAnswerPostToUpdate, AnswerPost.class, HttpStatus.OK);

        assertThat(conversationAnswerPostToUpdate).isEqualTo(updatedAnswerPost);

        // both conversation participants should be notified
        verify(websocketMessagingService, timeout(2000)).sendMessage(anyString(),
                (Object) argThat(argument -> argument instanceof PostDTO postDTO && postDTO.post().equals(existingConversationPostsWithAnswers.get(0))));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testMarkMessageAsResolved_asAuthor() throws Exception {
        // conversation post of student1 must be resolvable by them
        AnswerPost resolvingAnswerPost = existingConversationPostsWithAnswers.get(3).getAnswers().iterator().next();
        resolvingAnswerPost.setResolvesPost(true);

        AnswerPost updatedAnswerPost = request.putWithResponseBody("/api/courses/" + courseId + "/answer-messages/" + resolvingAnswerPost.getId(), resolvingAnswerPost,
                AnswerPost.class, HttpStatus.OK);

        assertThat(resolvingAnswerPost).isEqualTo(updatedAnswerPost);

        // confirm that the post is marked as resolved when it has a resolving answer
        assertThat(conversationMessageRepository.findMessagePostByIdElseThrow(updatedAnswerPost.getPost().getId()).isResolved()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseWideMessages_WithUnresolvedPosts() throws Exception {
        // filterToUnresolved set true; will fetch all unresolved posts of current course
        var params = new LinkedMultiValueMap<String, String>();
        params.add("courseWideChannelIds", "");
        params.add("filterToUnresolved", "true");
        params.add("size", "50");

        Set<Post> returnedPosts = request.getSet("/api/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        // get posts of current user and compare
        Set<Post> unresolvedPosts = existingCourseWideMessages.stream()
                .filter(post -> post.getAnswers().stream().noneMatch(answerPost -> Boolean.TRUE.equals(answerPost.doesResolvePost()))).collect(Collectors.toSet());

        assertThat(returnedPosts).isEqualTo(unresolvedPosts);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseWideMessages_WithOwnAndUnresolvedPosts() throws Exception {
        // filterToOwn & filterToUnresolved set true; will fetch all unresolved posts of current user
        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToUnresolved", "true");
        params.add("filterToOwn", "true");
        params.add("courseWideChannelIds", "");
        params.add("size", "50");

        Set<Post> returnedPosts = request.getSet("/api/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        // get unresolved posts of current user and compare
        Set<Post> resolvedPosts = existingCourseWideMessages.stream().filter(post -> student1.getId().equals(post.getAuthor().getId())
                && (post.getAnswers().stream().noneMatch(answerPost -> Boolean.TRUE.equals(answerPost.doesResolvePost())))).collect(Collectors.toSet());

        assertThat(returnedPosts).isEqualTo(resolvedPosts);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseWideMessages_WithOwnAndUnresolvedPostsWithCourseWideContent() throws Exception {
        // filterToOwn & filterToUnresolved set true; will fetch all unresolved posts of current user
        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToUnresolved", "true");
        params.add("filterToOwn", "true");
        params.add("courseWideChannelIds", "");

        Set<Post> returnedPosts = request.getSet("/api/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        // get unresolved posts of current user and compare
        Set<Post> resolvedPosts = existingCourseWideMessages.stream().filter(
                post -> student1.getId().equals(post.getAuthor().getId()) && post.getAnswers().stream().noneMatch(answerPost -> Boolean.TRUE.equals(answerPost.doesResolvePost())))
                .collect(Collectors.toSet());

        assertThat(returnedPosts).isEqualTo(resolvedPosts);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAnsweredOrReactedPostsByUserForCourse() throws Exception {

        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToAnsweredOrReacted", "true");
        params.add("courseWideChannelIds", "");

        Set<Post> returnedPosts = request.getSet("/api/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        Set<Post> filteredExistingCourseWideMessages = existingCourseWideMessages.stream()
                .filter(post -> post.getAnswers().stream().anyMatch(answerPost -> student1.getId().equals(answerPost.getAuthor().getId()))
                        || post.getReactions().stream().anyMatch(reaction -> student1.getId().equals(post.getAuthor().getId())))
                .collect(Collectors.toSet());

        assertThat(returnedPosts).isEqualTo(filteredExistingCourseWideMessages);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetOwnAndAnsweredOrReactedPostsByUserForCourse() throws Exception {

        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToAnsweredOrReacted", "true");
        params.add("filterToOwn", "true");
        params.add("courseWideChannelIds", "");

        Set<Post> returnedPosts = request.getSet("/api/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        Set<Post> filteredExistingCourseWideMessages = existingCourseWideMessages.stream().filter(
                post -> student1.getId().equals(post.getAuthor().getId()) && (post.getAnswers().stream().anyMatch(answerPost -> student1.getId().equals(post.getAuthor().getId()))
                        || post.getReactions().stream().anyMatch(reaction -> student1.getId().equals(post.getAuthor().getId()))))
                .collect(Collectors.toSet());

        assertThat(returnedPosts).isEqualTo(filteredExistingCourseWideMessages);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetUnresolvedAnsweredOrReactedPostsByUserForCourse() throws Exception {

        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToAnsweredOrReacted", "true");
        params.add("filterToUnresolved", "true");
        params.add("courseWideChannelIds", "");

        Set<Post> returnedPosts = request.getSet("/api/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        Set<Post> filteredExistingCourseWideMessages = existingCourseWideMessages.stream()
                .filter(post -> post.getAnswers().stream().noneMatch(answerPost -> Boolean.TRUE.equals(answerPost.doesResolvePost()))
                        && (post.getAnswers().stream().anyMatch(answerPost -> student1.getId().equals(answerPost.getAuthor().getId()))
                                || post.getReactions().stream().anyMatch(reaction -> student1.getId().equals(reaction.getUser().getId()))))
                .collect(Collectors.toSet());

        assertThat(returnedPosts).isEqualTo(filteredExistingCourseWideMessages);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetUnresolvedOwnAnsweredOrReactedPostsByUserForCourse() throws Exception {

        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToOwn", "true");
        params.add("filterToUnresolved", "true");
        params.add("filterToAnsweredOrReacted", "true");
        params.add("courseWideChannelIds", "");

        Set<Post> returnedPosts = request.getSet("/api/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        Set<Post> filteredExistingCourseWideMessages = existingCourseWideMessages.stream().filter(
                post -> student1.getId().equals(post.getAuthor().getId()) && (post.getAnswers().stream().noneMatch(answerPost -> Boolean.TRUE.equals(answerPost.doesResolvePost()))
                        && (post.getAnswers().stream().anyMatch(answerPost -> student1.getId().equals(post.getAuthor().getId()))
                                || post.getReactions().stream().anyMatch(reaction -> student1.getId().equals(reaction.getUser().getId())))))
                .collect(Collectors.toSet());

        assertThat(returnedPosts).isEqualTo(filteredExistingCourseWideMessages);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetOwnAndAnsweredOrReactedPostsByUserForCourseWithCourseWideContent() throws Exception {

        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToAnsweredOrReacted", "true");
        params.add("filterToOwn", "true");
        params.add("courseWideChannelIds", "");

        Set<Post> returnedPosts = request.getSet("/api/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        Set<Post> filteredExistingCourseWideMessages = existingCourseWideMessages.stream().filter(
                post -> student1.getId().equals(post.getAuthor().getId()) && (post.getAnswers().stream().anyMatch(answerPost -> student1.getId().equals(post.getAuthor().getId()))
                        || post.getReactions().stream().anyMatch(reaction -> student1.getId().equals(reaction.getUser().getId()))))
                .collect(Collectors.toSet());

        assertThat(returnedPosts).isEqualTo(filteredExistingCourseWideMessages);
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
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
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
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testEditAnswerPostWithInvalidId_badRequest() throws Exception {
        AnswerPost answerPostToUpdate = createAnswerPost(existingPostsWithAnswersCourseWide.get(0));

        AnswerPost updatedAnswerPostServer = request.putWithResponseBody("/api/courses/" + courseId + "/answer-messages/" + answerPostToUpdate.getId(), answerPostToUpdate,
                AnswerPost.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedAnswerPostServer).isNull();

        // conversation participants should not be notified
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
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
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
    }

    // DELETE

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeleteAnswerPost_asTutor_notFound() throws Exception {
        var countBefore = answerPostRepository.count();
        request.delete("/api/courses/" + courseId + "/answer-messages/" + 999999999L, HttpStatus.NOT_FOUND);
        assertThat(answerPostRepository.count()).isEqualTo(countBefore);

        // conversation participants should not be notified
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testDeleteConversationPost() throws Exception {
        // conversation post of student1 must be deletable by them
        AnswerPost conversationAnswerPostToDelete = existingConversationPostsWithAnswers.get(2).getAnswers().iterator().next();
        request.delete("/api/courses/" + courseId + "/answer-messages/" + conversationAnswerPostToDelete.getId(), HttpStatus.OK);

        assertThat(answerPostRepository.findById(conversationAnswerPostToDelete.getId())).isEmpty();

        // both conversation participants should be notified
        verify(websocketMessagingService, timeout(2000).times(2)).sendMessage(anyString(),
                (Object) argThat(argument -> argument instanceof PostDTO postDTO && postDTO.post().equals(existingConversationPostsWithAnswers.get(2))));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testDeleteConversationPost_forbidden() throws Exception {
        // conversation post of student1 must not be deletable by tutors
        AnswerPost conversationAnswerPostToDelete = existingConversationPostsWithAnswers.get(0).getAnswers().iterator().next();
        request.delete("/api/courses/" + courseId + "/answer-messages/" + conversationAnswerPostToDelete.getId(), HttpStatus.FORBIDDEN);

        assertThat(answerPostRepository.findById(conversationAnswerPostToDelete.getId())).isPresent();

        // conversation participants should not be notified
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
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

    protected static List<Arguments> userMentionProvider() {
        return userMentionProvider(TEST_PREFIX + "student1", TEST_PREFIX + "student2");
    }

    private static List<CourseInformationSharingConfiguration> courseInformationSharingConfigurationProvider() {
        return List.of(CourseInformationSharingConfiguration.MESSAGING_ONLY, CourseInformationSharingConfiguration.COMMUNICATION_ONLY,
                CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
    }
}
