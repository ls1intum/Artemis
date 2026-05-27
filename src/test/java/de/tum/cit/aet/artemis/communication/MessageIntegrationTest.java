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

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.CourseNotification;
import de.tum.cit.aet.artemis.communication.domain.DisplayPriority;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.PostSortCriterion;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.domain.conversation.OneToOneChat;
import de.tum.cit.aet.artemis.communication.dto.AnswerPostResponseDTO;
import de.tum.cit.aet.artemis.communication.dto.CreateAnswerPostDTO;
import de.tum.cit.aet.artemis.communication.dto.CreatePostConversationDTO;
import de.tum.cit.aet.artemis.communication.dto.CreatePostDTO;
import de.tum.cit.aet.artemis.communication.dto.ParentPostDTO;
import de.tum.cit.aet.artemis.communication.dto.PostBroadcastDTO;
import de.tum.cit.aet.artemis.communication.dto.PostContextFilterDTO;
import de.tum.cit.aet.artemis.communication.dto.PostResponseDTO;
import de.tum.cit.aet.artemis.communication.dto.UpdatePostingDTO;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.communication.test_repository.ConversationParticipantTestRepository;
import de.tum.cit.aet.artemis.communication.test_repository.CourseNotificationTestRepository;
import de.tum.cit.aet.artemis.communication.test_repository.OneToOneChatTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class MessageIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "messageintegration";

    static final int MAX_POSTS_PER_PAGE = 20;

    @Autowired
    private ConversationMessageRepository conversationMessageRepository;

    @Autowired
    private OneToOneChatTestRepository oneToOneChatRepository;

    @Autowired
    private ConversationParticipantTestRepository conversationParticipantRepository;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private CourseNotificationTestRepository courseNotificationRepository;

    private List<Post> existingCourseWideMessages;

    private List<Post> existingConversationMessages;

    private List<Long> existingConversationIds;

    private List<Long> existingCourseWideChannelIds;

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
        List<Post> existingPostsAndConversationPosts = conversationUtilService.createPostsWithinCourse(courseUtilService.createCourse(), TEST_PREFIX);

        existingCourseWideMessages = existingPostsAndConversationPosts.stream().filter(post -> post.getConversation() instanceof Channel channel && channel.getIsCourseWide())
                .toList();

        // filters existing posts with conversation
        existingConversationMessages = existingPostsAndConversationPosts.stream().filter(
                post -> post.getConversation() != null && !(post.getConversation() instanceof Channel channel && (channel.getExercise() != null || channel.getLecture() != null)))
                .toList();

        // filters course wide channels
        existingCourseWideChannelIds = existingPostsAndConversationPosts.stream().filter(post -> post.getConversation() instanceof Channel channel && channel.getIsCourseWide())
                .map(post -> post.getConversation().getId()).distinct().toList();

        // filters conversation ids
        existingConversationIds = existingPostsAndConversationPosts.stream().filter(post -> post.getConversation() != null).map(post -> post.getConversation().getId()).distinct()
                .toList();

        // filter existing posts with exercise context
        List<Post> existingExercisePosts = existingPostsAndConversationPosts.stream()
                .filter(coursePost -> (coursePost.getConversation() instanceof Channel channel && channel.getExercise() != null)).toList();

        // filter existing posts with first exercise context
        Channel exerciseChannel = ((Channel) existingExercisePosts.getFirst().getConversation());
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

        PostResponseDTO createdPost = createPostAndAwaitAsyncCode(postToSave);
        checkCreatedMessagePost(postToSave, createdPost);
        assertThat(createdPost.conversation().id()).isNotNull();
        var requestingUser = userTestRepository.getUser();

        long[] conversationIds = new long[] { createdPost.conversation().id() };
        PostContextFilterDTO postContextFilter = new PostContextFilterDTO(courseId, null, conversationIds, null, null, false, false, false, null, null);
        assertThat(conversationMessageRepository.findMessages(postContextFilter, Pageable.unpaged(), requestingUser.getId())).hasSize(1);

        // both conversation participants should be notified
        verify(websocketMessagingService, timeout(2000).times(2)).sendMessage(anyString(),
                (Object) argThat(argument -> argument instanceof PostBroadcastDTO postBroadcastDTO && idOf(postBroadcastDTO.post()).equals(idOf(createdPost))));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateConversationPostInCourseWideChannel(boolean isAnnouncement) throws Exception {
        Channel channel = conversationUtilService.createCourseWideChannel(course, "test", isAnnouncement);
        conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student2");
        ConversationParticipant author = conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1");

        Post postToSave = new Post();
        postToSave.setAuthor(author.getUser());
        postToSave.setConversation(channel);
        postToSave.setContent("message");

        PostResponseDTO createdPost = createPostAndAwaitAsyncCode(postToSave);
        checkCreatedMessagePost(postToSave, createdPost);

        // conversation participants should be notified via one broadcast
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostBroadcastDTO.class));
        verify(websocketMessagingService, timeout(2000).times(1)).sendMessage(eq("/topic/metis/courses/" + courseId), any(PostBroadcastDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateAnnouncementInPrivateChannel() throws Exception {
        Channel channel = conversationUtilService.createAnnouncementChannel(course, "test");
        conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1");
        ConversationParticipant author = conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "instructor1");

        Post postToSave = new Post();
        postToSave.setAuthor(author.getUser());
        postToSave.setConversation(channel);
        postToSave.setContent("message");

        PostResponseDTO createdPost = createPostAndAwaitAsyncCode(postToSave);
        checkCreatedMessagePost(postToSave, createdPost);

        // conversation participants should be notified individually
        verify(websocketMessagingService, timeout(2000).times(2)).sendMessage(anyString(),
                (Object) argThat(argument -> argument instanceof PostBroadcastDTO postBroadcastDTO && idOf(postBroadcastDTO.post()).equals(idOf(createdPost))));
        verify(websocketMessagingService, never()).sendMessage(eq("/topic/metis/courses/" + courseId), any(PostBroadcastDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateConversationPostInCourseWideChannel_onlyFewDatabaseCalls() throws Exception {
        Course course = courseUtilService.createCourseWithMessagingEnabled();
        userUtilService.addStudents(TEST_PREFIX + "createMessageDbTest", 1, 5);

        // given
        Channel channel = conversationUtilService.createCourseWideChannel(course, "test");
        CreatePostDTO postDTOToSave = new CreatePostDTO("", "", false, new CreatePostConversationDTO(channel.getId()));

        // then
        // TODO: Hibernate 7 increased query count from 7 to 8 — investigate in a follow-up
        // 4 calls are for user authentication checks, 3 calls to update database
        // + 1 additional query from Hibernate 7 entity/collection loading changes
        // further database calls are made in async code
        assertThatDb(() -> request.postWithResponseBody("/api/communication/courses/" + courseId + "/messages", postDTOToSave, PostResponseDTO.class, HttpStatus.CREATED))
                .hasBeenCalledTimes(8);
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

        CreatePostDTO postDTOToSave = new CreatePostDTO(postToSave.getContent(), "", false, new CreatePostConversationDTO(postToSave.getConversation().getId()));

        if (!isUserMentionValid) {
            request.postWithResponseBody("/api/communication/courses/" + courseId + "/messages", postDTOToSave, PostResponseDTO.class, HttpStatus.BAD_REQUEST);
            verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostBroadcastDTO.class));
            verify(websocketMessagingService, never()).sendMessage(anyString(), any(PostBroadcastDTO.class));
            return;
        }

        PostResponseDTO createdPost = createPostAndAwaitAsyncCode(postToSave);
        checkCreatedMessagePost(postToSave, createdPost);

        // conversation participants should be notified via one broadcast
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostBroadcastDTO.class));
        verify(websocketMessagingService, timeout(2000).times(1)).sendMessage(eq("/topic/metis/courses/" + courseId), any(PostBroadcastDTO.class));
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

        PostResponseDTO createdPost = createPostAndAwaitAsyncCode(postToSave);
        checkCreatedMessagePost(postToSave, createdPost);

        // both users are updated
        verify(websocketMessagingService, timeout(2000)).sendMessage(eq("/topic/user/" + author.getUser().getId() + "/notifications/conversations"),
                (Object) argThat(argument -> argument instanceof PostBroadcastDTO postBroadcastDTO && idOf(postBroadcastDTO.post()).equals(idOf(createdPost))));
        verify(websocketMessagingService, timeout(2000)).sendMessage(eq("/topic/user/" + mentionedUserParticipant.getUser().getId() + "/notifications/conversations"),
                (Object) argThat(argument -> argument instanceof PostBroadcastDTO postBroadcastDTO && idOf(postBroadcastDTO.post()).equals(idOf(createdPost))));
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

        PostResponseDTO createdPost = createPostAndAwaitAsyncCode(postToSave);
        checkCreatedMessagePost(postToSave, createdPost);

        verify(websocketMessagingService, timeout(2000).times(1)).sendMessage(eq("/topic/metis/courses/" + course.getId()),
                (Object) argThat(argument -> argument instanceof PostBroadcastDTO postBroadcastDTO && idOf(postBroadcastDTO.post()).equals(idOf(createdPost))));
    }

    @ParameterizedTest
    @ValueSource(ints = { HIGHER_PAGE_SIZE, LOWER_PAGE_SIZE, EQUAL_PAGE_SIZE })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFindMessagesWithPageSizes(int pageSize) {
        var student1 = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        var student2 = userTestRepository.findOneByLogin(TEST_PREFIX + "student2").orElseThrow();
        List<Post> posts = conversationUtilService.createPostsWithAnswersAndReactionsAndConversation(course, student1, student2, NUMBER_OF_POSTS, TEST_PREFIX);
        long conversationId = posts.getFirst().getConversation().getId();
        for (Post post : posts) {
            assertThat(post.getConversation().getId()).isNotNull();
            assertThat(post.getConversation().getId()).isEqualTo(conversationId);
        }
        var requestingUser = userTestRepository.getUser();

        long[] conversationIds = new long[] { posts.getFirst().getConversation().getId() };
        PostContextFilterDTO postContextFilter = new PostContextFilterDTO(course.getId(), null, conversationIds, null, null, false, false, false, null, null);
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
        CreatePostDTO postDTOToSave = new CreatePostDTO(postToSave.getContent(), "", false, new CreatePostConversationDTO(postToSave.getConversation().getId()));

        var requestingUser = userTestRepository.getUser();

        long[] conversationIds = new long[] { postToSave.getConversation().getId() };
        PostContextFilterDTO postContextFilter = new PostContextFilterDTO(courseId, null, conversationIds, null, null, false, false, false, null, null);
        var numberOfPostsBefore = conversationMessageRepository.findMessages(postContextFilter, Pageable.unpaged(), requestingUser.getId()).getSize();

        PostResponseDTO notCreatedPost = request.postWithResponseBody("/api/communication/courses/" + courseId + "/messages", postDTOToSave, PostResponseDTO.class,
                HttpStatus.BAD_REQUEST);

        assertThat(notCreatedPost).isNull();
        assertThat(conversationMessageRepository.findMessages(postContextFilter, Pageable.unpaged(), requestingUser.getId())).hasSize(numberOfPostsBefore);

        // conversation participants should not be notified
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostBroadcastDTO.class));

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
        postToSave.setConversation(existingConversationMessages.getFirst().getConversation());
        var requestingUser = userTestRepository.getUser();

        CreatePostDTO postDTOToSave = new CreatePostDTO(postToSave.getContent(), "", false, new CreatePostConversationDTO(postToSave.getConversation().getId()));

        long[] conversationIds = new long[] { postToSave.getConversation().getId() };
        PostContextFilterDTO postContextFilter = new PostContextFilterDTO(courseId, null, conversationIds, null, null, false, false, false, null, null);
        var numberOfPostsBefore = conversationMessageRepository.findMessages(postContextFilter, Pageable.unpaged(), requestingUser.getId()).getSize();

        PostResponseDTO notCreatedPost = request.postWithResponseBody("/api/communication/courses/" + courseId + "/messages", postDTOToSave, PostResponseDTO.class,
                HttpStatus.FORBIDDEN);
        assertThat(notCreatedPost).isNull();
        assertThat(conversationMessageRepository.findMessages(postContextFilter, Pageable.unpaged(), requestingUser.getId())).hasSize(numberOfPostsBefore);

        // conversation participants should not be notified
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostBroadcastDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetConversationPosts_IfNoParticipant() throws Exception {
        // conversation set will fetch all posts of conversation if the user is involved
        Channel channel = conversationUtilService.createCourseWideChannel(course, "course-wide");
        conversationUtilService.addMessageToConversation(TEST_PREFIX + "student1", channel);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("conversationIds", channel.getId().toString());

        // Deserialize into the cycle-free DTO so this test never enters Jackson's
        // DeserializerCache._createAndCache2 window — the previous Post.class deserialization
        // intermittently fired the "No _valueDeserializer assigned" race via reactions → user → User["id"].
        List<PostResponseDTO> returnedPosts = request.getList("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, PostResponseDTO.class, params);
        assertThat(returnedPosts).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testGetConversationPost() throws Exception {
        // conversation set will fetch all posts of conversation if the user is involved
        var params = new LinkedMultiValueMap<String, String>();
        Long conversationId = existingConversationMessages.getFirst().getConversation().getId();
        params.add("conversationIds", conversationId.toString());

        List<PostResponseDTO> returnedPosts = request.getList("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, PostResponseDTO.class, params);
        // get amount of posts with that certain
        assertThat(returnedPosts).hasSize(existingConversationMessages.stream().filter(post -> Objects.equals(post.getConversation().getId(), conversationId)).toList().size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testGetCourseWideMessages() throws Exception {
        // conversation set will fetch all posts of conversation if the user is involved
        var params = new LinkedMultiValueMap<String, String>();
        params.add("conversationIds", existingConversationIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        params.add("filterToCourseWide", "true");
        params.add("size", "50");

        List<PostResponseDTO> returnedPosts = request.getList("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, PostResponseDTO.class, params);

        assertThat(returnedPosts).isNotEmpty();
        assertThat(returnedPosts).hasSize(existingCourseWideMessages.size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseWideMessages_WithOwnWithCourseWideContent() throws Exception {
        // conversation set will fetch all posts of conversation if the user is involved
        var params = new LinkedMultiValueMap<String, String>();
        var courseWidePosts = existingConversationMessages.stream().filter(post -> post.getConversation() instanceof Channel channel && channel.getIsCourseWide()).toList();
        var courseWideChannelId = courseWidePosts.getFirst().getConversation().getId();
        var userId = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow().getId();
        params.add("conversationIds", courseWideChannelId.toString());
        params.add("filterToCourseWide", "true");
        params.add("authorIds", String.valueOf(userId));
        params.add("size", "50");

        List<PostResponseDTO> returnedPosts = request.getList("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, PostResponseDTO.class, params);
        // get amount of posts with that certain
        assertThat(returnedPosts).hasSize(existingCourseWideMessages.stream()
                .filter(post -> post.getConversation().getId().equals(courseWideChannelId) && post.getAuthor().getLogin().equals(TEST_PREFIX + "student1")).toList().size());
        assertThat(returnedPosts.size()).isLessThan(courseWidePosts.size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseWideMessages_OrderByCreationDateDESC() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();

        // ordering only available in course discussions page, where paging is enabled

        params.add("conversationIds", existingCourseWideChannelIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        params.add("filterToCourseWide", "true");
        params.add("pagingEnabled", "true");
        params.add("page", "0");
        params.add("size", String.valueOf(MAX_POSTS_PER_PAGE));

        params.add("postSortCriterion", PostSortCriterion.CREATION_DATE.toString());
        params.add("sortingOrder", SortingOrder.DESCENDING.toString());

        List<PostResponseDTO> returnedPosts = request.getList("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, PostResponseDTO.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);

        int numberOfMaxAnswersSeenOnAnyPost = Integer.MAX_VALUE;
        for (PostResponseDTO post : returnedPosts) {
            int answerCount = post.answers() == null ? 0 : post.answers().size();
            assertThat(answerCount).isLessThanOrEqualTo(numberOfMaxAnswersSeenOnAnyPost);
            numberOfMaxAnswersSeenOnAnyPost = answerCount;
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseWideMessages_OrderByCreationDateASC() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();

        // ordering only available in course discussions page, where paging is enabled
        params.add("conversationIds", existingCourseWideChannelIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        params.add("filterToCourseWide", "true");
        params.add("pagingEnabled", "true");
        params.add("page", "0");
        params.add("size", String.valueOf(MAX_POSTS_PER_PAGE));

        params.add("postSortCriterion", PostSortCriterion.CREATION_DATE.toString());
        params.add("sortingOrder", SortingOrder.ASCENDING.toString());

        List<PostResponseDTO> returnedPosts = request.getList("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, PostResponseDTO.class, params);
        conversationUtilService.assertSensitiveInformationHidden(returnedPosts);

        int numberOfMaxAnswersSeenOnAnyPost = 0;
        for (PostResponseDTO post : returnedPosts) {
            int answerCount = post.answers() == null ? 0 : post.answers().size();
            assertThat(answerCount).isGreaterThanOrEqualTo(numberOfMaxAnswersSeenOnAnyPost);
            numberOfMaxAnswersSeenOnAnyPost = answerCount;
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testGetCourseWideMessagesFromOneChannel() throws Exception {
        // conversation set will fetch all posts of conversation if the user is involved
        var params = new LinkedMultiValueMap<String, String>();
        var courseWidePosts = existingConversationMessages.stream().filter(post -> post.getConversation() instanceof Channel channel && channel.getIsCourseWide()).toList();
        Long courseWideChannelId = courseWidePosts.getFirst().getConversation().getId();
        params.add("conversationIds", courseWideChannelId.toString());
        params.add("filterToCourseWide", "true");

        List<PostResponseDTO> returnedPosts = request.getList("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, PostResponseDTO.class, params);
        // get amount of posts with that certain
        assertThat(returnedPosts)
                .hasSize(existingConversationMessages.stream().filter(post -> Objects.equals(post.getConversation().getId(), courseWideChannelId)).toList().size());
        assertThat(returnedPosts.size()).isLessThan(courseWidePosts.size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseWideMessages_WithOwnWithCourseWideContentAndSearchText() throws Exception {
        // conversation set will fetch all posts of conversation if the user is involved
        var params = new LinkedMultiValueMap<String, String>();
        var courseWidePosts = existingConversationMessages.stream().filter(post -> post.getConversation() instanceof Channel channel && channel.getIsCourseWide()).toList();
        var courseWideChannelId = courseWidePosts.getFirst().getConversation().getId();
        var userId = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow().getId();
        params.add("conversationIds", courseWideChannelId.toString());
        params.add("filterToCourseWide", "true");
        params.add("authorIds", String.valueOf(userId));
        params.add("size", "50");
        params.add("searchText", "Content");

        List<PostResponseDTO> returnedPosts = request.getList("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, PostResponseDTO.class, params);
        // get amount of posts with that certain
        assertThat(returnedPosts).hasSize(existingCourseWideMessages.stream().filter(post -> post.getConversation().getId().equals(courseWideChannelId)
                && post.getAuthor().getLogin().equals(TEST_PREFIX + "student1") && (post.getContent().contains("Content") || answerHasContext(post.getAnswers(), "Content")))
                .toList().size());
        assertThat(returnedPosts.size()).isLessThan(courseWidePosts.size());
    }

    private boolean answerHasContext(Set<AnswerPost> answers, String searchText) {
        return answers.stream().anyMatch(answerPost -> answerPost.getContent().contains(searchText));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseWideMessages_WithOwnWithCourseWideContentAndSearchTextInAnswer() throws Exception {
        // conversation set will fetch all posts of conversation if the user is involved
        var params = new LinkedMultiValueMap<String, String>();
        var courseWidePosts = existingConversationMessages.stream().filter(post -> post.getConversation() instanceof Channel channel && channel.getIsCourseWide()).toList();
        var courseWideChannelId = courseWidePosts.getFirst().getConversation().getId();
        var userId = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow().getId();
        courseWidePosts.getFirst().getAnswers().forEach(answer -> answer.setContent("AnswerPost"));
        params.add("conversationIds", courseWideChannelId.toString());
        params.add("filterToCourseWide", "true");
        params.add("authorIds", String.valueOf(userId));
        params.add("size", "50");
        params.add("searchText", "Answer");

        List<PostResponseDTO> returnedPosts = request.getList("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, PostResponseDTO.class, params);
        // get amount of posts with that certain
        assertThat(returnedPosts).hasSize(existingCourseWideMessages.stream().filter(post -> post.getConversation().getId().equals(courseWideChannelId)
                && post.getAuthor().getLogin().equals(TEST_PREFIX + "student1") && (post.getContent().contains("answer") || answerHasContext(post.getAnswers(), "Content")))
                .toList().size());
        assertThat(returnedPosts.size()).isLessThan(courseWidePosts.size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1")
    void testEditConversationPost() throws Exception {
        // conversation post of tutor1 must be only editable by them
        Post conversationPostToUpdate = existingConversationMessages.getFirst();
        conversationPostToUpdate.setContent("User changes one of their conversation posts");

        PostResponseDTO updatedPost = request.putWithResponseBody("/api/communication/courses/" + courseId + "/messages/" + conversationPostToUpdate.getId(),
                toUpdatePostingDTO(conversationPostToUpdate), PostResponseDTO.class, HttpStatus.OK);

        assertThat(updatedPost.id()).isEqualTo(conversationPostToUpdate.getId());
        assertThat(updatedPost.content()).isEqualTo(conversationPostToUpdate.getContent());

        // both conversation participants should be notified about the update
        verify(websocketMessagingService, timeout(2000).times(2)).sendMessage(anyString(),
                (Object) argThat(argument -> argument instanceof PostBroadcastDTO postBroadcastDTO && idOf(postBroadcastDTO.post()).equals(idOf(updatedPost))));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testPinPost_asTutor() throws Exception {
        Post postToPin = existingConversationMessages.getFirst();
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("displayPriority", DisplayPriority.PINNED.toString());

        // change display priority to PINNED
        PostResponseDTO updatedPost = request.putWithResponseBodyAndParams("/api/communication/courses/" + courseId + "/messages/" + postToPin.getId() + "/display-priority", null,
                PostResponseDTO.class, HttpStatus.OK, params);
        conversationUtilService.assertSensitiveInformationHidden(updatedPost);
        assertThat(updatedPost.id()).isEqualTo(postToPin.getId());
        assertThat(updatedPost.displayPriority()).isEqualTo(DisplayPriority.PINNED);
    }

    @ParameterizedTest
    @MethodSource("userMentionProvider")
    @WithMockUser(username = TEST_PREFIX + "tutor1")
    void testEditConversationPostWithUserMention(String userMention, boolean isUserMentionValid) throws Exception {
        // conversation post of tutor1 must be only editable by them
        Post postToUpdate = existingConversationMessages.getFirst();
        postToUpdate.setContent("User changes one of their conversation posts" + userMention);

        if (!isUserMentionValid) {
            request.putWithResponseBody("/api/communication/courses/" + courseId + "/messages/" + postToUpdate.getId(), toUpdatePostingDTO(postToUpdate), PostResponseDTO.class,
                    HttpStatus.BAD_REQUEST);
            verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostBroadcastDTO.class));
            return;
        }

        PostResponseDTO updatedPost = request.putWithResponseBody("/api/communication/courses/" + courseId + "/messages/" + postToUpdate.getId(), toUpdatePostingDTO(postToUpdate),
                PostResponseDTO.class, HttpStatus.OK);

        assertThat(updatedPost.id()).isEqualTo(postToUpdate.getId());
        assertThat(updatedPost.content()).isEqualTo(postToUpdate.getContent());

        // both conversation participants should be notified about the update
        verify(websocketMessagingService, timeout(2000).times(2)).sendMessage(anyString(),
                (Object) argThat(argument -> argument instanceof PostBroadcastDTO postBroadcastDTO && idOf(postBroadcastDTO.post()).equals(idOf(updatedPost))));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testEditConversationPost_forbidden() throws Exception {
        // conversation post of tutor1 must not be editable by tutor2
        Post conversationPostToUpdate = existingConversationMessages.getFirst();
        conversationPostToUpdate.setContent("Tutor attempts to change some other user's conversation post");

        PostResponseDTO notUpdatedPost = request.putWithResponseBody("/api/communication/courses/" + courseId + "/messages/" + conversationPostToUpdate.getId(),
                toUpdatePostingDTO(conversationPostToUpdate), PostResponseDTO.class, HttpStatus.FORBIDDEN);

        assertThat(notUpdatedPost).isNull();

        // conversation participants should not be notified
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostBroadcastDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1")
    void testDeleteConversationPost() throws Exception {
        // conversation post of tutor1 must be deletable by them
        Post conversationPostToDelete = existingConversationMessages.getFirst();
        request.delete("/api/communication/courses/" + courseId + "/messages/" + conversationPostToDelete.getId(), HttpStatus.OK);

        assertThat(conversationMessageRepository.findById(conversationPostToDelete.getId())).isEmpty();
        // both conversation participants should be notified
        verify(websocketMessagingService, timeout(2000).times(2)).sendMessage(anyString(),
                (Object) argThat(argument -> argument instanceof PostBroadcastDTO postBroadcastDTO && idOf(postBroadcastDTO.post()).equals(idOf(conversationPostToDelete))));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testDeleteConversationPost_forbidden() throws Exception {
        // conversation post of user must not be deletable by tutors
        Post conversationPostToDelete = existingConversationMessages.getFirst();
        request.delete("/api/communication/courses/" + courseId + "/messages/" + conversationPostToDelete.getId(), HttpStatus.FORBIDDEN);

        assertThat(conversationMessageRepository.findById(conversationPostToDelete.getId())).isPresent();
        // conversation participants should not be notified
        verify(websocketMessagingService, never()).sendMessageToUser(anyString(), anyString(), any(PostBroadcastDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testIncreaseUnreadMessageCountAfterMessageSend() throws Exception {
        var student1 = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        var student2 = userTestRepository.findOneByLogin(TEST_PREFIX + "student2").orElseThrow();

        Post postToSave = createPostWithOneToOneChat(TEST_PREFIX);
        PostResponseDTO createdPost = createPostAndAwaitAsyncCode(postToSave);

        await().untilAsserted(() -> {
            SecurityUtils.setAuthorizationObject();
            // student1 (author) should have 0 unread messages, student2 (conversation participant) should have 1 unread message
            assertThat(getUnreadMessagesCount(createdPost.conversation().id(), student1)).isZero();
            assertThat(getUnreadMessagesCount(createdPost.conversation().id(), student2)).isEqualTo(1);
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDecreaseUnreadMessageCountAfterMessageRead() throws Exception {
        var student1 = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        var student2 = userTestRepository.findOneByLogin(TEST_PREFIX + "student2").orElseThrow();

        Post postToSave1 = createPostWithOneToOneChat(TEST_PREFIX);
        CreatePostDTO postDTOToSave1 = new CreatePostDTO(postToSave1.getContent(), "", false, new CreatePostConversationDTO(postToSave1.getConversation().getId()));
        PostResponseDTO createdPost1 = request.postWithResponseBody("/api/communication/courses/" + courseId + "/messages", postDTOToSave1, PostResponseDTO.class,
                HttpStatus.CREATED);

        userUtilService.changeUser(TEST_PREFIX + "student2");
        // we read the messages by "getting" them from the server as student
        var params = new LinkedMultiValueMap<String, String>();
        params.add("conversationIds", createdPost1.conversation().id().toString());
        params.add("pagingEnabled", "true");
        params.add("size", String.valueOf(MAX_POSTS_PER_PAGE));
        Set<PostResponseDTO> returnedPosts = request.getSet("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, PostResponseDTO.class, params);
        assertThat(returnedPosts).hasSize(1);

        await().untilAsserted(() -> {
            SecurityUtils.setAuthorizationObject();
            assertThat(getUnreadMessagesCount(createdPost1.conversation().id(), student2)).isZero();
            assertThat(getUnreadMessagesCount(createdPost1.conversation().id(), student1)).isZero();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDecreaseUnreadMessageCountWhenDeletingMessage() throws Exception {
        final var student1 = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        final var student2 = userTestRepository.findOneByLogin(TEST_PREFIX + "student2").orElseThrow();

        Post postToSave1 = createPostWithOneToOneChat(TEST_PREFIX);
        CreatePostDTO postToSaveDTO1 = new CreatePostDTO(postToSave1.getContent(), "", false, new CreatePostConversationDTO(postToSave1.getConversation().getId()));

        PostResponseDTO createdPost1 = request.postWithResponseBody("/api/communication/courses/" + courseId + "/messages", postToSaveDTO1, PostResponseDTO.class,
                HttpStatus.CREATED);
        final long oneToOneChat1Id = createdPost1.conversation().id();
        // student 1 adds a message, so the unread count for student 2 should be 1
        await().untilAsserted(() -> {
            SecurityUtils.setAuthorizationObject();
            assertThat(getUnreadMessagesCount(oneToOneChat1Id, student1)).isZero();
            assertThat(getUnreadMessagesCount(oneToOneChat1Id, student2)).isEqualTo(1);
        });

        request.delete("/api/communication/courses/" + courseId + "/messages/" + createdPost1.id(), HttpStatus.OK);
        // After deleting the message in the chat, the unread count in the chat should become 0
        await().untilAsserted(() -> {
            SecurityUtils.setAuthorizationObject();
            assertThat(getUnreadMessagesCount(oneToOneChat1Id, student2)).isZero();
            assertThat(getUnreadMessagesCount(oneToOneChat1Id, student1)).isZero();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testMarkMessageAsUnread() throws Exception {
        var student1 = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        var student2 = userTestRepository.findOneByLogin(TEST_PREFIX + "student2").orElseThrow();

        Post postToSave1 = createPostWithOneToOneChat(TEST_PREFIX);
        CreatePostDTO postDTOToSave1 = new CreatePostDTO(postToSave1.getContent(), "", false, new CreatePostConversationDTO(postToSave1.getConversation().getId()));
        PostResponseDTO createdPost1 = request.postWithResponseBody("/api/communication/courses/" + courseId + "/messages", postDTOToSave1, PostResponseDTO.class,
                HttpStatus.CREATED);

        // Create a second message in the same conversation to validate that marking one as unread increments the unread count
        Post postToSave2 = createPostWithOneToOneChat(TEST_PREFIX);
        CreatePostDTO postDTOToSave2 = new CreatePostDTO(postToSave2.getContent(), "", false, new CreatePostConversationDTO(postToSave1.getConversation().getId()));
        PostResponseDTO createdPost2 = request.postWithResponseBody("/api/communication/courses/" + courseId + "/messages", postDTOToSave2, PostResponseDTO.class,
                HttpStatus.CREATED);

        userUtilService.changeUser(TEST_PREFIX + "student2");
        // we read the messages by "getting" them from the server as student
        String conversationId = createdPost1.conversation().id().toString();
        String messageId = createdPost1.id().toString();

        // Wait for both post-creation @Async increments to settle: count must reach 2 before we proceed.
        // Without this gate, the subsequent mark-as-read @Async could race with stale post-creation increments,
        // and the baseline `unreadCount == 0` could be satisfied by the initial state alone (never observing
        // that mark-as-read actually ran).
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            SecurityUtils.setAuthorizationObject();
            assertThat(getUnreadMessagesCount(createdPost1.conversation().id(), student2)).isEqualTo(2);
        });

        // establish baseline: mark conversation as read and verify unread count is 0
        request.patch("/api/communication/courses/" + courseId + "/conversations/" + conversationId + "/mark-as-read", null, HttpStatus.OK);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            SecurityUtils.setAuthorizationObject();
            assertThat(getUnreadMessagesCount(createdPost1.conversation().id(), student2)).isZero();
        });

        request.patch("/api/communication/courses/" + courseId + "/conversations/" + conversationId + "/messages/" + messageId + "/mark-as-unread", null, HttpStatus.OK);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            SecurityUtils.setAuthorizationObject();
            assertThat(getUnreadMessagesCount(createdPost1.conversation().id(), student2)).isEqualTo(2);
            assertThat(getUnreadMessagesCount(createdPost1.conversation().id(), student1)).isZero();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testMarkMessageAsUnreadNonMemberForbidden() throws Exception {
        Post postToSave = createPostWithOneToOneChat(TEST_PREFIX);
        CreatePostDTO postDTOToSave = new CreatePostDTO(postToSave.getContent(), "", false, new CreatePostConversationDTO(postToSave.getConversation().getId()));
        PostResponseDTO createdPost = request.postWithResponseBody("/api/communication/courses/" + courseId + "/messages", postDTOToSave, PostResponseDTO.class,
                HttpStatus.CREATED);

        String conversationId = createdPost.conversation().id().toString();
        String messageId = createdPost.id().toString();

        userUtilService.changeUser(TEST_PREFIX + "student3");
        // student3 is not a participant in this conversation, should get 403 Forbidden
        request.patch("/api/communication/courses/" + courseId + "/conversations/" + conversationId + "/messages/" + messageId + "/mark-as-unread", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testMarkMessageAsUnreadInvalidMessageIdNotFound() throws Exception {
        Post postToSave = createPostWithOneToOneChat(TEST_PREFIX);
        CreatePostDTO postDTOToSave = new CreatePostDTO(postToSave.getContent(), "", false, new CreatePostConversationDTO(postToSave.getConversation().getId()));
        PostResponseDTO createdPost = request.postWithResponseBody("/api/communication/courses/" + courseId + "/messages", postDTOToSave, PostResponseDTO.class,
                HttpStatus.CREATED);

        String conversationId = createdPost.conversation().id().toString();
        long invalidMessageId = 99999L; // Non-existent message ID

        // Attempt to mark a non-existent message as unread, should get 404 Not Found
        request.patch("/api/communication/courses/" + courseId + "/conversations/" + conversationId + "/messages/" + invalidMessageId + "/mark-as-unread", null,
                HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testMarkMessageAsUnreadMessageNotBelongingToConversationNotFound() throws Exception {
        // Create message in first conversation
        Post postToSave1 = createPostWithOneToOneChat(TEST_PREFIX);
        CreatePostDTO postDTOToSave1 = new CreatePostDTO(postToSave1.getContent(), "", false, new CreatePostConversationDTO(postToSave1.getConversation().getId()));
        PostResponseDTO createdPost1 = request.postWithResponseBody("/api/communication/courses/" + courseId + "/messages", postDTOToSave1, PostResponseDTO.class,
                HttpStatus.CREATED);

        // Create message in a different conversation
        Post postToSave2 = createPostWithOneToOneChat(TEST_PREFIX);
        CreatePostDTO postDTOToSave2 = new CreatePostDTO(postToSave2.getContent(), "", false, new CreatePostConversationDTO(postToSave2.getConversation().getId()));
        PostResponseDTO createdPost2 = request.postWithResponseBody("/api/communication/courses/" + courseId + "/messages", postDTOToSave2, PostResponseDTO.class,
                HttpStatus.CREATED);

        String conversationId1 = createdPost1.conversation().id().toString();
        String messageId2 = createdPost2.id().toString();

        // Try to mark a message from a different conversation as unread, should get 404 Not Found
        request.patch("/api/communication/courses/" + courseId + "/conversations/" + conversationId1 + "/messages/" + messageId2 + "/mark-as-unread", null, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testMarkOwnMessageAsUnreadBadRequest() throws Exception {
        Post postToSave = createPostWithOneToOneChat(TEST_PREFIX);
        postToSave.setAuthor(userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow());
        CreatePostDTO postDTOToSave = new CreatePostDTO(postToSave.getContent(), "", false, new CreatePostConversationDTO(postToSave.getConversation().getId()));
        PostResponseDTO createdPost = request.postWithResponseBody("/api/communication/courses/" + courseId + "/messages", postDTOToSave, PostResponseDTO.class,
                HttpStatus.CREATED);

        String conversationId = createdPost.conversation().id().toString();
        String messageId = createdPost.id().toString();

        // Author attempting to mark their own message as unread should get 400 Bad Request
        request.patch("/api/communication/courses/" + courseId + "/conversations/" + conversationId + "/messages/" + messageId + "/mark-as-unread", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCourseWideMessagesWithPinnedOnly() throws Exception {
        Channel channel = conversationUtilService.createCourseWideChannel(course, "channel-for-pinned-test", false);
        ConversationParticipant instructorParticipant = conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "instructor1");

        Post unpinnedPost = new Post();
        unpinnedPost.setAuthor(instructorParticipant.getUser());
        unpinnedPost.setConversation(channel);
        PostResponseDTO createdUnpinnedPost = request.postWithResponseBody("/api/communication/courses/" + courseId + "/messages", toCreatePostDTO(unpinnedPost),
                PostResponseDTO.class, HttpStatus.CREATED);

        Post pinnedPost = new Post();
        pinnedPost.setAuthor(instructorParticipant.getUser());
        pinnedPost.setConversation(channel);
        PostResponseDTO createdPinnedPost = request.postWithResponseBody("/api/communication/courses/" + courseId + "/messages", toCreatePostDTO(pinnedPost), PostResponseDTO.class,
                HttpStatus.CREATED);

        MultiValueMap<String, String> paramsPin = new LinkedMultiValueMap<>();
        paramsPin.add("displayPriority", DisplayPriority.PINNED.toString());
        PostResponseDTO updatedPinnedPost = request.putWithResponseBodyAndParams(
                "/api/communication/courses/" + courseId + "/messages/" + createdPinnedPost.id() + "/display-priority", null, PostResponseDTO.class, HttpStatus.OK, paramsPin);

        assertThat(updatedPinnedPost.displayPriority()).isEqualTo(DisplayPriority.PINNED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("conversationIds", channel.getId().toString());
        params.add("filterToCourseWide", "true");
        params.add("pinnedOnly", "true");
        params.add("size", "10");

        List<PostResponseDTO> pinnedPosts = request.getList("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, PostResponseDTO.class, params);

        assertThat(pinnedPosts).hasSize(1);
        assertThat(pinnedPosts.getFirst().id()).isEqualTo(updatedPinnedPost.id());

        params.set("pinnedOnly", "false");
        List<PostResponseDTO> allPosts = request.getList("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, PostResponseDTO.class, params);

        assertThat(allPosts).hasSize(2);
        assertThat(allPosts).extracting(PostResponseDTO::id).containsExactlyInAnyOrder(createdPinnedPost.id(), createdUnpinnedPost.id());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetCourseWideMessagesWithAuthorIds() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        var courseWidePosts = existingConversationMessages.stream().filter(post -> post.getConversation() instanceof Channel channel && channel.getIsCourseWide()).toList();
        var courseWideChannelId = courseWidePosts.getFirst().getConversation().getId();
        var authorId = courseWidePosts.getFirst().getAuthor().getId();
        params.add("conversationIds", courseWideChannelId.toString());
        params.add("filterToCourseWide", "true");
        params.add("authorIds", authorId.toString());
        params.add("size", "50");

        List<PostResponseDTO> returnedPosts = request.getList("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, PostResponseDTO.class, params);
        // get amount of posts with that certain
        assertThat(returnedPosts).hasSize(existingCourseWideMessages.stream()
                .filter(post -> post.getConversation().getId().equals(courseWideChannelId) && post.getAuthor().getId().equals(authorId)).toList().size());
        assertThat(returnedPosts.size()).isLessThan(courseWidePosts.size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testGetMessagesForPrivateConversationId_Forbidden() throws Exception {
        Post directPost = createPostWithOneToOneChat(TEST_PREFIX);
        var oneToOneChat = directPost.getConversation();

        var params = new LinkedMultiValueMap<String, String>();
        params.add("conversationIds", oneToOneChat.getId().toString());
        params.add("size", "50");

        request.getList("/api/communication/courses/" + courseId + "/messages", HttpStatus.FORBIDDEN, PostResponseDTO.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldIncludeDirectMessagesWhenFindingMessages() throws Exception {
        Post directPost = createPostWithOneToOneChat(TEST_PREFIX);
        directPost.setContent("SearchTestDirect");
        request.postWithResponseBody("/api/communication/courses/" + courseId + "/messages", toCreatePostDTO(directPost), PostResponseDTO.class, HttpStatus.CREATED);

        // include the newly created conversation into all course-wide conversations
        long[] conversationIds = Stream.concat(existingCourseWideChannelIds.stream(), Stream.of(directPost.getConversation().getId())).mapToLong(Long::longValue).toArray();

        PostContextFilterDTO filter = new PostContextFilterDTO(course.getId(), null, conversationIds, null, "SearchTest", false, false, false, PostSortCriterion.CREATION_DATE,
                SortingOrder.DESCENDING);

        var student1 = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        Page<Post> searchResults = conversationMessageRepository.findMessages(filter, Pageable.unpaged(), student1.getId());
        List<Post> resultPosts = searchResults.getContent();

        assertThat(resultPosts).extracting(Post::getContent).contains("SearchTestDirect");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldIncludeDirectMessagesWhenFindingMessagesUsingGetRequest() throws Exception {
        Post directPost = createPostWithOneToOneChat(TEST_PREFIX);
        directPost.setContent("SearchTestDirect");
        request.postWithResponseBody("/api/communication/courses/" + courseId + "/messages", toCreatePostDTO(directPost), PostResponseDTO.class, HttpStatus.CREATED);

        // include the newly created conversation into all conversations
        String conversationIds = Stream.concat(existingCourseWideChannelIds.stream(), Stream.of(directPost.getConversation().getId())).map(String::valueOf)
                .collect(Collectors.joining(","));

        var params = new LinkedMultiValueMap<String, String>();
        params.add("searchText", "SearchTest");
        params.add("conversationIds", conversationIds);

        List<PostResponseDTO> resultPosts = request.getList("/api/communication/courses/" + courseId + "/messages", HttpStatus.OK, PostResponseDTO.class, params);

        assertThat(resultPosts).extracting(PostResponseDTO::content).contains("SearchTestDirect");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldIncludeNonCourseWideChannelsWhenFindingMessages() throws Exception {
        Channel nonCourseWideChannel = conversationUtilService.createPublicChannel(course, "group-chat-test");
        conversationUtilService.addParticipantToConversation(nonCourseWideChannel, TEST_PREFIX + "student1");
        conversationUtilService.addParticipantToConversation(nonCourseWideChannel, TEST_PREFIX + "student2");

        Post groupPost = new Post();
        groupPost.setAuthor(userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow());
        groupPost.setConversation(nonCourseWideChannel);
        groupPost.setContent("SearchTestGroup");
        request.postWithResponseBody("/api/communication/courses/" + courseId + "/messages", toCreatePostDTO(groupPost), PostResponseDTO.class, HttpStatus.CREATED);

        // include the newly created conversation into all course-wide conversations
        long[] conversationIds = Stream.concat(existingCourseWideChannelIds.stream(), Stream.of(nonCourseWideChannel.getId())).mapToLong(Long::longValue).toArray();

        PostContextFilterDTO filter = new PostContextFilterDTO(course.getId(), null, conversationIds, null, "SearchTest", false, false, false, PostSortCriterion.CREATION_DATE,
                SortingOrder.DESCENDING);

        var student1 = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        Page<Post> searchResults = conversationMessageRepository.findMessages(filter, Pageable.unpaged(), student1.getId());
        List<Post> resultPosts = searchResults.getContent();

        assertThat(resultPosts).extracting(Post::getContent).contains("SearchTestGroup");
    }

    @ParameterizedTest
    @MethodSource("searchQueryAndAuthorProvider")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testWhetherShouldIncludeBasePostWhenSearchingMessagesByContentAndAuthor(String searchQuery, String authorLogin, boolean shouldBeIncluded) throws Exception {
        // Create base post by student1 with content "base"
        Post basePostEntity = createPostWithOneToOneChat(TEST_PREFIX);
        basePostEntity.setContent("base");
        PostResponseDTO basePost = createPostAndAwaitAsyncCode(basePostEntity);
        // propagate the persisted id back to the entity so subsequent answer-post creation can resolve the parent
        basePostEntity.setId(basePost.id());

        // Add an answer from student2 with content "answer" to the base post
        userUtilService.changeUser(TEST_PREFIX + "student2");
        AnswerPost answer = new AnswerPost();
        answer.setContent("answer");
        answer.setPost(basePostEntity);
        createAnswerPostAndAwaitAsyncCode(answer);

        // Use PostContextFilterDTO and conversationMessageRepository.findMessages
        long conversationId = basePost.conversation().id();
        long authorId = userTestRepository.findOneByLogin(authorLogin).orElseThrow().getId();
        PostContextFilterDTO filter = new PostContextFilterDTO(courseId, null, new long[] { conversationId }, new long[] { authorId }, searchQuery, false, false, false,
                PostSortCriterion.CREATION_DATE, SortingOrder.DESCENDING);
        Page<Post> returnedPage = conversationMessageRepository.findMessages(filter, Pageable.unpaged(), 0L /* Not used here */);
        List<Post> returnedPosts = returnedPage.getContent();

        if (shouldBeIncluded) {
            assertThat(returnedPosts).isNotEmpty();
            assertThat(returnedPosts).extracting(Post::getContent).contains("base");
        }
        else {
            assertThat(returnedPosts).isEmpty();
        }
    }

    private static Stream<Arguments> searchQueryAndAuthorProvider() {
        String baseAuthor = TEST_PREFIX + "student1";
        String answerAuthor = TEST_PREFIX + "student2";

        return Stream.of(Arguments.of("base", baseAuthor, true), Arguments.of("answer", answerAuthor, true), Arguments.of("base", answerAuthor, false),
                Arguments.of("answer", baseAuthor, false));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldSendCourseNotificationForNewPostWhenFeatureIsEnabled() throws Exception {
        var channel = createChannelWithTwoStudents();
        Post postToSave = new Post();
        postToSave.setAuthor(userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow());
        postToSave.setConversation(channel);
        postToSave.setContent("Test content for notification");

        request.postWithResponseBody("/api/communication/courses/" + courseId + "/messages", toCreatePostDTO(postToSave), PostResponseDTO.class, HttpStatus.CREATED);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<CourseNotification> notifications = courseNotificationRepository.findAll();
            assertThat(notifications).filteredOn(notification -> notification.getCourse().getId().equals(courseId)).filteredOn(notification -> notification.getType() == 1)
                    .isNotEmpty();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldSendMentionNotificationForNewPostWhenFeatureIsEnabled() throws Exception {
        User mentionedUser = userTestRepository.findOneByLogin(TEST_PREFIX + "student2").orElseThrow();

        var channel = createChannelWithTwoStudents();
        Post postToSave = new Post();
        postToSave.setAuthor(userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow());
        postToSave.setConversation(channel);
        postToSave.setContent("[user]" + mentionedUser.getName() + "(" + mentionedUser.getLogin() + ")[/user] Check this out!");

        CreatePostDTO postDTOToSave = new CreatePostDTO(postToSave.getContent(), postToSave.getTitle(), postToSave.getHasForwardedMessages(),
                new CreatePostConversationDTO(postToSave.getConversation().getId()));

        request.postWithResponseBody("/api/communication/courses/" + courseId + "/messages", postDTOToSave, PostResponseDTO.class, HttpStatus.CREATED);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<CourseNotification> notifications = courseNotificationRepository.findAll();
            assertThat(notifications).filteredOn(notification -> notification.getCourse().getId().equals(courseId)).filteredOn(notification -> notification.getType() == 3)
                    .isNotEmpty();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldSendAnnouncementNotificationWhenFeatureIsEnabled() throws Exception {
        Channel channel = conversationUtilService.createAnnouncementChannel(course, "test-announcement");
        conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1");
        ConversationParticipant author = conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "instructor1");

        Post postToSave = new Post();
        postToSave.setAuthor(author.getUser());
        postToSave.setConversation(channel);
        postToSave.setContent("Important announcement!");

        CreatePostDTO postDTOToSave = new CreatePostDTO(postToSave.getContent(), postToSave.getTitle(), postToSave.getHasForwardedMessages(),
                new CreatePostConversationDTO(postToSave.getConversation().getId()));

        request.postWithResponseBody("/api/communication/courses/" + courseId + "/messages", postDTOToSave, PostResponseDTO.class, HttpStatus.CREATED);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<CourseNotification> notifications = courseNotificationRepository.findAll();
            assertThat(notifications).filteredOn(notification -> notification.getCourse().getId().equals(courseId)).filteredOn(notification -> notification.getType() == 4)
                    .isNotEmpty();
        });
    }

    private Channel createChannelWithTwoStudents() {
        Course course = courseRepository.findByIdElseThrow(courseId);
        Channel channel = conversationUtilService.createPublicChannel(course, "test");
        conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1");
        conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student2");
        return channel;
    }

    private long getUnreadMessagesCount(Conversation conversation, User user) {
        return getUnreadMessagesCount(conversation.getId(), user);
    }

    /**
     * Long-id overload of {@link #getUnreadMessagesCount(Conversation, User)} so test sites that have only the
     * {@link de.tum.cit.aet.artemis.communication.dto.ConversationRefDTO} from a {@code PostResponseDTO} can
     * still query unread-message counts without re-resolving the conversation entity.
     */
    private long getUnreadMessagesCount(Long conversationId, User user) {
        return oneToOneChatRepository.findByIdWithConversationParticipantsAndUserGroups(conversationId).orElseThrow().getConversationParticipants().stream()
                .filter(conversationParticipant -> Objects.equals(conversationParticipant.getUser().getId(), user.getId())).findFirst().orElseThrow().getUnreadMessagesCount();
    }

    private Post createPostWithOneToOneChat(String userPrefix) {
        var student1 = userTestRepository.findOneByLogin(userPrefix + "student1").orElseThrow();
        var student2 = userTestRepository.findOneByLogin(userPrefix + "student2").orElseThrow();
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

    private void checkCreatedMessagePost(Post expectedMessagePost, PostResponseDTO createdMessagePost) {
        assertThat(createdMessagePost).isNotNull();
        assertThat(createdMessagePost.id()).isNotNull();
        assertThat(createdMessagePost.content()).isEqualTo(expectedMessagePost.getContent());
        assertThat(createdMessagePost.creationDate()).isNotNull();
        // @JsonInclude(NON_EMPTY) on PostResponseDTO strips empty collections from the wire payload,
        // so the reactions and answers fields come back as null when there are none.
        assertThat(createdMessagePost.reactions()).isNullOrEmpty();
        assertThat(createdMessagePost.answers()).isNullOrEmpty();
    }

    protected static List<Arguments> userMentionProvider() {
        return userMentionProvider(TEST_PREFIX + "student1", TEST_PREFIX + "student2");
    }

    /**
     * Calls the POST /messages endpoint to create a new message
     *
     * @param postToSave post to save in the database
     * @return saved post
     */
    private PostResponseDTO createPostAndAwaitAsyncCode(Post postToSave) throws Exception {
        var postDTOToSave = new CreatePostDTO(postToSave.getContent(), postToSave.getTitle(), postToSave.getHasForwardedMessages(),
                new CreatePostConversationDTO(postToSave.getConversation().getId()));
        return request.postWithResponseBody("/api/communication/courses/" + courseId + "/messages", postDTOToSave, PostResponseDTO.class, HttpStatus.CREATED);
    }

    /**
     * Calls the POST /answer-messages endpoint to create a new answer post
     *
     * @param answerPostToSave answer post to save in the database
     * @return saved answer post
     */
    private AnswerPostResponseDTO createAnswerPostAndAwaitAsyncCode(AnswerPost answerPostToSave) throws Exception {
        return request.postWithResponseBody("/api/communication/courses/" + courseId + "/answer-messages", toCreateAnswerPostDTO(answerPostToSave), AnswerPostResponseDTO.class,
                HttpStatus.CREATED);
    }

    private static List<CourseInformationSharingConfiguration> courseInformationSharingConfigurationProvider() {
        return List.of(CourseInformationSharingConfiguration.COMMUNICATION_ONLY, CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
    }

    /**
     * Helper for mock-verification predicates that compare the cycle-free PostBroadcastDTO payload
     * sent over STOMP against either a {@link de.tum.cit.aet.artemis.communication.domain.Post}
     * entity, an {@link de.tum.cit.aet.artemis.communication.domain.AnswerPost} entity, or a
     * {@link de.tum.cit.aet.artemis.communication.dto.PostResponseDTO}. Both shapes carry the
     * post id; this helper extracts it regardless of which side the test is comparing.
     *
     * @param postLike either a Post entity, an AnswerPost entity, or a PostResponseDTO
     * @return the id, or {@code null} for unknown shapes
     */
    private static Long idOf(Object postLike) {
        return switch (postLike) {
            case Post post -> post.getId();
            // The broadcast carries the *parent* post, so for an AnswerPost input we compare against its parent's id.
            case AnswerPost answerPost -> answerPost.getPost() == null ? null : answerPost.getPost().getId();
            case PostResponseDTO dto -> dto.id();
            case null, default -> null;
        };
    }

    private CreatePostDTO toCreatePostDTO(Post post) {
        return new CreatePostDTO(post.getContent(), post.getTitle(), false, new CreatePostConversationDTO(post.getConversation().getId()));
    }

    private UpdatePostingDTO toUpdatePostingDTO(Post post) {
        return new UpdatePostingDTO(post.getId(), post.getContent(), post.getTitle(), false);
    }

    private CreateAnswerPostDTO toCreateAnswerPostDTO(AnswerPost answerPost) {
        return new CreateAnswerPostDTO(answerPost.getContent(), new ParentPostDTO(answerPost.getPost().getId()));
    }

}
