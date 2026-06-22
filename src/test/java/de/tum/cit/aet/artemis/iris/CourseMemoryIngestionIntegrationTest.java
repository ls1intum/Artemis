package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.communication.test_repository.ConversationTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.service.CourseMemoryIngestionService;
import de.tum.cit.aet.artemis.iris.service.IrisBotUserService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisCourseMemorySource;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisWebhookCourseMemoryIngestionExecutionDTO;

class CourseMemoryIngestionIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "coursememingest";

    @Autowired
    private CourseMemoryIngestionService courseMemoryIngestionService;

    @Autowired
    private IrisBotUserService irisBotUserService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private ConversationMessageRepository conversationMessageRepository;

    @Autowired
    private ConversationTestRepository conversationRepository;

    private Course course;

    private Channel channel;

    private User student;

    private User tutor;

    private User botUser;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        course = courseUtilService.createCourseWithUserPrefix(TEST_PREFIX);
        channel = conversationUtilService.createCourseWideChannel(course, "general");
        irisBotUserService.ensureIrisBotUserExists();
        botUser = irisBotUserService.getIrisBotUser();
        student = userTestRepository.findOneWithGroupsAndAuthoritiesByLogin(TEST_PREFIX + "student1").orElseThrow();
        tutor = userTestRepository.findOneWithGroupsAndAuthoritiesByLogin(TEST_PREFIX + "tutor1").orElseThrow();
        enableIrisFor(course);
    }

    private Post createQuestion(String content) {
        Post post = new Post();
        post.setAuthor(student);
        post.setContent(content);
        post.setConversation(channel);
        post.setVisibleForStudents(true);
        return conversationMessageRepository.save(post);
    }

    private AnswerPost saveAnswer(Post post, User author, String content, boolean verified) {
        AnswerPost answer = new AnswerPost();
        answer.setPost(post);
        answer.setAuthor(author);
        answer.setContent(content);
        answer.setVerified(verified);
        if (verified) {
            answer.setVerifiedAt(ZonedDateTime.now());
        }
        return answerPostRepository.save(answer);
    }

    /**
     * Reloads the answer through its parent post so the eagerly-fetched thread (siblings + conversation)
     * matches what the production triggers operate on.
     */
    private AnswerPost reloadManagedAnswer(Post post, Long answerId) {
        Post reloaded = conversationMessageRepository.findMessagePostByIdElseThrow(post.getId());
        return reloaded.getAnswers().stream().filter(answer -> answer.getId().equals(answerId)).findFirst().orElseThrow();
    }

    // --- Service level: Trigger A (verified answer) ---

    @Test
    void ingestVerifiedAnswer_approvedAsIs_firesIrisAuto() {
        Post post = createQuestion("How do I submit the exercise?");
        AnswerPost answer = saveAnswer(post, botUser, "Push to your repo before the deadline.", true);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.ingestVerifiedAnswer(managed, false, tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.IRIS_AUTO);
        assertThat(dto.existingAnswer()).isNull();
        assertThat(dto.courseId()).isEqualTo(course.getId());
        assertThat(dto.conversationId()).isEqualTo(String.valueOf(channel.getId()));
        assertThat(dto.messageId()).isEqualTo(String.valueOf(answer.getId()));
        assertThat(dto.isPublicChannel()).isTrue();
        assertThat(dto.verifiedBy()).isEqualTo(tutor.getLogin());
        assertThat(dto.settings().authenticationToken()).isNotNull();

        // thread is ordered oldest->newest: question first (student), then the verified Iris answer marked as draft.
        // Note: Post and AnswerPost ids come from independent sequences and may collide, so assert by position.
        assertThat(dto.thread()).hasSize(2);
        var question = dto.thread().get(0);
        assertThat(question.authorRole()).isEqualTo("student");
        assertThat(question.isIrisDraft()).isFalse();
        var irisAnswer = dto.thread().get(1);
        assertThat(irisAnswer.id()).isEqualTo(String.valueOf(answer.getId()));
        assertThat(irisAnswer.authorRole()).isEqualTo("iris");
        assertThat(irisAnswer.isIrisDraft()).isTrue();
    }

    @Test
    void ingestVerifiedAnswer_edited_firesIrisCorrectedWithExistingAnswer() {
        Post post = createQuestion("When is the deadline?");
        AnswerPost answer = saveAnswer(post, botUser, "Corrected: only commits before 23:59 are graded.", true);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.ingestVerifiedAnswer(managed, true, tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.IRIS_CORRECTED);
        assertThat(dto.existingAnswer()).isEqualTo("Corrected: only commits before 23:59 are graded.");
        assertThat(dto.messageId()).isEqualTo(String.valueOf(answer.getId()));
    }

    // --- Service level: Trigger B (thread resolved) ---

    @Test
    void ingestResolvedThread_tutorAnswer_firesThreadResolved() {
        Post post = createQuestion("How is the exercise graded?");
        AnswerPost answer = saveAnswer(post, tutor, "The latest push before the deadline is graded.", true);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.ingestResolvedThread(managed, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.THREAD_RESOLVED);
        assertThat(dto.messageId()).isEqualTo(String.valueOf(answer.getId()));
        assertThat(dto.verifiedBy()).isNull();
        assertThat(dto.existingAnswer()).isNull();
        assertThat(dto.thread()).hasSize(2);
        var tutorAnswer = dto.thread().get(1);
        assertThat(tutorAnswer.id()).isEqualTo(String.valueOf(answer.getId()));
        assertThat(tutorAnswer.authorRole()).isEqualTo("tutor");
        assertThat(tutorAnswer.isIrisDraft()).isFalse();
    }

    @Test
    void ingestResolvedThread_irisAnswer_isSkipped() {
        Post post = createQuestion("What is a merge conflict?");
        AnswerPost answer = saveAnswer(post, botUser, "It happens when two branches change the same lines.", true);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        // No webhook mock registered: a request would fail the MockRestServiceServer, asserting nothing is sent.
        courseMemoryIngestionService.ingestResolvedThread(managed, course);
    }

    @Test
    void ingestResolvedThread_privateChannel_isSkipped() {
        Channel privateChannel = conversationUtilService.createPublicChannel(course, "private-ish");
        privateChannel.setIsPublic(false);
        privateChannel = conversationRepository.save(privateChannel);
        Post post = new Post();
        post.setAuthor(student);
        post.setContent("Is this private thread ingested?");
        post.setConversation(privateChannel);
        post.setVisibleForStudents(true);
        Post savedPost = conversationMessageRepository.save(post);
        AnswerPost answer = saveAnswer(savedPost, tutor, "It should not be ingested.", true);
        AnswerPost managed = reloadManagedAnswer(savedPost, answer.getId());

        // Not a public/course-wide channel -> no webhook expected (a stray request would fail the mock server)
        courseMemoryIngestionService.ingestResolvedThread(managed, course);
    }
}
