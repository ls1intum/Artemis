package de.tum.cit.aet.artemis.communication;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.io.IOException;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.ForwardedMessage;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.dto.ForwardedMessageDTO;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.test_repository.ForwardedMessageTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationFactory;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ForwardedMessageResourceIntegrationTest extends AbstractConversationTest {

    private static final String TEST_PREFIX = "fwtest";

    @Autowired
    private ForwardedMessageTestRepository forwardedMessageRepository;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    private Post testPost;

    private Post testPost2;

    private AnswerPost testAnswerPost;

    private ForwardedMessage testForwardedMessage;

    private ForwardedMessage forwardedMessageForAnswer;

    private User testUser;

    @BeforeEach
    void setUp() throws IOException {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);
        List<Course> courses = courseUtilService.createEnrolledCoursesWithExercisesAndLectures(TEST_PREFIX, true, 0);

        exampleCourse = courses.get(0);
        exampleCourse.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        exampleCourse = courseRepository.save(exampleCourse);
        exampleCourseId = exampleCourse.getId();

        testUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        Conversation conversation = ConversationFactory.generatePublicChannel(exampleCourse, "Test ForwardedMessage Channel", true);
        conversationRepository.save(conversation);
        conversationUtilService.addParticipantToConversation(conversation, TEST_PREFIX + "student1");

        testPost = ConversationFactory.createBasicPost(1, testUser);
        testPost.setConversation(conversation);
        testPost = conversationMessageRepository.save(testPost);

        testPost2 = ConversationFactory.createBasicPost(1, testUser);
        testPost2.setConversation(conversation);
        testPost2 = conversationMessageRepository.save(testPost2);

        testAnswerPost = new AnswerPost();
        testAnswerPost.setContent("Test Answer Post Content");
        testAnswerPost.setAuthor(testUser);
        testAnswerPost.setPost(testPost);
        testAnswerPost = answerPostRepository.save(testAnswerPost);

        testForwardedMessage = new ForwardedMessage();
        testForwardedMessage.setDestinationPost(testPost);
        testForwardedMessage.setSourceId(testPost.getId());
        testForwardedMessage.setSourceType(PostingType.POST);
        testForwardedMessage = forwardedMessageRepository.save(testForwardedMessage);

        forwardedMessageForAnswer = new ForwardedMessage();
        forwardedMessageForAnswer.setDestinationAnswerPost(testAnswerPost);
        forwardedMessageForAnswer.setSourceId(testPost.getId());
        forwardedMessageForAnswer.setSourceType(PostingType.POST);
        forwardedMessageRepository.save(forwardedMessageForAnswer);
    }

    /**
     * Test creating a new ForwardedMessage.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldCreateForwardedMessage() throws Exception {
        ForwardedMessage newForwardedMessage = new ForwardedMessage();
        newForwardedMessage.setDestinationPost(testPost);
        newForwardedMessage.setSourceId(testPost.getId());
        newForwardedMessage.setSourceType(PostingType.POST);
        ForwardedMessageDTO dto = new ForwardedMessageDTO(newForwardedMessage);

        URI uri = new URI("/api/communication/forwarded-messages");

        request.performMvcRequest(MockMvcRequestBuilders.post(uri).param("courseId", exampleCourseId.toString()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))).andExpect(MockMvcResultMatchers.status().isCreated()).andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.destinationPostId").value(testPost.getId())).andExpect(jsonPath("$.sourceId").value(testPost.getId()))
                .andExpect(jsonPath("$.sourceType").value("POST"));
    }

    /**
     * A forwarded message points at exactly one destination, so naming both is malformed input rather than a server
     * fault: without this the second setter on the entity throws IllegalStateException and the request answers 500.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRejectForwardedMessageWithTwoDestinations() throws Exception {
        ForwardedMessageDTO dto = new ForwardedMessageDTO(null, testPost.getId(), PostingType.POST, testPost.getId(), testAnswerPost.getId());

        request.performMvcRequest(MockMvcRequestBuilders.post(new URI("/api/communication/forwarded-messages")).param("courseId", exampleCourseId.toString())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto))).andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    /**
     * The other half of the same invariant. Nothing rejects a message with no destination before the insert, so
     * without this validation it reaches the check constraint that ForwardedMessage declares and fails there.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRejectForwardedMessageWithoutDestination() throws Exception {
        ForwardedMessageDTO dto = new ForwardedMessageDTO(null, testPost.getId(), PostingType.POST, null, null);

        request.performMvcRequest(MockMvcRequestBuilders.post(new URI("/api/communication/forwarded-messages")).param("courseId", exampleCourseId.toString())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto))).andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    /**
     * Test retrieving forwarded messages for destination IDs of type 'post'.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnForwardedMessagesForPosts() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.get("/api/communication/forwarded-messages").param("courseId", exampleCourseId.toString())
                .param("postingIds", String.valueOf(testPost.getId())).param("type", PostingType.POST.toString())).andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(testPost.getId())).andExpect(jsonPath("$[0].messages", hasSize(1)))
                .andExpect(jsonPath("$[0].messages[0].id").value(testForwardedMessage.getId()))
                .andExpect(jsonPath("$[0].messages[0].sourceId").value(testForwardedMessage.getSourceId())).andExpect(jsonPath("$[0].messages[0].sourceType").value("POST"));
    }

    /**
     * Test retrieving forwarded messages for destination IDs of type 'answer'.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnForwardedMessagesForAnswers() throws Exception {
        request.performMvcRequest(MockMvcRequestBuilders.get("/api/communication/forwarded-messages").param("courseId", exampleCourseId.toString())
                .param("postingIds", String.valueOf(testAnswerPost.getId())).param("type", PostingType.ANSWER.toString())).andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(testAnswerPost.getId())).andExpect(jsonPath("$[0].messages", hasSize(1)))
                .andExpect(jsonPath("$[0].messages[0].id").value(forwardedMessageForAnswer.getId()))
                .andExpect(jsonPath("$[0].messages[0].sourceId").value(forwardedMessageForAnswer.getSourceId())).andExpect(jsonPath("$[0].messages[0].sourceType").value("POST"));
    }

    /**
     * Test retrieving forwarded messages with no results.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnEmptyWhenNoForwardedMessagesExist() throws Exception {
        request.performMvcRequest(
                MockMvcRequestBuilders.get("/api/communication/forwarded-messages").param("postingIds", testPost2.getId().toString()).param("type", PostingType.POST.toString()))
                .andExpect(MockMvcResultMatchers.status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    /**
     * Test invalid type handling.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldThrowServerErrorForInvalidType() throws Exception {
        request.performMvcRequest(
                MockMvcRequestBuilders.get("/api/communication/forwarded-messages").param("postingIds", String.valueOf(testPost.getId())).param("type", "invalidType"))
                .andExpect(MockMvcResultMatchers.status().is5xxServerError());
    }

    /**
     * Test creating a ForwardedMessage for an AnswerPost.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldCreateForwardedMessageForAnswerPost() throws Exception {
        ForwardedMessage entity = new ForwardedMessage();
        entity.setDestinationAnswerPost(testAnswerPost);
        entity.setSourceId(testPost.getId());
        entity.setSourceType(PostingType.POST);
        ForwardedMessageDTO dto = new ForwardedMessageDTO(entity);

        URI uri = new URI("/api/communication/forwarded-messages");

        request.performMvcRequest(MockMvcRequestBuilders.post(uri).param("courseId", exampleCourseId.toString()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))).andExpect(MockMvcResultMatchers.status().isCreated()).andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.destinationAnswerPostId").value(testAnswerPost.getId())).andExpect(jsonPath("$.sourceId").value(testPost.getId()))
                .andExpect(jsonPath("$.sourceType").value("POST"));
    }

    @ParameterizedTest
    @CsvSource({ "POST, POST", "POST, ANSWER", "ANSWER, POST", "ANSWER, ANSWER" })
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void shouldShareForwardedContentWithoutSourceConversationAccess(PostingType sourceType, PostingType destinationType) throws Exception {
        Post sourcePost = createPrivateSource();
        AnswerPost sourceAnswer = createAnswer(sourcePost, "Private reply that was not forwarded");
        long sourceId = sourceType == PostingType.POST ? sourcePost.getId() : sourceAnswer.getId();
        Conversation destination = conversationUtilService.createOneToOneChat(exampleCourse, testUser, userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
        Post destinationPost = conversationUtilService.addMessageToConversation(TEST_PREFIX + "student1", destination);
        AnswerPost destinationAnswer = destinationType == PostingType.ANSWER ? createAnswer(destinationPost, "Forwarded here") : null;
        forwardedMessageRepository.save(new ForwardedMessage(sourceId, sourceType, destinationAnswer == null ? destinationPost : null, destinationAnswer));

        request.performMvcRequest(MockMvcRequestBuilders.get(sourceEndpoint(sourceType)).param(sourceParameter(sourceType), Long.toString(sourceId)))
                .andExpect(MockMvcResultMatchers.status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(sourceId))
                .andExpect(jsonPath("$[0].content").value(sourceType == PostingType.POST ? sourcePost.getContent() : sourceAnswer.getContent()))
                .andExpect(jsonPath("$[0].author.id").value(testUser.getId())).andExpect(jsonPath("$[0].conversation").doesNotExist())
                .andExpect(jsonPath("$[0].post").doesNotExist()).andExpect(jsonPath("$[0].answers").doesNotExist()).andExpect(jsonPath("$[0].reactions").doesNotExist());

        // Forwarding this message does not grant access to another message in its original conversation.
        long unrelatedId = sourceType == PostingType.POST ? conversationUtilService.addMessageToConversation(TEST_PREFIX + "student1", sourcePost.getConversation()).getId()
                : createAnswer(sourcePost, "Another private reply").getId();
        request.performMvcRequest(MockMvcRequestBuilders.get(sourceEndpoint(sourceType)).param(sourceParameter(sourceType), Long.toString(unrelatedId)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(PostingType.class)
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void shouldNotShareSourcesForwardedOnlyToAnotherConversation(PostingType sourceType) throws Exception {
        Post sourcePost = createPrivateSource();
        long sourceId = sourceType == PostingType.POST ? sourcePost.getId() : createAnswer(sourcePost, "Private source reply").getId();
        forwardedMessageRepository.save(new ForwardedMessage(sourceId, sourceType, sourcePost, null));
        request.performMvcRequest(MockMvcRequestBuilders.get(sourceEndpoint(sourceType)).param(sourceParameter(sourceType), Long.toString(sourceId)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void shouldNotForwardContentTheSenderCannotRead() throws Exception {
        Post sourcePost = createPrivateSource();
        Post ownDestination = conversationUtilService.addMessageToConversation(TEST_PREFIX + "student2", testPost.getConversation());
        ForwardedMessageDTO dto = new ForwardedMessageDTO(null, sourcePost.getId(), PostingType.POST, ownDestination.getId(), null);
        request.performMvcRequest(
                MockMvcRequestBuilders.post("/api/communication/forwarded-messages").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({ "POST, true, true", "POST, true, false", "ANSWER, true, true", "ANSWER, true, false", "POST, false, true", "POST, false, false", "ANSWER, false, true",
            "ANSWER, false, false" })
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void shouldNotRevealWhetherAnInaccessiblePostingExists(PostingType type, boolean probeSource, boolean exists) throws Exception {
        Post privateSource = createPrivateSource();
        long existingId = probeSource ? (type == PostingType.POST ? privateSource.getId() : createAnswer(privateSource, "Private reply").getId())
                : (type == PostingType.POST ? testPost.getId() : testAnswerPost.getId());
        long probeId = exists ? existingId : Long.MAX_VALUE;
        Post ownDestination = conversationUtilService.addMessageToConversation(TEST_PREFIX + "student2", testPost.getConversation());
        ForwardedMessageDTO dto = new ForwardedMessageDTO(null, probeSource ? probeId : testPost.getId(), probeSource ? type : PostingType.POST,
                probeSource ? ownDestination.getId() : type == PostingType.POST ? probeId : null, !probeSource && type == PostingType.ANSWER ? probeId : null);
        request.performMvcRequest(
                MockMvcRequestBuilders.post("/api/communication/forwarded-messages").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto)))
                .andExpect(MockMvcResultMatchers.status().isForbidden()).andExpect(jsonPath("$.detail").value(AccessForbiddenException.NOT_ALLOWED));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void shouldNotRevealPostingsInAnInaccessibleCourse() throws Exception {
        Course otherCourse = courseUtilService.createEnrolledCourseWithMessagingEnabled(TEST_PREFIX + "other");
        Conversation otherConversation = conversationRepository.save(ConversationFactory.generateCourseWideChannel(otherCourse));
        Post otherSource = conversationUtilService.addMessageToConversation(TEST_PREFIX + "student1", otherConversation);
        Post ownDestination = conversationUtilService.addMessageToConversation(TEST_PREFIX + "student2", testPost.getConversation());
        ForwardedMessageDTO sourceProbe = new ForwardedMessageDTO(null, otherSource.getId(), PostingType.POST, ownDestination.getId(), null);
        request.performMvcRequest(
                MockMvcRequestBuilders.post("/api/communication/forwarded-messages").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(sourceProbe)))
                .andExpect(MockMvcResultMatchers.status().isForbidden()).andExpect(jsonPath("$.detail").value(AccessForbiddenException.NOT_ALLOWED));

        Post otherDestination = conversationUtilService.addMessageToConversation(TEST_PREFIX + "student2", otherConversation);
        ForwardedMessageDTO destinationProbe = new ForwardedMessageDTO(null, testPost.getId(), PostingType.POST, otherDestination.getId(), null);
        request.performMvcRequest(MockMvcRequestBuilders.post("/api/communication/forwarded-messages").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(destinationProbe))).andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(jsonPath("$.detail").value(AccessForbiddenException.NOT_ALLOWED));
    }

    @ParameterizedTest
    @EnumSource(PostingType.class)
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void shouldStopSharingWhenRecipientLeavesDestination(PostingType sourceType) throws Exception {
        Post sourcePost = createPrivateSource();
        long sourceId = sourceType == PostingType.POST ? sourcePost.getId() : createAnswer(sourcePost, "Forwarded reply").getId();
        User recipient = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        Conversation destination = conversationUtilService.createOneToOneChat(exampleCourse, testUser, recipient);
        Post destinationPost = conversationUtilService.addMessageToConversation(TEST_PREFIX + "student1", destination);
        forwardedMessageRepository.save(new ForwardedMessage(sourceId, sourceType, destinationPost, null));
        request.performMvcRequest(MockMvcRequestBuilders.get(sourceEndpoint(sourceType)).param(sourceParameter(sourceType), Long.toString(sourceId)))
                .andExpect(MockMvcResultMatchers.status().isOk());

        var membership = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(destination.getId(), recipient.getId()).orElseThrow();
        conversationParticipantRepository.delete(membership);
        request.performMvcRequest(MockMvcRequestBuilders.get(sourceEndpoint(sourceType)).param(sourceParameter(sourceType), Long.toString(sourceId)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void shouldNotGrantSourceAccessThroughADestinationInAnotherCourse() throws Exception {
        Post sourcePost = createPrivateSource();
        Course otherCourse = courseUtilService.createEnrolledCourseWithMessagingEnabled(TEST_PREFIX);
        Conversation destination = conversationRepository.save(ConversationFactory.generateCourseWideChannel(otherCourse));
        Post destinationPost = conversationUtilService.addMessageToConversation(TEST_PREFIX + "student1", destination);
        forwardedMessageRepository.save(new ForwardedMessage(sourcePost.getId(), PostingType.POST, destinationPost, null));
        request.performMvcRequest(MockMvcRequestBuilders.get(sourceEndpoint(PostingType.POST)).param("postIds", sourcePost.getId().toString()))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void shouldNotGrantAccessThroughAnUnverifiedIrisDestination() throws Exception {
        Post source = createPrivateSource();
        AnswerPost destination = createAnswer(testPost, "Pending Iris reply");
        // This system account survives test cleanup. Reuse it instead of overwriting its profile through the generic user factory.
        User irisBot = userRepository.findOneWithAuthoritiesByLogin(User.IRIS_BOT_LOGIN).orElseGet(() -> {
            User newBot = userUtilService.createAndSaveUser(User.IRIS_BOT_LOGIN);
            newBot.setFirstName("Iris");
            newBot.setLastName("Bot");
            return userRepository.save(newBot);
        });
        destination.setAuthor(irisBot);
        destination.setVerified(false);
        destination = answerPostRepository.save(destination);
        forwardedMessageRepository.save(new ForwardedMessage(source.getId(), PostingType.POST, null, destination));
        request.performMvcRequest(MockMvcRequestBuilders.get(sourceEndpoint(PostingType.POST)).param("postIds", source.getId().toString()))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void shouldNotGrantAccessThroughAnUnreleasedExerciseDestination() throws Exception {
        Post source = createPrivateSource();
        var exercise = textExerciseUtilService.createIndividualTextExercise(exampleCourse, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2),
                ZonedDateTime.now().plusDays(3));
        Channel channel = conversationUtilService.addChannelToExercise(exercise);
        Post destination = conversationUtilService.addMessageToConversation(TEST_PREFIX + "student1", channel);
        forwardedMessageRepository.save(new ForwardedMessage(source.getId(), PostingType.POST, destination, null));
        request.performMvcRequest(MockMvcRequestBuilders.get(sourceEndpoint(PostingType.POST)).param("postIds", source.getId().toString()))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void shouldRejectAttachingAForwardToAnotherUsersMessage() throws Exception {
        ForwardedMessageDTO dto = new ForwardedMessageDTO(null, testPost2.getId(), PostingType.POST, testPost.getId(), null);
        request.performMvcRequest(
                MockMvcRequestBuilders.post("/api/communication/forwarded-messages").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRejectCreatingAForwardAcrossCourses() throws Exception {
        Course otherCourse = courseUtilService.createEnrolledCourseWithMessagingEnabled(TEST_PREFIX);
        Conversation otherConversation = conversationRepository.save(ConversationFactory.generateCourseWideChannel(otherCourse));
        Post destination = conversationUtilService.addMessageToConversation(TEST_PREFIX + "student1", otherConversation);
        ForwardedMessageDTO dto = new ForwardedMessageDTO(null, testPost.getId(), PostingType.POST, destination.getId(), null);
        request.performMvcRequest(
                MockMvcRequestBuilders.post("/api/communication/forwarded-messages").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRejectCreatingAForwardInAnArchivedChannel() throws Exception {
        Channel channel = (Channel) testPost.getConversation();
        channel.setIsArchived(true);
        channelRepository.save(channel);
        ForwardedMessageDTO dto = new ForwardedMessageDTO(null, testPost2.getId(), PostingType.POST, testPost.getId(), null);
        request.performMvcRequest(
                MockMvcRequestBuilders.post("/api/communication/forwarded-messages").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(PostingType.class)
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void shouldAllowForwardingReceivedContentOnward(PostingType sourceType) throws Exception {
        Post source = createPrivateSource();
        long sourceId = sourceType == PostingType.POST ? source.getId() : createAnswer(source, "Forwarded reply").getId();
        forwardedMessageRepository.save(new ForwardedMessage(sourceId, sourceType, testPost, null));
        Conversation destination = conversationUtilService.createOneToOneChat(exampleCourse, testUser, userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
        Post ownPost = conversationUtilService.addMessageToConversation(TEST_PREFIX + "student2", destination);
        ForwardedMessageDTO dto = new ForwardedMessageDTO(null, sourceId, sourceType, ownPost.getId(), null);
        request.performMvcRequest(
                MockMvcRequestBuilders.post("/api/communication/forwarded-messages").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(dto)))
                .andExpect(MockMvcResultMatchers.status().isCreated()).andExpect(jsonPath("$.sourceId").value(sourceId))
                .andExpect(jsonPath("$.sourceType").value(sourceType.name())).andExpect(jsonPath("$.destinationPostId").value(ownPost.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void shouldNotGrantAccessThroughAnUnreleasedExamDestination() throws Exception {
        Post source = createPrivateSource();
        var exam = examUtilService.addExam(exampleCourse, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2), ZonedDateTime.now().plusDays(3));
        Channel channel = ConversationFactory.generateCourseWideChannel(exampleCourse);
        channel.setExam(exam);
        channel = channelRepository.save(channel);
        Post destination = conversationUtilService.addMessageToConversation(TEST_PREFIX + "student1", channel);
        forwardedMessageRepository.save(new ForwardedMessage(source.getId(), PostingType.POST, destination, null));
        request.performMvcRequest(MockMvcRequestBuilders.get(sourceEndpoint(PostingType.POST)).param("postIds", source.getId().toString()))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    private Post createPrivateSource() {
        Conversation source = conversationRepository.save(ConversationFactory.generatePublicChannel(exampleCourse, "Private source", false));
        conversationUtilService.addParticipantToConversation(source, TEST_PREFIX + "student1");
        return conversationUtilService.addMessageToConversation(TEST_PREFIX + "student1", source);
    }

    private AnswerPost createAnswer(Post parent, String content) {
        AnswerPost answer = new AnswerPost();
        answer.setAuthor(testUser);
        answer.setContent(content);
        answer.setPost(parent);
        return answerPostRepository.save(answer);
    }

    private String sourceEndpoint(PostingType type) {
        return "/api/communication/courses/" + exampleCourseId + (type == PostingType.POST ? "/messages-source-posts" : "/answer-messages-source-posts");
    }

    private String sourceParameter(PostingType type) {
        return type == PostingType.POST ? "postIds" : "answerPostIds";
    }

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }
}
