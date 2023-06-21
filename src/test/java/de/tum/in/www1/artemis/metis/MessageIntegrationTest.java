package de.tum.in.www1.artemis.metis;

import static de.tum.in.www1.artemis.metis.AnswerPostIntegrationTest.MAX_POSTS_PER_PAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.validation.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.CourseInformationSharingConfiguration;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.CourseWideContext;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.conversation.OneToOneChat;
import de.tum.in.www1.artemis.post.ConversationUtilService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationMessageRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.OneToOneChatRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.PostContextFilter;
import de.tum.in.www1.artemis.web.websocket.dto.metis.PostDTO;

class MessageIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "messageintegration";

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

    private List<Post> existingPostsAndConversationPosts;

    private List<Post> existingConversationPosts;

    private List<Post> existingExercisePosts;

    private List<Post> existingLecturePosts;

    private Course course;

    private Long courseId;

    private Validator validator;

    private ValidatorFactory validatorFactory;

    private static final int NUMBER_OF_POSTS = 5;

    private static final int HIGHER_PAGE_SIZE = NUMBER_OF_POSTS + 10;

    private static final int LOWER_PAGE_SIZE = NUMBER_OF_POSTS - 2;

    private static final int EQUAL_PAGE_SIZE = NUMBER_OF_POSTS;

    @Autowired
    private CourseUtilService courseUtilService;

    @BeforeEach
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void initTestCase() {
        // used to test hibernate validation using custom PostContextConstraintValidator
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();

        userUtilService.addUsers(TEST_PREFIX, 4, 4, 4, 1);

        // initialize test setup and get all existing posts
        // (there are 4 posts with lecture context, 4 with exercise context, 3 with course-wide context and 3 with conversation initialized): 14 posts in total
        existingPostsAndConversationPosts = conversationUtilService.createPostsWithinCourse(TEST_PREFIX);

        List<Post> existingPosts = existingPostsAndConversationPosts.stream().filter(post -> post.getConversation() == null).toList();

        // filters existing posts with conversation
        existingConversationPosts = existingPostsAndConversationPosts.stream().filter(post -> post.getConversation() != null).toList();

        // filter existing posts with exercise context
        existingExercisePosts = existingPosts.stream().filter(coursePost -> (coursePost.getExercise() != null)).toList();

        // filter existing posts with lecture context
        existingLecturePosts = existingPosts.stream().filter(coursePost -> (coursePost.getLecture() != null)).toList();

        course = existingExercisePosts.get(0).getExercise().getCourseViaExerciseGroupOrCourseMember();
        courseUtilService.enableMessagingForCourse(course);

        courseId = course.getId();

        SimpMessageSendingOperations simpMessageSendingOperations = mock(SimpMessageSendingOperations.class);
        doNothing().when(simpMessageSendingOperations).convertAndSendToUser(any(), any(), any());
    }

    @AfterEach
    void tearDown() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateConversationPost() throws Exception {
        Post postToSave = createPostWithOneToOneChat(TEST_PREFIX);

        Post createdPost = request.postWithResponseBody("/api/courses/" + courseId + "/messages", postToSave, Post.class, HttpStatus.CREATED);
        checkCreatedMessagePost(postToSave, createdPost);
        assertThat(createdPost.getConversation().getId()).isNotNull();
        var requestingUser = userRepository.getUser();

        PostContextFilter postContextFilter = new PostContextFilter(courseId);
        postContextFilter.setConversationId(createdPost.getConversation().getId());
        assertThat(conversationMessageRepository.findMessages(postContextFilter, Pageable.unpaged(), requestingUser.getId())).hasSize(1);

        // both conversation participants should be notified
        verify(messagingTemplate, times(2)).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @ParameterizedTest
    @ValueSource(ints = { HIGHER_PAGE_SIZE, LOWER_PAGE_SIZE, EQUAL_PAGE_SIZE })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFindMessagesWithPageSizes(int pageSize) {

        var student1 = userRepository.findOneWithGroupsAndAuthoritiesByLogin(TEST_PREFIX + "student1").get();
        var student2 = userRepository.findOneWithGroupsAndAuthoritiesByLogin(TEST_PREFIX + "student2").get();
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
    void testMessagingNotAllowedIfCommunicationOnlySetting() throws Exception {
        messagingFeatureDisabledTest(CourseInformationSharingConfiguration.COMMUNICATION_ONLY);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testMessagingNotAllowedIfDisabledSetting() throws Exception {
        messagingFeatureDisabledTest(CourseInformationSharingConfiguration.DISABLED);
    }

    private void messagingFeatureDisabledTest(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        var persistedCourse = courseRepository.findByIdElseThrow(courseId);
        persistedCourse.setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);
        persistedCourse = courseRepository.saveAndFlush(persistedCourse);
        assertThat(persistedCourse.getCourseInformationSharingConfiguration()).isEqualTo(courseInformationSharingConfiguration);

        Post postToSave = createPostWithOneToOneChat(TEST_PREFIX);
        var requestingUser = userRepository.getUser();

        PostContextFilter postContextFilter = new PostContextFilter(courseId);
        postContextFilter.setConversationId(postToSave.getConversation().getId());
        var numberOfPostsBefore = conversationMessageRepository.findMessages(postContextFilter, Pageable.unpaged(), requestingUser.getId()).getSize();

        Post notCreatedPost = request.postWithResponseBody("/api/courses/" + courseId + "/messages", postToSave, Post.class, HttpStatus.BAD_REQUEST);

        assertThat(notCreatedPost).isNull();
        assertThat(conversationMessageRepository.findMessages(postContextFilter, Pageable.unpaged(), requestingUser.getId())).hasSize(numberOfPostsBefore);

        // conversation participants should not be notified
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));

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
        postToSave.setConversation(existingConversationPosts.get(0).getConversation());
        var requestingUser = userRepository.getUser();

        PostContextFilter postContextFilter = new PostContextFilter(courseId);
        postContextFilter.setConversationId(postToSave.getConversation().getId());
        var numberOfPostsBefore = conversationMessageRepository.findMessages(postContextFilter, Pageable.unpaged(), requestingUser.getId()).getSize();

        Post notCreatedPost = request.postWithResponseBody("/api/courses/" + courseId + "/messages", postToSave, Post.class, HttpStatus.FORBIDDEN);
        assertThat(notCreatedPost).isNull();
        assertThat(conversationMessageRepository.findMessages(postContextFilter, Pageable.unpaged(), requestingUser.getId())).hasSize(numberOfPostsBefore);

        // conversation participants should not be notified
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testValidatePostContextConstraintViolation() throws Exception {
        Post invalidPost = createPostWithOneToOneChat(TEST_PREFIX);
        invalidPost.setCourseWideContext(CourseWideContext.RANDOM);
        request.postWithResponseBody("/api/courses/" + courseId + "/messages", invalidPost, Post.class, HttpStatus.BAD_REQUEST);
        Set<ConstraintViolation<Post>> constraintViolations = validator.validate(invalidPost);
        assertThat(constraintViolations).hasSize(1);

        invalidPost = createPostWithOneToOneChat(TEST_PREFIX);
        invalidPost.setLecture(existingLecturePosts.get(0).getLecture());
        request.postWithResponseBody("/api/courses/" + courseId + "/messages", invalidPost, Post.class, HttpStatus.BAD_REQUEST);
        constraintViolations = validator.validate(invalidPost);
        assertThat(constraintViolations).hasSize(1);

        invalidPost = createPostWithOneToOneChat(TEST_PREFIX);
        invalidPost.setCourseWideContext(CourseWideContext.ORGANIZATION);
        invalidPost.setExercise(existingExercisePosts.get(0).getExercise());
        request.postWithResponseBody("/api/courses/" + courseId + "/messages", invalidPost, Post.class, HttpStatus.BAD_REQUEST);
        constraintViolations = validator.validate(invalidPost);
        assertThat(constraintViolations).hasSize(1);

        invalidPost = createPostWithOneToOneChat(TEST_PREFIX);
        invalidPost.setLecture(existingLecturePosts.get(0).getLecture());
        invalidPost.setExercise(existingExercisePosts.get(0).getExercise());
        request.postWithResponseBody("/api/courses/" + courseId + "/messages", invalidPost, Post.class, HttpStatus.BAD_REQUEST);
        constraintViolations = validator.validate(invalidPost);
        assertThat(constraintViolations).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void testGetConversationPost() throws Exception {
        // conversation set will fetch all posts of conversation if the user is involved
        var params = new LinkedMultiValueMap<String, String>();
        params.add("conversationId", existingConversationPosts.get(0).getConversation().getId().toString());

        List<Post> returnedPosts = request.getList("/api/courses/" + courseId + "/messages", HttpStatus.OK, Post.class, params);
        // get amount of posts with that certain
        assertThat(returnedPosts).hasSize(existingConversationPosts.size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1")
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
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
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
    @WithMockUser(username = TEST_PREFIX + "tutor1")
    void testDeleteConversationPost() throws Exception {
        // conversation post of tutor1 must be deletable by them
        Post conversationPostToDelete = existingConversationPosts.get(0);
        request.delete("/api/courses/" + courseId + "/messages/" + conversationPostToDelete.getId(), HttpStatus.OK);

        assertThat(conversationMessageRepository.findById(conversationPostToDelete.getId())).isEmpty();
        // both conversation participants should be notified
        verify(messagingTemplate, times(2)).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testDeleteConversationPost_forbidden() throws Exception {
        // conversation post of user must not be deletable by tutors
        Post conversationPostToDelete = existingConversationPosts.get(0);
        request.delete("/api/courses/" + courseId + "/messages/" + conversationPostToDelete.getId(), HttpStatus.FORBIDDEN);

        assertThat(conversationMessageRepository.findById(conversationPostToDelete.getId())).isPresent();
        // conversation participants should not be notified
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(PostDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testIncreaseUnreadMessageCountAfterMessageSend() throws Exception {
        Post postToSave = createPostWithOneToOneChat(TEST_PREFIX);
        Post createdPost = request.postWithResponseBody("/api/courses/" + courseId + "/messages", postToSave, Post.class, HttpStatus.CREATED);

        long unreadMessages = oneToOneChatRepository.findByIdWithConversationParticipantsAndUserGroups(createdPost.getConversation().getId()).get().getConversationParticipants()
                .stream().filter(conversationParticipant -> !Objects.equals(conversationParticipant.getUser().getId(), postToSave.getAuthor().getId())).findAny().orElseThrow()
                .getUnreadMessagesCount();
        assertThat(unreadMessages).isEqualTo(1L);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDecreaseUnreadMessageCountAfterMessageRead() throws Exception {
        Post postToSave1 = createPostWithOneToOneChat(TEST_PREFIX);

        ResultActions resultActions = request.getMvc()
                .perform(MockMvcRequestBuilders.post("/api/courses/" + courseId + "/messages").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postToSave1)).with(user(TEST_PREFIX + "student1").roles("USER")).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        MvcResult result = resultActions.andReturn();
        String contentAsString = result.getResponse().getContentAsString();
        Post createdPost1 = objectMapper.readValue(contentAsString, Post.class);

        request.getMvc()
                .perform(MockMvcRequestBuilders.get("/api/courses/" + courseId + "/messages").param("conversationId", createdPost1.getConversation().getId().toString())
                        .param("pagingEnabled", "true").param("size", String.valueOf(MAX_POSTS_PER_PAGE)).with(user(TEST_PREFIX + "student2").roles("USER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        SecurityContextHolder.setContext(TestSecurityContextHolder.getContext());
        long unreadMessages = oneToOneChatRepository.findByIdWithConversationParticipantsAndUserGroups(createdPost1.getConversation().getId()).get().getConversationParticipants()
                .stream().filter(conversationParticipant -> !Objects.equals(conversationParticipant.getUser().getId(), postToSave1.getAuthor().getId())).findAny().orElseThrow()
                .getUnreadMessagesCount();

        assertThat(unreadMessages).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDecreaseUnreadMessageCountWhenDeletingMessage() throws Exception {
        Post postToSave1 = createPostWithOneToOneChat(TEST_PREFIX);
        Post postToSave2 = createPostWithOneToOneChat(TEST_PREFIX);

        ResultActions resultActions = request.getMvc()
                .perform(MockMvcRequestBuilders.post("/api/courses/" + courseId + "/messages").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postToSave1)).with(user(TEST_PREFIX + "student1").roles("USER")).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        MvcResult result = resultActions.andReturn();
        String contentAsString = result.getResponse().getContentAsString();
        Post createdPost1 = objectMapper.readValue(contentAsString, Post.class);

        ResultActions resultActions2 = request.getMvc()
                .perform(MockMvcRequestBuilders.post("/api/courses/" + courseId + "/messages").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postToSave2)).with(user(TEST_PREFIX + "student1").roles("USER")).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        MvcResult result2 = resultActions2.andReturn();
        String contentAsString2 = result2.getResponse().getContentAsString();
        Post createdPost2 = objectMapper.readValue(contentAsString2, Post.class);

        request.getMvc().perform(MockMvcRequestBuilders.delete("/api/courses/" + courseId + "/messages/" + createdPost2.getId()).with(user(TEST_PREFIX + "student1").roles("USER"))
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk());

        SecurityContextHolder.setContext(TestSecurityContextHolder.getContext());
        long unreadMessages = oneToOneChatRepository.findByIdWithConversationParticipantsAndUserGroups(createdPost1.getConversation().getId()).get().getConversationParticipants()
                .stream().filter(conversationParticipant -> !Objects.equals(conversationParticipant.getUser().getId(), postToSave1.getAuthor().getId())).findAny().orElseThrow()
                .getUnreadMessagesCount();

        assertThat(unreadMessages).isEqualTo(1);
    }

    private Post createPostWithOneToOneChat(String userPrefix) {
        var student1 = userRepository.findOneWithGroupsAndAuthoritiesByLogin(userPrefix + "student1").get();
        var student2 = userRepository.findOneWithGroupsAndAuthoritiesByLogin(userPrefix + "student2").get();
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
        chat = oneToOneChatRepository.findByIdWithConversationParticipantsAndUserGroups(chat.getId()).get();
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

        // conversation posts should not have course initialized
        assertThat(createdMessagePost.getCourse()).isNull();
    }
}
