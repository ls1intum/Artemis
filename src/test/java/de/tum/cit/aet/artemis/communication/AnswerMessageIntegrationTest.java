package de.tum.cit.aet.artemis.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.CourseNotification;
import de.tum.cit.aet.artemis.communication.domain.NotificationType;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.notification.SingleUserNotification;
import de.tum.cit.aet.artemis.communication.dto.PostDTO;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.communication.test_repository.CourseNotificationTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class AnswerMessageIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "answermessageint";

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private ConversationMessageRepository conversationMessageRepository;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private CourseNotificationTestRepository courseNotificationRepository;

    @Autowired
    private FeatureToggleService featureToggleService;

    private List<Post> existingConversationPostsWithAnswers;

    private List<Post> existingPostsWithAnswersCourseWide;

    private List<Post> existingCourseWideMessages;

    private List<Long> existingCourseWideChannelIds;

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

        // filters course wide channels
        existingCourseWideChannelIds = existingPostsAndConversationPosts.stream().filter(post -> post.getConversation() instanceof Channel channel && channel.getIsCourseWide())
                .map(post -> post.getConversation().getId()).distinct().toList();

        // get all existing posts with answers in exercise context
        List<Post> existingPostsWithAnswersInExercise = existingConversationPostsWithAnswers.stream()
                .filter(coursePost -> coursePost.getConversation() instanceof Channel channel && channel.getExercise() != null).toList();

        // get all existing posts with answers in lecture context
        existingPostsWithAnswersCourseWide = existingConversationPostsWithAnswers.stream()
                .filter(coursePost -> coursePost.getConversation() instanceof Channel channel && channel.getIsCourseWide()).toList();

        Course course = ((Channel) existingPostsWithAnswersInExercise.getFirst().getConversation()).getExercise().getCourseViaExerciseGroupOrCourseMember();
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

        AnswerPost createdAnswerPost = request.postWithResponseBody("/api/communication/courses/" + courseId + "/answer-messages", answerPostToSave, AnswerPost.class,
                HttpStatus.CREATED);
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
        testCreateChannelAnswer((Channel) existingConversationPostsWithAnswers.get(3).getConversation(), 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateAnswerInLectureChannel() throws Exception {
        Course course = courseRepository.findByIdElseThrow(courseId);
        Lecture lecture = lectureUtilService.createLecture(course, ZonedDateTime.now());
        Channel channel = lectureUtilService.addLectureChannel(lecture);
        testCreateChannelAnswer(channel, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateAnswerInExerciseChannel() throws Exception {
        Course course = courseRepository.findWithEagerExercisesById(courseId);
        Exercise exercise = course.getExercises().iterator().next();
        Channel channel = exerciseUtilService.addChannelToExercise(exercise);
        testCreateChannelAnswer(channel, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateAnswerInExamChannel() throws Exception {
        Course course = courseRepository.findByIdElseThrow(courseId);
        Exam exam = examUtilService.addExam(course);
        Channel channel = examUtilService.addExamChannel(exam, "exam channel");
        testCreateChannelAnswer(channel, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateAnswerInPublicChannel() throws Exception {
        var channel = createChannelWithTwoStudents();
        testCreateChannelAnswer(channel, 2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testSendNotificationWhenDifferentUserAnswersPost() throws Exception {
        var channel = createChannelWithTwoStudents();
        var createdAnswerPost = testCreateChannelAnswer(channel, 2);
        verify(singleUserNotificationService, timeout(2000).times(1)).notifyUserAboutNewMessageReply(eq(createdAnswerPost), any(), any(), any(),
                eq(NotificationType.CONVERSATION_NEW_REPLY_MESSAGE));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDoNotSendNotificationWhenSameUserAnswersPost() throws Exception {
        var channel = createChannelWithTwoStudents();
        var createdAnswerPost = testCreateChannelAnswer(channel, 2);
        verify(singleUserNotificationService, timeout(2000).times(0)).notifyUserAboutNewMessageReply(eq(createdAnswerPost), any(), any(), any(),
                eq(NotificationType.CONVERSATION_NEW_REPLY_MESSAGE));
    }

    private Channel createChannelWithTwoStudents() {
        Course course = courseRepository.findByIdElseThrow(courseId);
        Channel channel = conversationUtilService.createPublicChannel(course, "test");
        conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1");
        conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student2");
        return channel;
    }

    private AnswerPost testCreateChannelAnswer(Channel channel, int wantedNumberOfWSMessages) throws Exception {
        Post message = existingConversationPostsWithAnswers.getFirst();
        message.setConversation(channel);
        Post savedMessage = conversationMessageRepository.save(message);

        AnswerPost answerPostToSave = createAnswerPost(savedMessage);

        var countBefore = answerPostRepository.count();

        // avoid sending too much information to the server (otherwise deserialization might not work for a nested object due to a strange test error)
        var conversation = savedMessage.getConversation();
        if (conversation instanceof Channel theChannel) {
            theChannel.setExam(null);
        }
        AnswerPost createdAnswerPost = request.postWithResponseBody("/api/communication/courses/" + courseId + "/answer-messages", answerPostToSave, AnswerPost.class,
                HttpStatus.CREATED);
        conversationUtilService.assertSensitiveInformationHidden(createdAnswerPost);
        // should not be automatically post resolving
        assertThat(createdAnswerPost.doesResolvePost()).isFalse();
        checkCreatedAnswerPost(answerPostToSave, createdAnswerPost);
        assertThat(answerPostRepository.count()).isEqualTo(countBefore + 1);

        // conversation participants should be notified
        verify(websocketMessagingService, timeout(2000).times(wantedNumberOfWSMessages)).sendMessage(anyString(),
                (Object) argThat(argument -> argument instanceof PostDTO postDTO && postDTO.post().equals(savedMessage)));

        return createdAnswerPost;
    }

    @ParameterizedTest
    @MethodSource("userMentionProvider")
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testCreateConversationAnswerPostWithUserMention(String userMention, boolean isUserMentionValid) throws Exception {
        AnswerPost answerPostToSave = createAnswerPost(existingConversationPostsWithAnswers.get(2));
        answerPostToSave.setContent(userMention);

        var countBefore = answerPostRepository.count();

        if (!isUserMentionValid) {
            request.postWithResponseBody("/api/communication/courses/" + courseId + "/answer-messages", answerPostToSave, AnswerPost.class, HttpStatus.BAD_REQUEST);
            verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
            return;
        }

        AnswerPost createdAnswerPost = request.postWithResponseBody("/api/communication/courses/" + courseId + "/answer-messages", answerPostToSave, AnswerPost.class,
                HttpStatus.CREATED);
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
        AnswerPost answerPostToSave = createAnswerPost(existingConversationPostsWithAnswers.getFirst());
        User mentionedUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        answerPostToSave.setContent("[user]" + mentionedUser.getName() + "(" + mentionedUser.getLogin() + ")[/user]");

        var countBefore = answerPostRepository.count();

        AnswerPost createdAnswerPost = request.postWithResponseBody("/api/communication/courses/" + courseId + "/answer-messages", answerPostToSave, AnswerPost.class,
                HttpStatus.CREATED);
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
        AnswerPost postToSave = createAnswerPost(existingConversationPostsWithAnswers.getFirst());

        AnswerPost notCreatedAnswerPost = request.postWithResponseBody("/api/communication/courses/" + courseId + "/answer-messages", postToSave, AnswerPost.class,
                HttpStatus.BAD_REQUEST);

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
        AnswerPost answerPostToSave = createAnswerPost(existingConversationPostsWithAnswers.getFirst());
        answerPostToSave.setId(999L);

        var countBefore = answerPostRepository.count();

        AnswerPost notCreatedAnswerPost = request.postWithResponseBody("/api/communication/courses/" + courseId + "/answer-messages", answerPostToSave, AnswerPost.class,
                HttpStatus.BAD_REQUEST);
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

        AnswerPost notCreatedAnswerPost = request.postWithResponseBody("/api/communication/courses/" + courseId + "/answer-messages", answerPostToSave, AnswerPost.class,
                HttpStatus.FORBIDDEN);

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

        AnswerPost updatedAnswerPost = request.putWithResponseBody("/api/communication/courses/" + courseId + "/answer-messages/" + conversationAnswerPostToUpdate.getId(),
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
        AnswerPost conversationAnswerPostToUpdate = existingConversationPostsWithAnswers.getFirst().getAnswers().iterator().next();
        conversationAnswerPostToUpdate.setContent("User changes one of their conversation answerPosts" + userMention);

        if (!isUserMentionValid) {
            request.putWithResponseBody("/api/communication/courses/" + courseId + "/answer-messages/" + conversationAnswerPostToUpdate.getId(), conversationAnswerPostToUpdate,
                    AnswerPost.class, HttpStatus.BAD_REQUEST);
            verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
            return;
        }

        AnswerPost updatedAnswerPost = request.putWithResponseBody("/api/communication/courses/" + courseId + "/answer-messages/" + conversationAnswerPostToUpdate.getId(),
                conversationAnswerPostToUpdate, AnswerPost.class, HttpStatus.OK);

        assertThat(conversationAnswerPostToUpdate).isEqualTo(updatedAnswerPost);

        // both conversation participants should be notified
        verify(websocketMessagingService, timeout(2000)).sendMessage(anyString(),
                (Object) argThat(argument -> argument instanceof PostDTO postDTO && postDTO.post().equals(existingConversationPostsWithAnswers.getFirst())));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testMarkMessageAsResolved_asAuthor() throws Exception {
        // conversation post of student1 must be resolvable by them
        AnswerPost resolvingAnswerPost = existingConversationPostsWithAnswers.get(3).getAnswers().iterator().next();
        resolvingAnswerPost.setResolvesPost(true);

        AnswerPost updatedAnswerPost = request.putWithResponseBody("/api/communication/courses/" + courseId + "/answer-messages/" + resolvingAnswerPost.getId(),
                resolvingAnswerPost, AnswerPost.class, HttpStatus.OK);

        assertThat(resolvingAnswerPost).isEqualTo(updatedAnswerPost);

        // confirm that the post is marked as resolved when it has a resolving answer
        assertThat(conversationMessageRepository.findMessagePostByIdElseThrow(updatedAnswerPost.getPost().getId()).isResolved()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseWideMessages_WithUnresolvedPosts() throws Exception {
        // filterToUnresolved set true; will fetch all unresolved posts of current course
        var params = new LinkedMultiValueMap<String, String>();
        params.add("conversationIds", existingCourseWideChannelIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        params.add("filterToUnresolved", "true");
        params.add("filterToCourseWide", "true");
        params.add("size", "50");

        Set<Post> returnedPosts = request.getSet("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        // get posts of current user and compare
        Set<Post> unresolvedPosts = existingCourseWideMessages.stream()
                .filter(post -> post.getAnswers().stream().noneMatch(answerPost -> Boolean.TRUE.equals(answerPost.doesResolvePost()))).collect(Collectors.toSet());

        assertThat(returnedPosts).isEqualTo(unresolvedPosts);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseWideMessages_WithOwnAndUnresolvedPosts() throws Exception {
        // authorIds containing the current user id & filterToUnresolved set true; will fetch all unresolved posts of current user
        var userId = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow().getId();
        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToUnresolved", "true");
        params.add("authorIds", String.valueOf(userId));
        params.add("filterToCourseWide", "true");
        params.add("conversationIds", existingCourseWideChannelIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        params.add("size", "50");

        Set<Post> returnedPosts = request.getSet("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);
        // get unresolved posts of current user and compare
        Set<Post> resolvedPosts = existingCourseWideMessages.stream().filter(post -> student1.getId().equals(post.getAuthor().getId())
                && (post.getAnswers().stream().noneMatch(answerPost -> Boolean.TRUE.equals(answerPost.doesResolvePost())))).collect(Collectors.toSet());

        assertThat(returnedPosts).isEqualTo(resolvedPosts);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseWideMessages_WithOwnAndUnresolvedPostsWithCourseWideContent() throws Exception {
        // authorIds containing the current user id & filterToUnresolved set true; will fetch all unresolved posts of current user
        var userId = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow().getId();
        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToUnresolved", "true");
        params.add("authorIds", String.valueOf(userId));
        params.add("filterToCourseWide", "true");
        params.add("conversationIds", existingCourseWideChannelIds.stream().map(String::valueOf).collect(Collectors.joining(",")));

        Set<Post> returnedPosts = request.getSet("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
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
        params.add("filterToCourseWide", "true");
        params.add("conversationIds", existingCourseWideChannelIds.stream().map(String::valueOf).collect(Collectors.joining(",")));

        Set<Post> returnedPosts = request.getSet("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
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

        var userId = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow().getId();
        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToAnsweredOrReacted", "true");
        params.add("authorIds", String.valueOf(userId));
        params.add("filterToCourseWide", "true");
        params.add("conversationIds", existingCourseWideChannelIds.stream().map(String::valueOf).collect(Collectors.joining(",")));

        Set<Post> returnedPosts = request.getSet("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
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
        params.add("filterToCourseWide", "true");
        params.add("conversationIds", existingCourseWideChannelIds.stream().map(String::valueOf).collect(Collectors.joining(",")));

        Set<Post> returnedPosts = request.getSet("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
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

        var userId = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow().getId();
        var params = new LinkedMultiValueMap<String, String>();
        params.add("authorIds", String.valueOf(userId));
        params.add("filterToUnresolved", "true");
        params.add("filterToAnsweredOrReacted", "true");
        params.add("filterToCourseWide", "true");
        params.add("conversationIds", existingCourseWideChannelIds.stream().map(String::valueOf).collect(Collectors.joining(",")));

        Set<Post> returnedPosts = request.getSet("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
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

        var userId = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow().getId();
        var params = new LinkedMultiValueMap<String, String>();
        params.add("filterToAnsweredOrReacted", "true");
        params.add("authorIds", String.valueOf(userId));
        params.add("filterToCourseWide", "true");
        params.add("conversationIds", existingCourseWideChannelIds.stream().map(String::valueOf).collect(Collectors.joining(",")));

        Set<Post> returnedPosts = request.getSet("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
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
        AnswerPost conversationAnswerPostToUpdate = existingConversationPostsWithAnswers.getFirst().getAnswers().iterator().next();
        conversationAnswerPostToUpdate.setContent("Tutor attempts to change some other user's conversation answerPost");

        AnswerPost notUpdatedAnswerPost = request.putWithResponseBody("/api/communication/courses/" + courseId + "/answer-messages/" + conversationAnswerPostToUpdate.getId(),
                conversationAnswerPostToUpdate, AnswerPost.class, HttpStatus.FORBIDDEN);

        assertThat(notUpdatedAnswerPost).isNull();

        // conversation participants should not be notified
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testEditAnswerPostWithIdIsNull_badRequest() throws Exception {
        // updated answerMessage and provided answerMessageId should match
        AnswerPost conversationAnswerPostToUpdate = existingConversationPostsWithAnswers.getFirst().getAnswers().iterator().next();
        conversationAnswerPostToUpdate.setContent("User changes one of their conversation answerPosts");

        var countBefore = answerPostRepository.count();

        AnswerPost notUpdatedAnswerPost = request.putWithResponseBody("/api/communication/courses/" + courseId + "/answer-messages/" + 999L, conversationAnswerPostToUpdate,
                AnswerPost.class, HttpStatus.BAD_REQUEST);

        assertThat(notUpdatedAnswerPost).isNull();
        assertThat(answerPostRepository.count()).isEqualTo(countBefore);

        // conversation participants should not be notified
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testEditAnswerPostWithInvalidId_badRequest() throws Exception {
        AnswerPost answerPostToUpdate = createAnswerPost(existingPostsWithAnswersCourseWide.getFirst());

        AnswerPost updatedAnswerPostServer = request.putWithResponseBody("/api/communication/courses/" + courseId + "/answer-messages/" + answerPostToUpdate.getId(),
                answerPostToUpdate, AnswerPost.class, HttpStatus.BAD_REQUEST);
        assertThat(updatedAnswerPostServer).isNull();

        // conversation participants should not be notified
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testEditAnswerPostWithWrongCourseId_badRequest() throws Exception {
        AnswerPost answerPostToUpdate = createAnswerPost(existingPostsWithAnswersCourseWide.getFirst());
        Course dummyCourse = courseUtilService.createCourse();

        AnswerPost updatedAnswerPostServer = request.putWithResponseBody("/api/communication/courses/" + dummyCourse.getId() + "/answer-messages/" + answerPostToUpdate.getId(),
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
        request.delete("/api/communication/courses/" + courseId + "/answer-messages/" + 999999999L, HttpStatus.NOT_FOUND);
        assertThat(answerPostRepository.count()).isEqualTo(countBefore);

        // conversation participants should not be notified
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testDeleteConversationPost() throws Exception {
        // conversation post of student1 must be deletable by them
        AnswerPost conversationAnswerPostToDelete = existingConversationPostsWithAnswers.get(2).getAnswers().iterator().next();
        request.delete("/api/communication/courses/" + courseId + "/answer-messages/" + conversationAnswerPostToDelete.getId(), HttpStatus.OK);

        assertThat(answerPostRepository.findById(conversationAnswerPostToDelete.getId())).isEmpty();

        // both conversation participants should be notified
        verify(websocketMessagingService, timeout(2000).times(2)).sendMessage(anyString(),
                (Object) argThat(argument -> argument instanceof PostDTO postDTO && postDTO.post().equals(existingConversationPostsWithAnswers.get(2))));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testDeleteConversationPost_forbidden() throws Exception {
        // conversation post of student1 must not be deletable by tutors
        AnswerPost conversationAnswerPostToDelete = existingConversationPostsWithAnswers.getFirst().getAnswers().iterator().next();
        request.delete("/api/communication/courses/" + courseId + "/answer-messages/" + conversationAnswerPostToDelete.getId(), HttpStatus.FORBIDDEN);

        assertThat(answerPostRepository.findById(conversationAnswerPostToDelete.getId())).isPresent();

        // conversation participants should not be notified
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldSendCourseNotificationWhenFeatureIsEnabled() throws Exception {

        featureToggleService.enableFeature(Feature.CourseSpecificNotifications);

        var channel = createChannelWithTwoStudents();
        var post = existingConversationPostsWithAnswers.getFirst();
        post.setConversation(channel);
        Post savedMessage = conversationMessageRepository.save(post);

        AnswerPost answerPostToSave = createAnswerPost(savedMessage);

        request.postWithResponseBody("/api/communication/courses/" + courseId + "/answer-messages", answerPostToSave, AnswerPost.class, HttpStatus.CREATED);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<CourseNotification> notifications = courseNotificationRepository.findAll();

            boolean hasAnswerNotification = notifications.stream().filter(notification -> notification.getCourse().getId().equals(courseId))
                    .anyMatch(notification -> notification.getType() == 2);

            assertThat(hasAnswerNotification).isTrue();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldSendMentionNotificationWhenUserMentioned() throws Exception {
        featureToggleService.enableFeature(Feature.CourseSpecificNotifications);

        User mentionedUser = userUtilService.getUserByLogin(TEST_PREFIX + "student2");

        var channel = createChannelWithTwoStudents();
        var post = existingConversationPostsWithAnswers.getFirst();
        post.setConversation(channel);
        Post savedMessage = conversationMessageRepository.save(post);

        AnswerPost answerPostToSave = createAnswerPost(savedMessage);
        answerPostToSave.setContent("[user]" + mentionedUser.getName() + "(" + mentionedUser.getLogin() + ")[/user] Check this out!");

        request.postWithResponseBody("/api/communication/courses/" + courseId + "/answer-messages", answerPostToSave, AnswerPost.class, HttpStatus.CREATED);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<CourseNotification> notifications = courseNotificationRepository.findAll();

            boolean hasMentionNotification = notifications.stream().filter(notification -> notification.getCourse().getId().equals(courseId))
                    .anyMatch(notification -> notification.getType() == 3);

            assertThat(hasMentionNotification).isTrue();
        });
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
        return List.of(CourseInformationSharingConfiguration.COMMUNICATION_ONLY, CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
    }
}
