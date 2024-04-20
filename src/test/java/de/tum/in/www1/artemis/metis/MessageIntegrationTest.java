package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.CourseInformationSharingConfiguration;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.PostSortCriterion;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.OneToOneChat;
import de.tum.in.www1.artemis.domain.notification.ConversationNotification;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.post.ConversationUtilService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationMessageRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ConversationNotificationRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.OneToOneChatRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.PostContextFilter;
import de.tum.in.www1.artemis.web.websocket.dto.metis.PostDTO;

class MessageIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "messageintegration";

    static final int MAX_POSTS_PER_PAGE = 20;

    @Autowired
    private ConversationMessageRepository conversationMessageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OneToOneChatRepository oneToOneChatRepository;

    @Autowired
    private ConversationParticipantRepository conversationParticipantRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ConversationNotificationRepository conversationNotificationRepository;

    private List<Post> existingCourseWideMessages;

    private List<Post> existingConversationMessages;

    private Course course;

    private Long courseId;

    private static final int NUMBER_OF_POSTS = 5;

    private static final int HIGHER_PAGE_SIZE = NUMBER_OF_POSTS + 10;

    private static final int LOWER_PAGE_SIZE = NUMBER_OF_POSTS - 2;

    private static final int EQUAL_PAGE_SIZE = NUMBER_OF_POSTS;

    @BeforeEach
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 4, 4, 4, 1);

        // initialize test setup and get all existing posts
        // (there are 4 posts with lecture context, 4 with exercise context, 3 with course-wide context and 3 with conversation initialized): 14 posts in total
        List<Post> existingPostsAndConversationPosts = conversationUtilService.createPostsWithinCourse(TEST_PREFIX);

        existingCourseWideMessages = existingPostsAndConversationPosts.stream().filter(post -> post.getConversation() instanceof Channel channel && channel.getIsCourseWide())
                .toList();

        // filters existing posts with conversation
        existingConversationMessages = existingPostsAndConversationPosts.stream().filter(
                post -> post.getConversation() != null && !(post.getConversation() instanceof Channel channel && (channel.getExercise() != null || channel.getLecture() != null)))
                .toList();

        // filter existing posts with exercise context
        List<Post> existingExercisePosts = existingPostsAndConversationPosts.stream()
                .filter(coursePost -> (coursePost.getConversation() instanceof Channel channel && channel.getExercise() != null)).toList();

        // filter existing posts with first exercise context
        Channel exerciseChannel = ((Channel) existingExercisePosts.get(0).getConversation());
        Exercise exercise = exerciseChannel.getExercise();

        course = exercise.getCourseViaExerciseGroupOrCourseMember();
        courseUtilService.enableMessagingForCourse(course);

        courseId = course.getId();
    }

    @ParameterizedTest
    @MethodSource("courseInformationSharingConfigurationProvider")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateConversationPost(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        var persistedCourse = courseRepository.findByIdElseThrow(courseId);
        persistedCourse.setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);
        persistedCourse = courseRepository.saveAndFlush(persistedCourse);
        assertThat(persistedCourse.getCourseInformationSharingConfiguration()).isEqualTo(courseInformationSharingConfiguration);

        Post postToSave = createPostWithOneToOneChat(TEST_PREFIX);

        Post createdPost = createPostAndAwaitAsyncCode(postToSave);
        checkCreatedMessagePost(postToSave, createdPost);
        assertThat(createdPost.getConversation().getId()).isNotNull();
        var requestingUser = userRepository.getUser();

        PostContextFilter postContextFilter = new PostContextFilter(courseId);
        postContextFilter.setConversationId(createdPost.getConversation().getId());
        assertThat(conversationMessageRepository.findMessages(postContextFilter, Pageable.unpaged(), requestingUser.getId())).hasSize(1);

        // both conversation participants should be notified
        verify(websocketMessagingService, timeout(2000).times(2)).sendMessage(anyString(),
                (Object) argThat(argument -> argument instanceof PostDTO postDTO && postDTO.post().equals(createdPost)));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateConversationPostInCourseWideChannel(boolean isAnnouncement) throws Exception {
        Channel channel = conversationUtilService.createCourseWideChannel(course, "test", isAnnouncement);
        ConversationParticipant otherParticipant = conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student2");
        ConversationParticipant author = conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1");

        Post postToSave = new Post();
        postToSave.setAuthor(author.getUser());
        postToSave.setConversation(channel);
        postToSave.setContent("message");

        Post createdPost = createPostAndAwaitAsyncCode(postToSave);
        checkCreatedMessagePost(postToSave, createdPost);

        // conversation participants should be notified via one broadcast
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
        verify(websocketMessagingService, timeout(2000).times(1)).sendMessage(eq("/topic/metis/courses/" + courseId), any(PostDTO.class));

        if (isAnnouncement) {
            verify(mailService, timeout(2000).times(1)).sendNotification(any(ConversationNotification.class), eq(otherParticipant.getUser()), any(Post.class));
            verify(mailService, timeout(2000).times(1)).sendNotification(any(ConversationNotification.class), eq(author.getUser()), any(Post.class));
        }
        else {
            verify(mailService, never()).sendNotification(any(Notification.class), any(User.class), any(Post.class));
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateAnnouncementInPrivateChannel() throws Exception {
        Channel channel = conversationUtilService.createAnnouncementChannel(course, "test");
        ConversationParticipant otherParticipant = conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1");
        ConversationParticipant author = conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "instructor1");

        Post postToSave = new Post();
        postToSave.setAuthor(author.getUser());
        postToSave.setConversation(channel);
        postToSave.setContent("message");

        Post createdPost = createPostAndAwaitAsyncCode(postToSave);
        checkCreatedMessagePost(postToSave, createdPost);

        // conversation participants should be notified individually
        verify(websocketMessagingService, timeout(2000).times(2)).sendMessage(anyString(),
                (Object) argThat(argument -> argument instanceof PostDTO postDTO && postDTO.post().equals(createdPost)));
        verify(websocketMessagingService, never()).sendMessage(eq("/topic/metis/courses/" + courseId), any(PostDTO.class));

        verify(mailService, timeout(2000).times(1)).sendNotification(any(ConversationNotification.class), eq(otherParticipant.getUser()), any(Post.class));
        verify(mailService, timeout(2000).times(1)).sendNotification(any(ConversationNotification.class), eq(author.getUser()), any(Post.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateConversationPostInCourseWideChannel_onlyFewDatabaseCalls() throws Exception {
        Course course = courseUtilService.createCourseWithMessagingEnabled();
        userUtilService.addStudents(TEST_PREFIX + "createMessageDbTest", 1, 5);

        // given
        Channel channel = conversationUtilService.createCourseWideChannel(course, "test");
        ConversationParticipant author = conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1");
        Post postToSave = new Post();
        postToSave.setAuthor(author.getUser());
        postToSave.setConversation(channel);

        // then
        // expected are 7 database calls independent of the number of students in the course.
        // 4 calls are for user authentication checks, 3 calls to update database
        // further database calls are made in async code
        assertThatDb(() -> request.postWithResponseBody("/api/courses/" + courseId + "/messages", postToSave, Post.class, HttpStatus.CREATED)).hasBeenCalledTimes(7);
    }

    @ParameterizedTest
    @MethodSource("userMentionProvider")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateConversationPostWithUserMention(String userMention, boolean isUserMentionValid) throws Exception {
        Channel channel = conversationUtilService.createCourseWideChannel(course, "test");
        ConversationParticipant author = conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1");

        Post postToSave = new Post();
        postToSave.setAuthor(author.getUser());
        postToSave.setConversation(channel);
        postToSave.setContent(userMention);

        if (!isUserMentionValid) {
            request.postWithResponseBody("/api/courses/" + courseId + "/messages", postToSave, Post.class, HttpStatus.BAD_REQUEST);
            verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
            verify(websocketMessagingService, never()).sendMessage(anyString(), any(PostDTO.class));
            return;
        }

        Post createdPost = createPostAndAwaitAsyncCode(postToSave);
        checkCreatedMessagePost(postToSave, createdPost);

        // conversation participants should be notified via one broadcast
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
        verify(websocketMessagingService, timeout(2000).times(1)).sendMessage(eq("/topic/metis/courses/" + courseId), any(PostDTO.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateConversationPostWithUserMention_MentionedUserHasChannelHidden(boolean isConversationHidden) throws Exception {
        Channel channel = conversationUtilService.createPublicChannel(course, "test");
        ConversationParticipant author = conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1");
        ConversationParticipant mentionedUserParticipant = conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student2", isConversationHidden);

        Post postToSave = new Post();
        postToSave.setAuthor(author.getUser());
        postToSave.setConversation(channel);
        String userMention = "[user]" + mentionedUserParticipant.getUser().getName() + "(" + mentionedUserParticipant.getUser().getLogin() + ")[/user]";
        String authorMention = "[user]" + author.getUser().getName() + "(" + author.getUser().getLogin() + ")[/user]";
        postToSave.setContent(userMention + authorMention);

        Post createdPost = createPostAndAwaitAsyncCode(postToSave);
        checkCreatedMessagePost(postToSave, createdPost);

        // both users are updated
        verify(websocketMessagingService, timeout(2000)).sendMessage(eq("/topic/user/" + author.getUser().getId() + "/notifications/conversations"),
                (Object) argThat(argument -> argument instanceof PostDTO postDTO && postDTO.post().equals(createdPost)));
        verify(websocketMessagingService, timeout(2000)).sendMessage(eq("/topic/user/" + mentionedUserParticipant.getUser().getId() + "/notifications/conversations"),
                (Object) argThat(argument -> argument instanceof PostDTO postDTO && postDTO.post().equals(createdPost)));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testBroadCastWithNotification() throws Exception {
        Channel channel = conversationUtilService.createCourseWideChannel(course, "test");
        ConversationParticipant recipientWithHiddenTrue = conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student2");
        recipientWithHiddenTrue.setIsHidden(true);
        conversationParticipantRepository.save(recipientWithHiddenTrue);
        ConversationParticipant author = conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1");

        Post postToSave = new Post();
        postToSave.setAuthor(author.getUser());
        postToSave.setConversation(channel);

        Post createdPost = createPostAndAwaitAsyncCode(postToSave);
        checkCreatedMessagePost(postToSave, createdPost);

        ConversationNotification notificationForPost = conversationNotificationRepository.findAll().stream().filter(notification -> createdPost.equals(notification.getMessage()))
                .findFirst().orElseThrow();

        verify(websocketMessagingService, timeout(2000).times(1)).sendMessage(eq("/topic/metis/courses/" + course.getId()),
                (Object) argThat(argument -> argument instanceof PostDTO postDTO && postDTO.post().equals(createdPost) && postDTO.notification().equals(notificationForPost)));
    }

    @ParameterizedTest
    @ValueSource(ints = { HIGHER_PAGE_SIZE, LOWER_PAGE_SIZE, EQUAL_PAGE_SIZE })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFindMessagesWithPageSizes(int pageSize) {

        var student1 = userRepository.findOneWithGroupsAndAuthoritiesByLogin(TEST_PREFIX + "student1").orElseThrow();
        var student2 = userRepository.findOneWithGroupsAndAuthoritiesByLogin(TEST_PREFIX + "student2").orElseThrow();
        List<Post> posts = conversationUtilService.createPostsWithAnswersAndReactionsAndConversation(course, student1, student2, NUMBER_OF_POSTS, TEST_PREFIX);
        long conversationId = posts.get(0).getConversation().getId();
        for (Post post : posts) {
            assertThat(post.getConversation().getId()).isNotNull();
            assertThat(post.getConversation().getId()).isEqualTo(conversationId);
        }
        var requestingUser = userRepository.getUser();

        PostContextFilter postContextFilter = new PostContextFilter(course.getId());
        postContextFilter.setConversationId(posts.get(0).getConversation().getId());
        if (pageSize == LOWER_PAGE_SIZE) {
            assertThat(conversationMessageRepository.findMessages(postContextFilter, Pageable.ofSize(pageSize), requestingUser.getId())).hasSize(LOWER_PAGE_SIZE);
        }
        else {
            assertThat(conversationMessageRepository.findMessages(postContextFilter, Pageable.ofSize(pageSize), requestingUser.getId())).hasSize(NUMBER_OF_POSTS);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testMessagingNotAllowedIfDisabledSetting() throws Exception {
        var persistedCourse = courseRepository.findByIdElseThrow(courseId);
        persistedCourse.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.DISABLED);
        persistedCourse = courseRepository.saveAndFlush(persistedCourse);
        assertThat(persistedCourse.getCourseInformationSharingConfiguration()).isEqualTo(CourseInformationSharingConfiguration.DISABLED);

        Post postToSave = createPostWithOneToOneChat(TEST_PREFIX);
        var requestingUser = userRepository.getUser();

        PostContextFilter postContextFilter = new PostContextFilter(courseId);
        postContextFilter.setConversationId(postToSave.getConversation().getId());
        var numberOfPostsBefore = conversationMessageRepository.findMessages(postContextFilter, Pageable.unpaged(), requestingUser.getId()).getSize();

        Post notCreatedPost = request.postWithResponseBody("/api/courses/" + courseId + "/messages", postToSave, Post.class, HttpStatus.BAD_REQUEST);

        assertThat(notCreatedPost).isNull();
        assertThat(conversationMessageRepository.findMessages(postContextFilter, Pageable.unpaged(), requestingUser.getId())).hasSize(numberOfPostsBefore);

        // conversation participants should not be notified
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));

        // active messaging again
        persistedCourse.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        courseRepository.saveAndFlush(persistedCourse);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void testCreateConversationPost_forbidden() throws Exception {
        // only participants of a conversation can create posts for it

        Post postToSave = createPostWithOneToOneChat(TEST_PREFIX);
        // attempt to save new post under someone else's conversation
        postToSave.setConversation(existingConversationMessages.get(0).getConversation());
        var requestingUser = userRepository.getUser();

        PostContextFilter postContextFilter = new PostContextFilter(courseId);
        postContextFilter.setConversationId(postToSave.getConversation().getId());
        var numberOfPostsBefore = conversationMessageRepository.findMessages(postContextFilter, Pageable.unpaged(), requestingUser.getId()).getSize();

        Post notCreatedPost = request.postWithResponseBody("/api/courses/" + courseId + "/messages", postToSave, Post.class, HttpStatus.FORBIDDEN);
        assertThat(notCreatedPost).isNull();
        assertThat(conversationMessageRepository.findMessages(postContextFilter, Pageable.unpaged(), requestingUser.getId())).hasSize(numberOfPostsBefore);

        // conversation participants should not be notified
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testValidatePostContextConstraintViolation() throws Exception {
        ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
        Validator validator = validatorFactory.getValidator();
        validatorFactory.close();
        Post invalidPost = createPostWithOneToOneChat(TEST_PREFIX);
        invalidPost.setPlagiarismCase(new PlagiarismCase());
        request.postWithResponseBody("/api/courses/" + courseId + "/messages", invalidPost, Post.class, HttpStatus.BAD_REQUEST);
        Set<ConstraintViolation<Post>> constraintViolations = validator.validate(invalidPost);
        assertThat(constraintViolations).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetConversationPosts_IfNoParticipant() throws Exception {
        // conversation set will fetch all posts of conversation if the user is involved
        Channel channel = conversationUtilService.createCourseWideChannel(course, "course-wide");
        conversationUtilService.addMessageToConversation(TEST_PREFIX + "student1", channel);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("conversationId", channel.getId().toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
        // get amount of posts with that certain
        assertThat(returnedPosts).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testGetConversationPost() throws Exception {
        // conversation set will fetch all posts of conversation if the user is involved
        var params = new LinkedMultiValueMap<String, String>();
        Long conversationId = existingConversationMessages.get(0).getConversation().getId();
        params.add("conversationId", conversationId.toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
        // get amount of posts with that certain
        assertThat(returnedPosts).hasSize(existingConversationMessages.stream().filter(post -> post.getConversation().getId() == conversationId).toList().size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testGetCourseWideMessages() throws Exception {
        // conversation set will fetch all posts of conversation if the user is involved
        var params = new LinkedMultiValueMap<String, String>();
        params.add("courseWideChannelIds", "");
        params.add("size", "50");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);

        assertThat(returnedPosts).isNotEmpty();
        assertThat(returnedPosts).hasSize(existingCourseWideMessages.size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseWideMessages_WithOwnWithCourseWideContent() throws Exception {
        // conversation set will fetch all posts of conversation if the user is involved
        var params = new LinkedMultiValueMap<String, String>();
        var courseWidePosts = existingConversationMessages.stream().filter(post -> post.getConversation() instanceof Channel channel && channel.getIsCourseWide()).toList();
        var courseWideChannelId = courseWidePosts.get(0).getConversation().getId();
        params.add("courseWideChannelIds", courseWideChannelId.toString());
        params.add("filterToOwn", "true");
        params.add("size", "50");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
        // get amount of posts with that certain
        assertThat(returnedPosts).hasSize(existingCourseWideMessages.stream()
                .filter(post -> post.getConversation().getId().equals(courseWideChannelId) && post.getAuthor().getLogin().equals(TEST_PREFIX + "student1")).toList().size());
        assertThat(returnedPosts.size()).isLessThan(courseWidePosts.size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseWideMessages_OrderByAnswerCountDESC() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();

        // ordering only available in course discussions page, where paging is enabled
        params.add("pagingEnabled", "true");
        params.add("page", "0");
        params.add("size", String.valueOf(MAX_POSTS_PER_PAGE));

        params.add("postSortCriterion", PostSortCriterion.ANSWER_COUNT.toString());
        params.add("sortingOrder", SortingOrder.DESCENDING.toString());
        params.add("courseWideChannelIds", "");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);

        int numberOfMaxAnswersSeenOnAnyPost = Integer.MAX_VALUE;
        for (Post post : returnedPosts) {
            assertThat(post.getAnswers().size()).isLessThanOrEqualTo(numberOfMaxAnswersSeenOnAnyPost);
            numberOfMaxAnswersSeenOnAnyPost = post.getAnswers().size();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseWideMessages_OrderByAnswerCountASC() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();

        // ordering only available in course discussions page, where paging is enabled
        params.add("pagingEnabled", "true");
        params.add("page", "0");
        params.add("size", String.valueOf(MAX_POSTS_PER_PAGE));

        params.add("postSortCriterion", PostSortCriterion.ANSWER_COUNT.toString());
        params.add("sortingOrder", SortingOrder.ASCENDING.toString());
        params.add("courseWideChannelIds", "");

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);

        int numberOfMaxAnswersSeenOnAnyPost = 0;
        for (Post post : returnedPosts) {
            assertThat(post.getAnswers().size()).isGreaterThanOrEqualTo(numberOfMaxAnswersSeenOnAnyPost);
            numberOfMaxAnswersSeenOnAnyPost = post.getAnswers().size();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testGetCourseWideMessagesFromOneChannel() throws Exception {
        // conversation set will fetch all posts of conversation if the user is involved
        var params = new LinkedMultiValueMap<String, String>();
        var courseWidePosts = existingConversationMessages.stream().filter(post -> post.getConversation() instanceof Channel channel && channel.getIsCourseWide()).toList();
        var courseWideChannelId = courseWidePosts.get(0).getConversation().getId();
        params.add("courseWideChannelIds", courseWideChannelId.toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
        // get amount of posts with that certain
        assertThat(returnedPosts)
                .hasSize(existingConversationMessages.stream().filter(post -> Objects.equals(post.getConversation().getId(), courseWideChannelId)).toList().size());
        assertThat(returnedPosts.size()).isLessThan(courseWidePosts.size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1")
    void testEditConversationPost() throws Exception {
        // conversation post of tutor1 must be only editable by them
        Post conversationPostToUpdate = existingConversationMessages.get(0);
        conversationPostToUpdate.setContent("User changes one of their conversation posts");

        Post updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/messages/" + conversationPostToUpdate.getId(), conversationPostToUpdate, Post.class,
                HttpStatus.OK);

        assertThat(conversationPostToUpdate).isEqualTo(updatedPost);

        // both conversation participants should be notified about the update
        verify(websocketMessagingService, timeout(2000).times(2)).sendMessage(anyString(),
                (Object) argThat(argument -> argument instanceof PostDTO postDTO && postDTO.post().equals(updatedPost)));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testPinPost_asTutor() throws Exception {
        Post postToPin = existingConversationMessages.get(0);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("displayPriority", DisplayPriority.PINNED.toString());

        // change display priority to PINNED
        Post updatedPost = request.putWithResponseBodyAndParams("/api/courses/" + courseId + "/messages/" + postToPin.getId() + "/display-priority", null, Post.class,
                HttpStatus.OK, params);
        conversationUtilService.assertSensitiveInformationHidden(updatedPost);
        assertThat(updatedPost).isEqualTo(postToPin);
    }

    @ParameterizedTest
    @MethodSource("userMentionProvider")
    @WithMockUser(username = TEST_PREFIX + "tutor1")
    void testEditConversationPostWithUserMention(String userMention, boolean isUserMentionValid) throws Exception {
        // conversation post of tutor1 must be only editable by them
        Post conversationPostToUpdate = existingConversationMessages.get(0);
        conversationPostToUpdate.setContent("User changes one of their conversation posts" + userMention);

        if (!isUserMentionValid) {
            request.putWithResponseBody("/api/courses/" + courseId + "/messages/" + conversationPostToUpdate.getId(), conversationPostToUpdate, Post.class, HttpStatus.BAD_REQUEST);
            verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
            return;
        }

        Post updatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/messages/" + conversationPostToUpdate.getId(), conversationPostToUpdate, Post.class,
                HttpStatus.OK);

        assertThat(conversationPostToUpdate).isEqualTo(updatedPost);

        // both conversation participants should be notified about the update
        verify(websocketMessagingService, timeout(2000).times(2)).sendMessage(anyString(),
                (Object) argThat(argument -> argument instanceof PostDTO postDTO && postDTO.post().equals(updatedPost)));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testEditConversationPost_forbidden() throws Exception {
        // conversation post of tutor1 must not be editable by tutor2
        Post conversationPostToUpdate = existingConversationMessages.get(0);
        conversationPostToUpdate.setContent("Tutor attempts to change some other user's conversation post");

        Post notUpdatedPost = request.putWithResponseBody("/api/courses/" + courseId + "/messages/" + conversationPostToUpdate.getId(), conversationPostToUpdate, Post.class,
                HttpStatus.FORBIDDEN);

        assertThat(notUpdatedPost).isNull();

        // conversation participants should not be notified
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1")
    void testDeleteConversationPost() throws Exception {
        // conversation post of tutor1 must be deletable by them
        Post conversationPostToDelete = existingConversationMessages.get(0);
        request.delete("/api/courses/" + courseId + "/messages/" + conversationPostToDelete.getId(), HttpStatus.OK);

        assertThat(conversationMessageRepository.findById(conversationPostToDelete.getId())).isEmpty();
        // both conversation participants should be notified
        verify(websocketMessagingService, timeout(2000).times(2)).sendMessage(anyString(),
                (Object) argThat(argument -> argument instanceof PostDTO postDTO && postDTO.post().equals(conversationPostToDelete)));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testDeleteConversationPost_forbidden() throws Exception {
        // conversation post of user must not be deletable by tutors
        Post conversationPostToDelete = existingConversationMessages.get(0);
        request.delete("/api/courses/" + courseId + "/messages/" + conversationPostToDelete.getId(), HttpStatus.FORBIDDEN);

        assertThat(conversationMessageRepository.findById(conversationPostToDelete.getId())).isPresent();
        // conversation participants should not be notified
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testIncreaseUnreadMessageCountAfterMessageSend() throws Exception {
        Post postToSave = createPostWithOneToOneChat(TEST_PREFIX);
        Post createdPost = createPostAndAwaitAsyncCode(postToSave);

        await().untilAsserted(() -> {
            SecurityUtils.setAuthorizationObject();
            long unreadMessages = oneToOneChatRepository.findByIdWithConversationParticipantsAndUserGroups(createdPost.getConversation().getId()).orElseThrow()
                    .getConversationParticipants().stream()
                    .filter(conversationParticipant -> !Objects.equals(conversationParticipant.getUser().getId(), postToSave.getAuthor().getId())).findAny().orElseThrow()
                    .getUnreadMessagesCount();
            assertThat(unreadMessages).isEqualTo(1L);
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDecreaseUnreadMessageCountAfterMessageRead() throws Exception {
        Post postToSave1 = createPostWithOneToOneChat(TEST_PREFIX);

        ResultActions resultActions = request
                .performMvcRequest(MockMvcRequestBuilders.post("/api/courses/" + courseId + "/messages").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postToSave1)).with(user(TEST_PREFIX + "student1").roles("USER")).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        MvcResult result = resultActions.andReturn();
        String contentAsString = result.getResponse().getContentAsString();
        Post createdPost1 = objectMapper.readValue(contentAsString, Post.class);

        request.performMvcRequest(MockMvcRequestBuilders.get("/api/courses/" + courseId + "/messages").param("conversationId", createdPost1.getConversation().getId().toString())
                .param("pagingEnabled", "true").param("size", String.valueOf(MAX_POSTS_PER_PAGE)).with(user(TEST_PREFIX + "student2").roles("USER"))
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk());

        await().untilAsserted(() -> {
            SecurityUtils.setAuthorizationObject();
            assertThat(oneToOneChatRepository.findByIdWithConversationParticipantsAndUserGroups(createdPost1.getConversation().getId()).orElseThrow().getConversationParticipants()
                    .stream().filter(conversationParticipant -> !Objects.equals(conversationParticipant.getUser().getId(), postToSave1.getAuthor().getId())).findAny().orElseThrow()
                    .getUnreadMessagesCount()).isZero();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDecreaseUnreadMessageCountWhenDeletingMessage() throws Exception {
        Post postToSave1 = createPostWithOneToOneChat(TEST_PREFIX);
        Post postToSave2 = createPostWithOneToOneChat(TEST_PREFIX);

        ResultActions resultActions = request
                .performMvcRequest(MockMvcRequestBuilders.post("/api/courses/" + courseId + "/messages").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postToSave1)).with(user(TEST_PREFIX + "student1").roles("USER")).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        MvcResult result = resultActions.andReturn();
        String contentAsString = result.getResponse().getContentAsString();
        Post createdPost1 = objectMapper.readValue(contentAsString, Post.class);

        ResultActions resultActions2 = request
                .performMvcRequest(MockMvcRequestBuilders.post("/api/courses/" + courseId + "/messages").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postToSave2)).with(user(TEST_PREFIX + "student1").roles("USER")).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        MvcResult result2 = resultActions2.andReturn();
        String contentAsString2 = result2.getResponse().getContentAsString();
        Post createdPost2 = objectMapper.readValue(contentAsString2, Post.class);

        request.performMvcRequest(MockMvcRequestBuilders.delete("/api/courses/" + courseId + "/messages/" + createdPost2.getId()).with(user(TEST_PREFIX + "student1").roles("USER"))
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk());

        SecurityContextHolder.setContext(TestSecurityContextHolder.getContext());
        long unreadMessages = oneToOneChatRepository.findByIdWithConversationParticipantsAndUserGroups(createdPost1.getConversation().getId()).orElseThrow()
                .getConversationParticipants().stream()
                .filter(conversationParticipant -> !Objects.equals(conversationParticipant.getUser().getId(), postToSave1.getAuthor().getId())).findAny().orElseThrow()
                .getUnreadMessagesCount();

        assertThat(unreadMessages).isEqualTo(1);
    }

    private Post createPostWithOneToOneChat(String userPrefix) {
        var student1 = userRepository.findOneWithGroupsAndAuthoritiesByLogin(userPrefix + "student1").orElseThrow();
        var student2 = userRepository.findOneWithGroupsAndAuthoritiesByLogin(userPrefix + "student2").orElseThrow();
        var chat = new OneToOneChat();
        chat.setCourse(course);
        chat.setCreator(student1);
        chat.setCreationDate(ZonedDateTime.now());
        chat.setLastMessageDate(ZonedDateTime.now());
        chat = oneToOneChatRepository.save(chat);
        var participant1 = new ConversationParticipant();
        participant1.setConversation(chat);
        participant1.setUser(student1);
        participant1.setUnreadMessagesCount(0L);
        participant1.setLastRead(ZonedDateTime.now().minusYears(2));
        conversationParticipantRepository.save(participant1);
        var participant2 = new ConversationParticipant();
        participant2.setConversation(chat);
        participant2.setUser(student2);
        participant2.setUnreadMessagesCount(0L);
        participant2.setLastRead(ZonedDateTime.now().minusYears(2));
        conversationParticipantRepository.save(participant2);
        chat = oneToOneChatRepository.findByIdWithConversationParticipantsAndUserGroups(chat.getId()).orElseThrow();
        Post post = new Post();
        post.setAuthor(student1);
        post.setDisplayPriority(DisplayPriority.NONE);
        post.setConversation(chat);
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
    }

    protected static List<Arguments> userMentionProvider() {
        return userMentionProvider(TEST_PREFIX + "student1", TEST_PREFIX + "student2");
    }

    /**
     * Calls the POST /messages endpoint to create a new message and awaits the asynchronously executed code
     * <p>
     * This method awaits the asynchronous calls, by checking that the notification for the new message has been stored in the database,
     * which is a call close to the end of the asynchronously executed code.
     *
     * @param postToSave post to save in the database
     * @return saved post
     */
    private Post createPostAndAwaitAsyncCode(Post postToSave) throws Exception {
        Post savedPost = request.postWithResponseBody("/api/courses/" + courseId + "/messages", postToSave, Post.class, HttpStatus.CREATED);
        await().until(() -> conversationNotificationRepository.findAll().stream().map(ConversationNotification::getMessage).collect(Collectors.toSet()).contains(savedPost));
        return savedPost;
    }

    private static List<CourseInformationSharingConfiguration> courseInformationSharingConfigurationProvider() {
        return List.of(CourseInformationSharingConfiguration.MESSAGING_ONLY, CourseInformationSharingConfiguration.COMMUNICATION_ONLY,
                CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSimilarityCheck() throws Exception {
        Post postToCheck = new Post();
        postToCheck.setTitle("Title Post");

        List<Post> similarPosts = request.postListWithResponseBody("/api/courses/" + courseId + "/messages/similarity-check", postToCheck, Post.class, HttpStatus.OK);
        assertThat(similarPosts).hasSize(5);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPostTagsForCourse() throws Exception {
        List<String> returnedTags = request.getList("/api/courses/" + courseId + "/messages/tags", HttpStatus.OK, String.class);
        // 4 different tags were used for the posts
        assertThat(returnedTags).hasSameSizeAs(conversationMessageRepository.findPostTagsForCourse(courseId));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPostTagsForCourseWithNonExistentCourseId_notFound() throws Exception {
        List<String> returnedTags = request.getList("/api/courses/" + 9999L + "/messages/tags", HttpStatus.NOT_FOUND, String.class);
        assertThat(returnedTags).isNull();
    }
}
