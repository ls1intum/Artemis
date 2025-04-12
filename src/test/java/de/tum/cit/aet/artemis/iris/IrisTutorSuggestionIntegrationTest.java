package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.DisplayPriority;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.test_repository.ConversationParticipantTestRepository;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTutorSuggestionSession;
import de.tum.cit.aet.artemis.iris.repository.IrisTutorSuggestionSessionRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class IrisTutorSuggestionIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "iristutorsuggestionintegration";

    @Autowired
    private IrisTutorSuggestionSessionRepository irisTutorSuggestionSessionRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    protected UserTestRepository userTestRepository;

    @Autowired
    private ConversationParticipantTestRepository conversationParticipantRepository;

    @Autowired
    private PostTestRepository postRepository;

    @Autowired
    private CourseTestRepository courseRepository;

    private ProgrammingExercise exercise;

    private Course course;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);

        course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        course = courseRepository.save(course);
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(exercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TUTOR")
    void createSession() throws Exception {
        var post = createPostInExerciseChat(exercise, TEST_PREFIX);
        var irisSession = request.postWithResponseBody(tutorSuggestionUrl(post.getId()), null, IrisTutorSuggestionSession.class, HttpStatus.CREATED);
        var actualIrisSession = irisTutorSuggestionSessionRepository.findByIdElseThrow(irisSession.getId());
        assertThat(actualIrisSession.getUser()).isEqualTo(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"));
        assertThat(post).isEqualTo(actualIrisSession.getPost());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TUTOR")
    void createSession_alreadyExists() throws Exception {
        var post = createPostInExerciseChat(exercise, TEST_PREFIX);
        var firstResponse = request.postWithResponseBody(tutorSuggestionUrl(post.getId()), null, IrisSession.class, HttpStatus.CREATED);
        var secondResponse = request.postWithResponseBody(tutorSuggestionUrl(post.getId()), null, IrisSession.class, HttpStatus.CREATED);
        assertThat(firstResponse).isNotEqualTo(secondResponse);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TUTOR")
    void getCurrentSession() throws Exception {
        var post = createPostInExerciseChat(exercise, TEST_PREFIX);
        var irisSession = request.postWithResponseBody(tutorSuggestionUrl(post.getId()), null, IrisSession.class, HttpStatus.CREATED);
        var currentIrisSession = request.postWithResponseBody(tutorSuggestionUrl(post.getId()) + "/current", null, IrisSession.class, HttpStatus.OK);
        assertThat(irisSession).isEqualTo(currentIrisSession);
    }

    private static String tutorSuggestionUrl(long sessionId) {
        return "/api/iris/tutor-suggestion/" + sessionId + "/sessions";
    }

    private Post createPostInExerciseChat(ProgrammingExercise exercise, String userPrefix) {
        var student = userTestRepository.findOneByLogin(userPrefix + "student1").orElseThrow();
        var tutor = userTestRepository.findOneByLogin(userPrefix + "tutor1").orElseThrow();
        var chat = new Channel();
        chat.setExercise(exercise);
        chat.setName("Test channel");
        chat.setCourse(course);
        chat = channelRepository.save(chat);
        var participant1 = new ConversationParticipant();
        participant1.setConversation(chat);
        participant1.setUser(student);
        conversationParticipantRepository.save(participant1);
        var participant2 = new ConversationParticipant();
        participant2.setConversation(chat);
        participant2.setUser(tutor);
        conversationParticipantRepository.save(participant2);
        chat = channelRepository.findById(chat.getId()).orElseThrow();
        Post post = new Post();
        post.setAuthor(student);
        post.setDisplayPriority(DisplayPriority.NONE);
        post.setConversation(chat);
        post.setContent("Test content");
        post.setCourse(course);
        post = postRepository.save(post);
        return post;
    }

}
