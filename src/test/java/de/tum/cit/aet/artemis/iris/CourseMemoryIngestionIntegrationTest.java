package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.client.ExpectedCount;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.cleanup.CommunicationDataCleanupRepository;
import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.account.service.user.deletion.UserOwnedContentDeletionService;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.dto.UpdatePostingDTO;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.service.AnswerMessageService;
import de.tum.cit.aet.artemis.communication.service.ConversationMessagingService;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationService;
import de.tum.cit.aet.artemis.communication.test_repository.ConversationTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.dto.SelectedLLMUsageDTO;
import de.tum.cit.aet.artemis.core.dto.vm.ManagedUserVM;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.domain.CourseMemoryOperation;
import de.tum.cit.aet.artemis.iris.domain.CourseMemoryStage;
import de.tum.cit.aet.artemis.iris.dto.IrisCourseMemoryStatusDTO;
import de.tum.cit.aet.artemis.iris.service.CourseMemoryIngestionService;
import de.tum.cit.aet.artemis.iris.service.CourseMemorySyncService;
import de.tum.cit.aet.artemis.iris.service.IrisBotUserService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisStatusUpdateService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisCourseMemoryCourseSyncDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisCourseMemoryIngestionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisCourseMemoryInstanceSyncDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisCourseMemorySource;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisCourseMemorySyncThreadDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisCourseMemoryThreadMessageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisWebhookCourseMemoryDeletionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisWebhookCourseMemoryIngestionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStatusErrorDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.CourseMemoryIngestionWebhookJob;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class CourseMemoryIngestionIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "coursememingest";

    @Autowired
    private CourseMemoryIngestionService courseMemoryIngestionService;

    @Autowired
    private AnswerMessageService answerMessageService;

    @Autowired
    private ConversationMessagingService conversationMessagingService;

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

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ChannelService channelService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private PyrisJobService pyrisJobService;

    @Autowired
    private PyrisStatusUpdateService pyrisStatusUpdateService;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private CourseMemorySyncService courseMemorySyncService;

    @Autowired
    private CommunicationDataCleanupRepository communicationDataCleanupRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private UserAiPreferenceService userAiPreferenceService;

    @Autowired
    private UserOwnedContentDeletionService userOwnedContentDeletionService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private LectureTestRepository lectureTestRepository;

    private Course course;

    private Channel channel;

    private User student;

    private User tutor;

    private User botUser;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        course = courseUtilService.createEnrolledCourseWithMessagingEnabled(TEST_PREFIX);
        channel = conversationUtilService.createCourseWideChannel(course, "general");
        irisBotUserService.ensureIrisBotUserExists();
        botUser = irisBotUserService.getIrisBotUser();
        student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        // The opt-out and AI-selection tests below persist this decision and users are shared across
        // methods in the class, so reset it here to keep the tests order-independent.
        userUtilService.clearAiSelectionDecision(student);
        userUtilService.clearAiSelectionDecision(tutor);
        userUtilService.clearAiSelectionDecision(userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
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
        return saveAnswer(post, author, content, verified, false);
    }

    private AnswerPost saveAnswer(Post post, User author, String content, boolean verified, boolean resolvesPost) {
        AnswerPost answer = new AnswerPost();
        answer.setPost(post);
        answer.setAuthor(author);
        answer.setContent(content);
        answer.setVerified(verified);
        answer.setResolvesPost(resolvesPost);
        if (verified) {
            answer.setVerifiedAt(ZonedDateTime.now());
        }
        return answerPostRepository.save(answer);
    }

    /**
     * An answer marked as resolving its post by {@code endorser}, as {@code AnswerMessageService} records it. The
     * endorsement is what Course Memory derives the trust tier from, so a test asserting a tier has to state it;
     * {@link #saveAnswer(Post, User, String, boolean, boolean)} with {@code resolvesPost} leaves it unrecorded,
     * which is the state of every answer resolved before endorsers existed.
     */
    private AnswerPost saveResolvingAnswer(Post post, User author, String content, boolean verified, User endorser) {
        AnswerPost answer = new AnswerPost();
        answer.setPost(post);
        answer.setAuthor(author);
        answer.setContent(content);
        answer.setVerified(verified);
        answer.setResolution(true, endorser);
        if (verified) {
            answer.setVerifiedAt(ZonedDateTime.now());
        }
        return answerPostRepository.save(answer);
    }

    /**
     * An Iris answer a tutor approved in the verification dashboard, as opposed to one published
     * automatically on a high confidence score. The two are stored identically except for
     * {@code verifiedBy}, which only the dashboard flow records — see
     * {@code AutonomousTutorService#createAndSaveAnswerPost}, which leaves it null because no human
     * reviewed the answer.
     */
    private AnswerPost saveDashboardVerifiedIrisAnswer(Post post, String content, boolean resolvesPost) {
        AnswerPost answer = saveAnswer(post, botUser, content, true, resolvesPost);
        answer.setVerifiedBy(tutor);
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

    /**
     * Gives the thread a Course Memory version, as an earlier dispatched operation would. Edits only refresh threads that
     * have one.
     */
    private void markAsStoredInCourseMemory(Post post) {
        conversationMessageRepository.mintCourseMemoryVersion(post.getId());
    }

    private Post reloadPost(Post post) {
        return conversationMessageRepository.findMessagePostByIdElseThrow(post.getId());
    }

    private static PyrisCourseMemoryThreadMessageDTO messageWithId(List<PyrisCourseMemoryThreadMessageDTO> thread, String id) {
        return thread.stream().filter(message -> message.id().equals(id)).findFirst().orElseThrow();
    }

    // --- Service level: Trigger A (verified answer) ---

    @Test
    void ingestVerifiedAnswer_approvedAsIs_firesIrisAuto() {
        Post post = createQuestion("How do I submit the exercise?");
        AnswerPost answer = saveDashboardVerifiedIrisAnswer(post, "Push to your repo before the deadline.", false);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(managed.getPost().getId(), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.IRIS_AUTO);
        // Approving a draft unchanged still endorses that exact wording, so it travels verbatim rather than
        // being re-derived by the extraction model into a paraphrase the tutor never saw.
        assertThat(dto.existingAnswer()).isEqualTo("Push to your repo before the deadline.");
        assertThat(dto.courseId()).isEqualTo(course.getId());
        assertThat(dto.conversationId()).isEqualTo(String.valueOf(channel.getId()));
        assertThat(dto.postId()).isEqualTo(String.valueOf(post.getId()));
        assertThat(dto.messageId()).isEqualTo(String.valueOf(answer.getId()));
        assertThat(dto.isPublicChannel()).isTrue();
        assertThat(dto.settings().authenticationToken()).isNotNull();

        // thread is ordered oldest->newest: question first (student), then the verified Iris answer marked as draft.
        assertThat(dto.thread()).hasSize(2);
        var question = dto.thread().get(0);
        assertThat(question.id()).isEqualTo("post-" + post.getId());
        assertThat(question.authorRole()).isEqualTo("student");
        assertThat(question.isIrisDraft()).isFalse();
        assertThat(question.isVerifiedAnswer()).isFalse();
        var irisAnswer = dto.thread().get(1);
        assertThat(irisAnswer.id()).isEqualTo("answer-" + answer.getId());
        assertThat(irisAnswer.authorRole()).isEqualTo("iris");
        assertThat(irisAnswer.isIrisDraft()).isTrue();
        // Verification does not set resolvesPost, so the anchor comes solely from isVerifiedAnswer.
        assertThat(irisAnswer.isVerifiedAnswer()).isTrue();
        assertThat(irisAnswer.resolvesPost()).isFalse();
    }

    @Test
    void ingestVerifiedAnswer_edited_firesIrisCorrectedWithExistingAnswer() {
        Post post = createQuestion("When is the deadline?");
        AnswerPost answer = saveDashboardVerifiedIrisAnswer(post, "Corrected: only commits before 23:59 are graded.", false);
        // Approving with an edit records updatedDate in the same statement; that, not an event flag, marks the correction.
        answer.setUpdatedDate(ZonedDateTime.now());
        answerPostRepository.save(answer);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(managed.getPost().getId(), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.IRIS_CORRECTED);
        assertThat(dto.existingAnswer()).isEqualTo("Corrected: only commits before 23:59 are graded.");
        assertThat(dto.messageId()).isEqualTo(String.valueOf(answer.getId()));
    }

    // --- Service level: Trigger B (resolution changed) ---

    @Test
    void resolutionChanged_tutorAnswerMarkedByTutor_firesTutorWritten() {
        Post post = createQuestion("How is the exercise graded?");
        AnswerPost answer = saveResolvingAnswer(post, tutor, "The latest push before the deadline is graded.", true, tutor);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        // A tutor wrote the answer AND a tutor marked it resolving, so it earns the tutor-verified tier.
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.TUTOR_WRITTEN);
        assertThat(dto.postId()).isEqualTo(String.valueOf(post.getId()));
        assertThat(dto.messageId()).isEqualTo(String.valueOf(answer.getId()));
        // The tutor vouched for exactly this text, so it travels verbatim.
        assertThat(dto.existingAnswer()).isEqualTo("The latest push before the deadline is graded.");
        assertThat(dto.thread()).hasSize(2);
        var tutorAnswer = dto.thread().get(1);
        assertThat(tutorAnswer.id()).isEqualTo("answer-" + answer.getId());
        assertThat(tutorAnswer.authorRole()).isEqualTo("tutor");
        assertThat(tutorAnswer.isIrisDraft()).isFalse();
        assertThat(tutorAnswer.isVerifiedAnswer()).isTrue();
        assertThat(tutorAnswer.resolvesPost()).isTrue();
    }

    @Test
    void resolutionChanged_tutorAnswerMarkedByStudent_staysThreadResolved() {
        Post post = createQuestion("Which Java version should I use?");
        AnswerPost answer = saveResolvingAnswer(post, tutor, "Java 25.", true, student);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        // The post author (a student) may mark any answer resolving, but that is not a tutor endorsement.
        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), student, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.THREAD_RESOLVED);
        assertThat(dto.verifiedAt()).isNull();
    }

    @Test
    void resolutionChanged_studentAuthoredAnswer_isIngestedAsCommunityResolved() {
        Post post = createQuestion("Where do I find the slides?");
        AnswerPost answer = saveResolvingAnswer(post, student, "They are on the lecture page.", true, student);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), student, course);

        // A peer answer that resolved the thread is worth remembering, but nobody with authority signed
        // off on it: it is stored, and labelled so retrieval can weight it as a hint rather than fact.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.THREAD_RESOLVED);
        assertThat(dto.verifiedAt()).isNull();
        assertThat(messageWithId(dto.thread(), "answer-" + answer.getId()).isVerifiedAnswer()).isTrue();
    }

    @Test
    void resolutionChanged_studentAnswerMarkedByTutor_isTutorEndorsed() {
        Post post = createQuestion("Is the exam open book?");
        AnswerPost answer = saveResolvingAnswer(post, student, "Yes, one A4 sheet is allowed.", true, tutor);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        // The trust tier follows the endorsement, not the authorship: a tutor marking a student's answer
        // as the resolving one vouches for it just as much as writing it themselves.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.TUTOR_WRITTEN);
        assertThat(dto.verifiedAt()).isNotNull();
    }

    @Test
    void resolutionChanged_studentResolvingAnswerIsFlaggedInTheThread() {
        Post post = createQuestion("How do I set up the project?");
        AnswerPost studentAnswer = saveAnswer(post, student, "Clone it and open it in IntelliJ.", true, true);
        AnswerPost tutorAnswer = saveAnswer(post, tutor, "Clone it and run ./gradlew build.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        // Pyris merges every flagged message into the one stored answer. Both answers resolved the
        // thread, so both belong in it — a resolving answer is not worth less for being a peer's.
        assertThat(messageWithId(dto.thread(), "answer-" + studentAnswer.getId()).resolvesPost()).isTrue();
        assertThat(messageWithId(dto.thread(), "answer-" + tutorAnswer.getId()).resolvesPost()).isTrue();
        assertThat(messageWithId(dto.thread(), "answer-" + studentAnswer.getId()).content()).isEqualTo("Clone it and open it in IntelliJ.");
    }

    @Test
    void ingestion_redactsAQuestionAuthorWhoOptedOutOfAi() {
        userUtilService.setAiSelectionDecision(student, AiSelectionDecision.NO_AI);
        userTestRepository.save(student);
        Post post = createQuestion("Please keep my question away from AI.");
        AnswerPost answer = saveAnswer(post, tutor, "Understood.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        // The question author's words never travel; the thread itself stays usable.
        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        var question = messageWithId(dto.thread(), "post-" + post.getId());
        assertThat(question.redacted()).isTrue();
        assertThat(question.content()).isNullOrEmpty();
        assertThat(messageWithId(dto.thread(), "answer-" + answer.getId()).isVerifiedAnswer()).isTrue();
    }

    @Test
    void resolutionChanged_onlyResolvingAnswerIsOptedOut_retractsInsteadOfIngesting() {
        userUtilService.setAiSelectionDecision(tutor, AiSelectionDecision.NO_AI);
        userTestRepository.save(tutor);
        Post post = createQuestion("Who wrote this answer?");
        AnswerPost answer = saveAnswer(post, tutor, "A tutor who opted out.", true, true);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());
        // Stored before the tutor opted out.
        markAsStoredInCourseMemory(post);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> ingested = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(ingested::set);
        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> retracted = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(retracted::set);

        // The answer itself would become the stored text, so redacting it is not an option and nothing may be
        // ingested. Doing nothing is not an option either: whatever the entry holds was written by an answer
        // that no longer resolves this thread, so it has to go.
        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        assertThat(ingested.get()).isNull();
        assertThat(retracted.get()).isNotNull();
        assertThat(retracted.get().postId()).isEqualTo(String.valueOf(post.getId()));
    }

    @Test
    void ingestion_redactsAParticipantWhoOptedOutMidThread() {
        User bystander = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        userUtilService.setAiSelectionDecision(bystander, AiSelectionDecision.NO_AI);
        bystander = userTestRepository.save(bystander);

        Post post = createQuestion("Why does the build fail?");
        AnswerPost bystanderAnswer = saveAnswer(post, bystander, "I had the same problem in my private repo.", true, false);
        AnswerPost tutorAnswer = saveAnswer(post, tutor, "Run ./gradlew clean first.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        // A participant who is neither the question author nor the resolving author cannot block the
        // thread, but not one word of theirs may leave Artemis. The slot stays so the thread reads in
        // order — the same treatment PyrisPostDTO gives them on the autonomous tutor path.
        var redactedMessage = messageWithId(dto.thread(), "answer-" + bystanderAnswer.getId());
        assertThat(redactedMessage.redacted()).isTrue();
        // Asserted on the deserialized payload, so this is what Pyris actually receives: NON_EMPTY drops
        // the empty content from the JSON altogether, which is why the field is optional on the Pyris DTO.
        assertThat(redactedMessage.content()).isNullOrEmpty();
        assertThat(redactedMessage.resolvesPost()).isFalse();
        assertThat(redactedMessage.isVerifiedAnswer()).isFalse();
        // Everyone else is untouched, and the thread still carries its anchor.
        assertThat(messageWithId(dto.thread(), "answer-" + tutorAnswer.getId()).content()).isEqualTo("Run ./gradlew clean first.");
        assertThat(messageWithId(dto.thread(), "answer-" + tutorAnswer.getId()).isVerifiedAnswer()).isTrue();
        assertThat(messageWithId(dto.thread(), "post-" + post.getId()).content()).isEqualTo("Why does the build fail?");
    }

    // --- An edit to the text an entry was built from must reach Course Memory ---

    @Test
    void editingAResolvingAnswer_reingestsTheThread() {
        Post post = createQuestion("What is the deadline?");
        AnswerPost answer = saveAnswer(post, tutor, "Friday.", true, true);
        markAsStoredInCourseMemory(post);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        // resolvesPost unchanged, so this takes the content-only branch: before, nothing was dispatched
        // and the entry kept serving the wording the tutor had just corrected.
        userUtilService.changeUser(tutor.getLogin());
        answerMessageService.updateAnswerMessage(course.getId(), answer.getId(), new UpdatePostingDTO(answer.getId(), "Friday, 23:59 CET.", null, true));

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.postId()).isEqualTo(String.valueOf(post.getId()));
        assertThat(messageWithId(dto.thread(), "answer-" + answer.getId()).content()).isEqualTo("Friday, 23:59 CET.");
    }

    @Test
    void editingANonContributingAnswer_doesNotReingest() {
        Post post = createQuestion("Any tips for the exercise?");
        saveAnswer(post, tutor, "Read the task description carefully.", true, true);
        AnswerPost chatter = saveAnswer(post, tutor, "Good luck everyone.", true, false);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set, ExpectedCount.max(1));

        // The edited answer neither resolves the thread nor is a verified Iris answer, so no entry was
        // built from it and nothing should be dispatched.
        userUtilService.changeUser(tutor.getLogin());
        answerMessageService.updateAnswerMessage(course.getId(), chatter.getId(), new UpdatePostingDTO(chatter.getId(), "Good luck to everyone.", null, false));

        // Asserted on the captured request rather than left to the mock server: updateAnswerMessage logs
        // and swallows webhook failures, so an unexpected dispatch would never surface as a test failure.
        assertThat(captured.get()).isNull();
    }

    @Test
    void editingTheQuestionOfAResolvedThread_reingestsTheThread() {
        Post post = createQuestion("How do I run it?");
        saveAnswer(post, tutor, "Use ./gradlew bootRun.", true, true);
        post.setResolved(true);
        Post resolvedPost = conversationMessageRepository.save(post);
        markAsStoredInCourseMemory(resolvedPost);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        // The stored question is derived from the root post, so editing it has to re-extract the entry.
        userUtilService.changeUser(student.getLogin());
        conversationMessagingService.updateMessage(course.getId(), resolvedPost.getId(),
                new UpdatePostingDTO(resolvedPost.getId(), "How do I run the server locally?", null, false));

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(messageWithId(dto.thread(), "post-" + resolvedPost.getId()).content()).isEqualTo("How do I run the server locally?");
    }

    @Test
    void editingTheQuestionOfAnUnresolvedThread_doesNotReingest() {
        Post post = createQuestion("Still unanswered?");
        saveAnswer(post, tutor, "Looking into it.", true, false);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set, ExpectedCount.max(1));

        // Nothing resolves the thread, so it has no entry to refresh — no webhook expected.
        userUtilService.changeUser(student.getLogin());
        conversationMessagingService.updateMessage(course.getId(), post.getId(), new UpdatePostingDTO(post.getId(), "Still unanswered, sorry for the bump.", null, false));

        // updateMessage swallows webhook failures too, so the absence of a dispatch has to be asserted.
        assertThat(captured.get()).isNull();
    }

    // --- A channel that stops being an eligible source must take its entries with it ---

    @Test
    void threadDeletion_stillTargetsASingleThread() {
        Post post = createQuestion("Does a thread deletion stay narrow?");
        saveAnswer(post, tutor, "It should.", true, false);

        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.retractDeletedThread(post.getId(), course.getId(), tutor);

        // Pyris rejects a deletion carrying both scopes, so the thread path must leave conversationId unset.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.postId()).isEqualTo(String.valueOf(post.getId()));
    }

    @Test
    void ingestion_downgradesToLocalWhenAnyParticipantChoseLocal() {
        User secondStudent = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        userUtilService.setAiSelectionDecision(secondStudent, AiSelectionDecision.LOCAL_AI);
        secondStudent = userTestRepository.save(secondStudent);

        Post post = createQuestion("Does the extractor run on-premise?");
        saveAnswer(post, secondStudent, "I would like it to.", true, false);
        AnswerPost tutorAnswer = saveAnswer(post, tutor, "It depends on the thread.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        // Ingestion sends the whole transcript to an extraction model, so it answers "which model may
        // see this thread" exactly as the autonomous tutor run does: one local participant pins it.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.settings().selection()).isEqualTo(AiSelectionDecision.LOCAL_AI);
    }

    @Test
    void ingestion_staysCloudWhenNoParticipantChoseLocal() {
        Post post = createQuestion("Any preference here?");
        AnswerPost tutorAnswer = saveAnswer(post, tutor, "None recorded.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.settings().selection()).isEqualTo(AiSelectionDecision.CLOUD_AI);
    }

    @Test
    void ingestion_clearsResolvingFlagOfARedactedAnswer() {
        User bystander = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        userUtilService.setAiSelectionDecision(bystander, AiSelectionDecision.NO_AI);
        bystander = userTestRepository.save(bystander);

        Post post = createQuestion("Which Java version do we use?");
        AnswerPost redactedResolver = saveAnswer(post, bystander, "Java 25, see the README.", true, true);
        AnswerPost tutorAnswer = saveAnswer(post, tutor, "Java 25.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        // Pyris merges every flagged message into the single stored answer. Leaving the flag on a
        // redacted message would splice the placeholder into the answer served to future students.
        assertThat(messageWithId(dto.thread(), "answer-" + redactedResolver.getId()).resolvesPost()).isFalse();
        assertThat(messageWithId(dto.thread(), "answer-" + tutorAnswer.getId()).resolvesPost()).isTrue();
    }

    @Test
    void retraction_stillWorksForAnOptedOutQuestionAuthor() {
        userUtilService.setAiSelectionDecision(student, AiSelectionDecision.NO_AI);
        userTestRepository.save(student);
        Post post = createQuestion("Opted out after an entry already existed.");
        AnswerPost answer = saveAnswer(post, tutor, "No longer resolving.", true, false);
        markAsStoredInCourseMemory(post);

        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        // Deletion must stay reachable: an entry written before the author opted out has to be removable.
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().postId()).isEqualTo(String.valueOf(post.getId()));
    }

    @Test
    void resolutionChanged_severalResolvingAnswers_flagsAllAndKeepsOnePostId() {
        Post post = createQuestion("How do I run the tests locally?");
        AnswerPost first = saveAnswer(post, tutor, "Use ./gradlew test.", true, true);
        AnswerPost second = saveAnswer(post, tutor, "You also need Docker running.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        // Both resolving answers are flagged, so the extractor merges them instead of being told to
        // ignore all but one; the entry is keyed on the thread so there is still only one of them.
        assertThat(dto.postId()).isEqualTo(String.valueOf(post.getId()));
        assertThat(messageWithId(dto.thread(), "answer-" + first.getId()).resolvesPost()).isTrue();
        assertThat(messageWithId(dto.thread(), "answer-" + second.getId()).resolvesPost()).isTrue();
        // Exactly one anchor: the answer whose flag just changed.
        assertThat(dto.thread().stream().filter(PyrisCourseMemoryThreadMessageDTO::isVerifiedAnswer).toList()).singleElement()
                .satisfies(message -> assertThat(message.id()).isEqualTo("answer-" + second.getId()));
    }

    @Test
    void resolutionChanged_lastResolvingAnswerUnmarked_deletesThreadEntry() {
        Post post = createQuestion("Is attendance mandatory?");
        // Already un-marked in the database, mirroring the state after the flag was toggled back off.
        AnswerPost answer = saveAnswer(post, tutor, "No, it is optional.", true, false);
        markAsStoredInCourseMemory(post);

        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.postId()).isEqualTo(String.valueOf(post.getId()));
        assertThat(dto.courseId()).isEqualTo(course.getId());
        assertThat(dto.settings().authenticationToken()).isNotNull();
        // The retraction is ordered against the thread's ingestions by the version minted for it.
        assertThat(dto.version()).isEqualTo(conversationMessageRepository.findCourseMemoryVersion(post.getId()).orElseThrow());
    }

    @Test
    void resolutionChanged_unmarkedOnAThreadThatWasNeverStored_doesNothing() {
        // Nothing was ever sent for this thread, so there is nothing to retract and no removal to report to the tutor.
        Post post = createQuestion("Was this ever stored?");
        saveAnswer(post, tutor, "Not yet.", true, false);

        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(captured::set, ExpectedCount.max(1));

        courseMemoryIngestionService.refreshThread(post.getId(), tutor, course);

        assertThat(captured.get()).isNull();
        assertThat(conversationMessageRepository.findCourseMemoryVersion(post.getId()).orElseThrow()).isZero();
    }

    @Test
    void resolutionChanged_oneOfTwoResolversUnmarked_reingestsRatherThanDeletes() {
        Post post = createQuestion("Do I need to register for the exam?");
        AnswerPost stillResolving = saveAnswer(post, tutor, "Yes, via TUMonline.", true, true);
        AnswerPost unmarked = saveAnswer(post, tutor, "Ignore my earlier note.", true, false);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        // The thread still holds a resolving answer, so the entry is refreshed from it, not deleted.
        assertThat(dto.thread().stream().filter(PyrisCourseMemoryThreadMessageDTO::isVerifiedAnswer).toList()).singleElement()
                .satisfies(message -> assertThat(message.id()).isEqualTo("answer-" + stillResolving.getId()));
    }

    @Test
    void resolutionChanged_verifiedIrisAnswerSurvivesUnmarking_isNotDeleted() {
        Post post = createQuestion("What does CI stand for?");
        AnswerPost irisAnswer = saveDashboardVerifiedIrisAnswer(post, "Continuous Integration.", false);
        AnswerPost humanAnswer = saveAnswer(post, student, "Also see the glossary.", true, false);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        // A tutor-verified Iris answer keeps the thread memory-worthy, so the entry must not be retracted
        // just because an unrelated answer was un-marked.
        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), student, course);

        // Rebuilt from the Iris answer rather than left as it stood: the entry is keyed on the thread, so
        // it may hold what a since-retracted staff answer wrote.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.IRIS_AUTO);
        assertThat(dto.messageId()).isEqualTo(String.valueOf(irisAnswer.getId()));
    }

    @Test
    void resolutionChanged_anAutoPublishedIrisAnswerDoesNotHideAnOlderVerifiedOne() {
        Post post = createQuestion("What does CD stand for?");
        AnswerPost verifiedIrisAnswer = saveDashboardVerifiedIrisAnswer(post, "Continuous Delivery.", false);
        // Auto-published: isVerified() like the one above, but with no human verifier behind it — and newer.
        // Testing only the newest bot answer for a verifier would make the anchor come out empty and the
        // caller retract an entry the tutor-approved answer above still owns.
        saveAnswer(post, botUser, "It can also mean Continuous Deployment.", true, false);
        AnswerPost humanAnswer = saveAnswer(post, student, "See the glossary.", false, false);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), student, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.messageId()).isEqualTo(String.valueOf(verifiedIrisAnswer.getId()));
    }

    @Test
    void resolutionChanged_anOptedOutResolverDoesNotDisplaceAnOlderUsableOne() {
        // selectAnchor takes the newest resolving answer, so an opted-out author answering after a tutor used to
        // win the anchor — and the run then stopped, leaving the tutor's entry standing for an answer that no
        // longer owned it. The opted-out answer is not a candidate at all, so the tutor's still is.
        User bystander = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        userUtilService.setAiSelectionDecision(bystander, AiSelectionDecision.NO_AI);
        bystander = userTestRepository.save(bystander);

        Post post = createQuestion("Which branch do I base my work on?");
        AnswerPost tutorAnswer = saveAnswer(post, tutor, "Always branch off develop.", true, true);
        saveAnswer(post, bystander, "That matches what I did.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.messageId()).isEqualTo(String.valueOf(tutorAnswer.getId()));
    }

    @Test
    void resolutionChanged_dashboardVerifiedIrisAnswer_isReingestedUnderVerificationProvenance() {
        Post post = createQuestion("What is a merge conflict?");
        AnswerPost answer = saveDashboardVerifiedIrisAnswer(post, "It happens when two branches change the same lines.", true);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        // Trigger A owns this answer, so the run keeps its provenance and its verifier instead of being
        // relabelled after whoever triggered this pass — but it still has to be dispatched, or a stale
        // entry written by another answer would survive untouched.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.IRIS_AUTO);
        // Uncorrected, but still a tutor's sign-off on this exact text, so it travels verbatim.
        assertThat(dto.existingAnswer()).isEqualTo("It happens when two branches change the same lines.");
    }

    @Test
    void resolutionChanged_editedVerifiedIrisAnswer_isReingestedAsCorrectedWithTheEditVerbatim() {
        Post post = createQuestion("When is the exam?");
        AnswerPost answer = saveDashboardVerifiedIrisAnswer(post, "Some time in March.", true);
        // The tutor corrected the draft, which is what an update after creation means for a bot answer.
        answer.setContent("On 14 March, 10:00, in MW 2001.");
        answer.setUpdatedDate(ZonedDateTime.now());
        answerPostRepository.save(answer);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        // The corrected text travels as existingAnswer so extraction cannot paraphrase the tutor's wording
        // away, exactly as Trigger A passes it.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.IRIS_CORRECTED);
        assertThat(dto.existingAnswer()).isEqualTo("On 14 March, 10:00, in MW 2001.");
    }

    @Test
    void resolutionChanged_autoPostedIrisAnswerMarkedByTutor_isIngestedAsIrisAuto() {
        Post post = createQuestion("What is a merge conflict?");
        // Published automatically on a high confidence score: verified, but with no human reviewer, so it
        // never passed through the dashboard and Trigger A never fired for it.
        AnswerPost answer = saveResolvingAnswer(post, botUser, "It happens when two branches change the same lines.", true, tutor);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        // The tutor marking it resolving is the first and only sign-off the answer ever gets; without
        // this path an auto-posted answer could never reach course memory at all.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.IRIS_AUTO);
        assertThat(messageWithId(dto.thread(), "answer-" + answer.getId()).isIrisDraft()).isTrue();
        // The tutor signed off on exactly the text they read, so it travels verbatim like a dashboard approval;
        // Pyris rejects IRIS_AUTO without it rather than store an extractor's paraphrase as tutor-approved.
        assertThat(dto.existingAnswer()).isEqualTo("It happens when two branches change the same lines.");
    }

    @Test
    void resolutionChanged_autoPostedIrisAnswerMarkedByStudent_isCommunityResolved() {
        Post post = createQuestion("What does a rebase do?");
        AnswerPost answer = saveResolvingAnswer(post, botUser, "It replays your commits on top of another branch.", true, student);
        AnswerPost managed = reloadManagedAnswer(post, answer.getId());

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), student, course);

        // A student accepting an AI answer is not a tutor endorsement, so it must not be labelled as one.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.THREAD_RESOLVED);
    }

    @Test
    void resolutionChanged_unverifiedIrisDraftIsExcludedFromThread() {
        Post post = createQuestion("How do I reset my password?");
        saveAnswer(post, botUser, "Unapproved draft that students cannot see.", false, false);
        AnswerPost tutorAnswer = saveAnswer(post, tutor, "Use the forgot-password link.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.thread()).hasSize(2);
        assertThat(dto.thread()).noneMatch(PyrisCourseMemoryThreadMessageDTO::isIrisDraft);
    }

    @Test
    void threadDeleted_deletesThreadEntry() {
        Post post = createQuestion("Will this thread be removed?");
        saveAnswer(post, tutor, "Yes it will.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.retractDeletedThread(post.getId(), course.getId(), tutor);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().postId()).isEqualTo(String.valueOf(post.getId()));
    }

    // --- Websocket status reported to the acting user ---

    private String courseMemoryTopic() {
        return "course-memory/" + course.getId();
    }

    private ArgumentMatcher<Object> status(CourseMemoryOperation operation, CourseMemoryStage stage, Post post) {
        return payload -> payload instanceof IrisCourseMemoryStatusDTO dto && dto.operation() == operation && dto.stage() == stage
                && dto.postId().equals(String.valueOf(post.getId()));
    }

    @Test
    void resolutionChanged_pushesTriggeredToTheMarker() {
        Post post = createQuestion("Does resolving notify me?");
        AnswerPost answer = saveAnswer(post, tutor, "It should.", true, true);

        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(dto -> {
        });

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        verifyMessageWasSentOverWebsocket(tutor.getLogin(), courseMemoryTopic(), status(CourseMemoryOperation.INGEST, CourseMemoryStage.TRIGGERED, post));
    }

    @Test
    void retraction_pushesDeleteTriggeredToTheMarker() {
        Post post = createQuestion("Does un-resolving notify me?");
        AnswerPost answer = saveAnswer(post, tutor, "No longer resolving.", true, false);
        markAsStoredInCourseMemory(post);

        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(dto -> {
        });

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        verifyMessageWasSentOverWebsocket(tutor.getLogin(), courseMemoryTopic(), status(CourseMemoryOperation.DELETE, CourseMemoryStage.TRIGGERED, post));
    }

    @Test
    void skippedIngestion_pushesNothing() {
        // The whole reason TRIGGERED is server-pushed: a client-side toast would announce an ingestion
        // in a case like this one, which dispatches nothing at all.
        Channel privateChannel = conversationUtilService.createPublicChannel(course, "private-for-status");
        privateChannel.setIsPublic(false);
        privateChannel = conversationRepository.save(privateChannel);
        Post privatePost = new Post();
        privatePost.setAuthor(student);
        privatePost.setContent("Private thread");
        privatePost.setConversation(privateChannel);
        privatePost.setVisibleForStudents(true);
        Post savedPrivatePost = conversationMessageRepository.save(privatePost);
        AnswerPost privateAnswer = saveAnswer(savedPrivatePost, tutor, "Not ingested.", true, true);
        courseMemoryIngestionService.refreshThread(reloadPost(savedPrivatePost).getId(), tutor, course);

        verifyNumberOfCallsToWebsocket(tutor.getLogin(), courseMemoryTopic(), 0);
    }

    @Test
    void statusUpdate_finishedPushesCompleted_runningPushesNothing() {
        Post post = createQuestion("Does completion notify me?");
        String jobToken = pyrisJobService.addCourseMemoryIngestionWebhookJob(course.getId(), String.valueOf(channel.getId()), String.valueOf(post.getId()), "answer-1",
                tutor.getLogin(), CourseMemoryOperation.INGEST);
        var job = (CourseMemoryIngestionWebhookJob) pyrisJobService.getJob(jobToken);

        // Pyris emits several RUNNING updates per run; each would otherwise raise its own toast.
        pyrisStatusUpdateService.handleStatusUpdate(job, new PyrisCourseMemoryIngestionStatusUpdateDTO(null, PyrisRunState.RUNNING, null, null));
        verifyNumberOfCallsToWebsocket(tutor.getLogin(), courseMemoryTopic(), 0);

        pyrisStatusUpdateService.handleStatusUpdate(job, new PyrisCourseMemoryIngestionStatusUpdateDTO(null, PyrisRunState.FINISHED, null, null));
        verifyMessageWasSentOverWebsocket(tutor.getLogin(), courseMemoryTopic(), status(CourseMemoryOperation.INGEST, CourseMemoryStage.COMPLETED, post));
    }

    @Test
    void dispatchFailure_pushesFailedAndReleasesTheJob() {
        Post post = createQuestion("What if Pyris cannot be reached?");
        AnswerPost answer = saveAnswer(post, tutor, "An answer nobody will store.", true, true);
        AtomicReference<String> jobToken = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunError(dto -> jobToken.set(dto.settings().authenticationToken()), HttpStatus.INTERNAL_SERVER_ERROR.value());

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        // TRIGGERED was already pushed, and Pyris never took the request, so nothing else will ever close this run
        // out: Artemis has to report the failure itself.
        verifyMessageWasSentOverWebsocket(tutor.getLogin(), courseMemoryTopic(),
                payload -> payload instanceof IrisCourseMemoryStatusDTO dto && dto.stage() == CourseMemoryStage.FAILED);
        // And drop the job rather than leaving a token nothing can redeem to sit out the ingestion TTL.
        assertThat(jobToken.get()).isNotNull();
        assertThat(pyrisJobService.getJob(jobToken.get())).isNull();
    }

    @Test
    void statusUpdate_failedPushesFailedWithMessage() {
        Post post = createQuestion("Does failure notify me?");
        String jobToken = pyrisJobService.addCourseMemoryIngestionWebhookJob(course.getId(), String.valueOf(channel.getId()), String.valueOf(post.getId()), "answer-1",
                tutor.getLogin(), CourseMemoryOperation.DELETE);
        var job = (CourseMemoryIngestionWebhookJob) pyrisJobService.getJob(jobToken);

        pyrisStatusUpdateService.handleStatusUpdate(job, new PyrisCourseMemoryIngestionStatusUpdateDTO(null, PyrisRunState.FAILED, new PyrisStatusErrorDTO("boom", null), null));

        verifyMessageWasSentOverWebsocket(tutor.getLogin(), courseMemoryTopic(), payload -> payload instanceof IrisCourseMemoryStatusDTO dto
                && dto.operation() == CourseMemoryOperation.DELETE && dto.stage() == CourseMemoryStage.FAILED && "boom".equals(dto.errorMessage()));
    }

    @Test
    void statusUpdate_withoutActorPushesNothing() {
        Post post = createQuestion("Anonymous trigger");
        String jobToken = pyrisJobService.addCourseMemoryIngestionWebhookJob(course.getId(), String.valueOf(channel.getId()), String.valueOf(post.getId()), "answer-1", null,
                CourseMemoryOperation.INGEST);
        var job = (CourseMemoryIngestionWebhookJob) pyrisJobService.getJob(jobToken);

        // A run without a known actor must still complete; it just reports to nobody.
        pyrisStatusUpdateService.handleStatusUpdate(job, new PyrisCourseMemoryIngestionStatusUpdateDTO(null, PyrisRunState.FINISHED, null, null));

        verifyNumberOfCallsToWebsocket(tutor.getLogin(), courseMemoryTopic(), 0);
    }

    @Test
    void resolutionChanged_privateChannel_isSkipped() {
        Channel privateChannel = conversationUtilService.createPublicChannel(course, "private-ish");
        privateChannel.setIsPublic(false);
        privateChannel = conversationRepository.save(privateChannel);
        Post post = new Post();
        post.setAuthor(student);
        post.setContent("Is this private thread ingested?");
        post.setConversation(privateChannel);
        post.setVisibleForStudents(true);
        Post savedPost = conversationMessageRepository.save(post);
        AnswerPost answer = saveAnswer(savedPost, tutor, "It should not be ingested.", true, true);
        AnswerPost managed = reloadManagedAnswer(savedPost, answer.getId());

        // Not a public/course-wide channel -> no webhook expected (a stray request would fail the mock server)
        courseMemoryIngestionService.refreshThread(reloadPost(savedPost).getId(), tutor, course);
    }

    // --- The trust tier follows the endorser recorded on the anchoring answer, not the acting user ---

    @Test
    void resolutionChanged_tutorUnmarksWhileAStudentEndorsedAnswerStands_staysCommunityResolved() {
        Post post = createQuestion("Is the retake exam open book?");
        // Endorsed by the post author, a student: nobody with authority has checked it.
        AnswerPost studentEndorsed = saveResolvingAnswer(post, student, "I think so, a friend told me.", true, student);
        // The tutor's own answer, already un-marked in the database, mirroring the state after the toggle.
        AnswerPost unmarked = saveAnswer(post, tutor, "Retracting this, I was wrong.", true, false);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        // The tutor is the actor of this refresh, not the endorser of the answer that now anchors the entry.
        // Labelling it after the actor would store a student's guess about the exam as tutor-verified.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.messageId()).isEqualTo(String.valueOf(studentEndorsed.getId()));
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.THREAD_RESOLVED);
        assertThat(dto.verifiedAt()).isNull();
    }

    @Test
    void resolutionChanged_studentUnmarksWhileATutorEndorsedAnswerStands_keepsTheEndorsersProvenance() {
        Post post = createQuestion("Which deadline counts, the calendar or the exercise page?");
        AnswerPost tutorEndorsed = saveResolvingAnswer(post, student, "The exercise page.", true, tutor);
        AnswerPost unmarked = saveAnswer(post, student, "Never mind, found it.", true, false);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), student, course);

        // The student is merely the actor; the surviving answer carries a tutor's endorsement and keeps it, with
        // the verifier and timestamp of that endorsement rather than of this refresh.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.messageId()).isEqualTo(String.valueOf(tutorEndorsed.getId()));
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.TUTOR_WRITTEN);
        ZonedDateTime endorsedAt = answerPostRepository.findById(tutorEndorsed.getId()).orElseThrow().getResolvedAt();
        assertThat(dto.verifiedAt()).isEqualTo(endorsedAt.toInstant().toString());
    }

    @Test
    void editingTheQuestion_doesNotUpgradeAStudentResolvedThread() {
        Post post = createQuestion("Whre are the slides?");
        saveResolvingAnswer(post, student, "On the lecture page.", true, student);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        // What the question-edit path calls: no triggering answer, and the editor — here a tutor fixing a typo —
        // as the actor. Fixing a typo is not an endorsement of the answer below it.
        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.THREAD_RESOLVED);
    }

    @Test
    void resolutionChanged_studentMarksASecondAnswer_keepsTheTutorEndorsedAnchor() {
        Post post = createQuestion("Do we need to register for the exam separately?");
        AnswerPost tutorEndorsed = saveResolvingAnswer(post, tutor, "Yes, via TUMonline until the 15th.", true, tutor);
        AnswerPost studentEndorsed = saveResolvingAnswer(post, student, "I did not have to.", true, student);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), student, course);

        // Pyris applies the latest state Artemis sends, so the anchor has to be chosen by trust tier first: the
        // newer student-endorsed answer must not demote the thread's tutor-verified entry, even though it is the
        // one whose flag just changed. It still travels flagged, so the extractor sees it.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.messageId()).isEqualTo(String.valueOf(tutorEndorsed.getId()));
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.TUTOR_WRITTEN);
        assertThat(messageWithId(dto.thread(), "answer-" + tutorEndorsed.getId()).isVerifiedAnswer()).isTrue();
        assertThat(messageWithId(dto.thread(), "answer-" + studentEndorsed.getId()).resolvesPost()).isTrue();
    }

    @Test
    void resolutionChanged_resolvingAnswerWithoutARecordedEndorser_isCommunityResolved() {
        Post post = createQuestion("Resolved before endorsers were recorded?");
        // resolvesPost without resolvedBy: the state of every answer resolved before the endorsement columns existed.
        AnswerPost legacy = saveAnswer(post, tutor, "Yes.", true, true);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        // Nobody is on record as having endorsed it, so it fails closed into the community tier — even though a
        // tutor wrote it and a tutor triggered this refresh.
        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.THREAD_RESOLVED);
    }

    @Test
    void markingAnAnswerResolving_recordsTheEndorserAndUnmarkingClearsIt() {
        Post post = createQuestion("Who endorsed this?");
        AnswerPost answer = saveAnswer(post, student, "Me, apparently.", true, false);
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(dto -> {
        });
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(dto -> {
        });

        userUtilService.changeUser(tutor.getLogin());
        answerMessageService.updateAnswerMessage(course.getId(), answer.getId(), new UpdatePostingDTO(answer.getId(), "Me, apparently.", null, true));

        assertThat(answerPostRepository.findResolvingAnswerEndorsersByPostId(post.getId())).singleElement()
                .satisfies(endorser -> assertThat(endorser.endorserLogin()).isEqualTo(tutor.getLogin()));
        assertThat(answerPostRepository.findById(answer.getId()).orElseThrow().getResolvedAt()).isNotNull();

        answerMessageService.updateAnswerMessage(course.getId(), answer.getId(), new UpdatePostingDTO(answer.getId(), "Me, apparently.", null, false));

        // Un-marking clears the endorsement: a later re-mark is a fresh endorsement by whoever makes it.
        assertThat(answerPostRepository.findResolvingAnswerEndorsersByPostId(post.getId())).isEmpty();
        assertThat(answerPostRepository.findById(answer.getId()).orElseThrow().getResolvedAt()).isNull();
    }

    // --- Every thread-scoped operation carries a version Pyris orders it by ---

    @Test
    void successiveOperationsOnAThread_carryStrictlyIncreasingVersions() {
        Post post = createQuestion("Is attendance mandatory?");
        AnswerPost answer = saveResolvingAnswer(post, tutor, "No.", true, tutor);

        // Both expectations up front: the mock server refuses new ones once a request has been made.
        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> ingested = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(ingested::set);
        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> retracted = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(retracted::set);

        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        // The answer is un-marked, so the next event retracts the entry.
        answer.setResolution(false, null);
        answerPostRepository.save(answer);
        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        // Pyris keeps the highest version per thread and drops anything older, so the retraction has to outrank
        // the ingestion it supersedes — and the counter it was minted from has to say the same.
        assertThat(ingested.get().version()).isPositive();
        assertThat(retracted.get().version()).isEqualTo(ingested.get().version() + 1);
        assertThat(conversationMessageRepository.findCourseMemoryVersion(post.getId())).contains(retracted.get().version());
    }

    @Test
    void verificationAndResolution_shareOneVersionCounterPerThread() {
        Post post = createQuestion("Does Trigger A share the counter?");
        AnswerPost irisAnswer = saveDashboardVerifiedIrisAnswer(post, "It has to.", false);
        AnswerPost humanAnswer = saveResolvingAnswer(post, tutor, "Confirmed.", true, tutor);

        List<Long> versions = new ArrayList<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(dto -> versions.add(dto.version()), ExpectedCount.times(2));
        courseMemoryIngestionService.refreshThread(post.getId(), tutor, course);
        courseMemoryIngestionService.refreshThread(reloadPost(post).getId(), tutor, course);

        // Both triggers write the same thread entry, so they must be ordered against each other, not each on its own.
        assertThat(versions).hasSize(2);
        assertThat(versions.get(1)).isEqualTo(versions.get(0) + 1);
    }

    @Test
    void threadDeletion_carriesTheFinalVersion() {
        Post post = createQuestion("Will anything follow my deletion?");
        saveResolvingAnswer(post, tutor, "Nothing can.", true, tutor);

        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.retractDeletedThread(post.getId(), course.getId(), tutor);

        // The row is gone by the time this fires in production, so no version can be minted for it; the maximum
        // value is a tombstone no ingestion still in flight can ever outrank.
        assertThat(captured.get().version()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void deletingATrackedThread_retractsItWithTheFinalVersion() {
        Post post = createQuestion("Delete me once I am stored.");
        markAsStoredInCourseMemory(post);

        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(captured::set);

        userUtilService.changeUser(student.getLogin());
        conversationMessagingService.deleteMessageById(course.getId(), post.getId());

        assertThat(conversationMessageRepository.findById(post.getId())).isEmpty();
        assertThat(captured.get().postId()).isEqualTo(String.valueOf(post.getId()));
        assertThat(captured.get().version()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void deletingAThreadThatWasNeverStored_doesNotCallPyris() {
        Post post = createQuestion("Never stored, deleted quietly.");

        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(captured::set, ExpectedCount.max(1));

        userUtilService.changeUser(student.getLogin());
        conversationMessagingService.deleteMessageById(course.getId(), post.getId());

        assertThat(conversationMessageRepository.findById(post.getId())).isEmpty();
        assertThat(captured.get()).isNull();
    }

    // --- Readable channels, privacy and the nightly sync ---

    @Test
    void readableChannels_areExactlyThoseEveryStudentCanRead() {
        Channel publicChannel = conversationUtilService.createPublicChannel(course, "public-readable");
        Channel privateChannel = conversationUtilService.createPublicChannel(course, "private-unreadable");
        privateChannel.setIsPublic(false);
        privateChannel = conversationRepository.save(privateChannel);
        TextExercise released = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(2));
        TextExercise unreleased = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2),
                ZonedDateTime.now().plusDays(3));
        Channel releasedChannel = conversationUtilService.addChannelToExercise(released);
        Channel unreleasedChannel = conversationUtilService.addChannelToExercise(unreleased);
        Lecture lecture = lectureUtilService.createLecture(course);
        Channel lectureChannel = lectureUtilService.addLectureChannel(lecture);
        Lecture tutorialLecture = lectureUtilService.createLecture(course);
        tutorialLecture.setIsTutorialLecture(true);
        tutorialLecture = lectureTestRepository.save(tutorialLecture);
        // Course-wide, but hidden from every channel list.
        Channel tutorialLectureChannel = lectureUtilService.addLectureChannel(tutorialLecture);

        var readable = channelRepository.findIdsOfChannelsReadableByAllStudents(course.getId(), ZonedDateTime.now());

        assertThat(readable).contains(channel.getId(), publicChannel.getId(), releasedChannel.getId(), lectureChannel.getId()).doesNotContain(privateChannel.getId(),
                unreleasedChannel.getId(), tutorialLectureChannel.getId());
        assertThat(channelRepository.isChannelReadableByAllStudents(unreleasedChannel.getId(), ZonedDateTime.now())).isFalse();
        assertThat(channelRepository.isChannelReadableByAllStudents(releasedChannel.getId(), ZonedDateTime.now())).isTrue();
    }

    @Test
    void refresh_ofAStoredThreadInAChannelThatTurnedPrivate_retractsIt() {
        Channel narrowed = conversationUtilService.createPublicChannel(course, "turns-private");
        Post post = new Post();
        post.setAuthor(student);
        post.setContent("Question in a channel that will be narrowed");
        post.setConversation(narrowed);
        post.setVisibleForStudents(true);
        post = conversationMessageRepository.save(post);
        saveResolvingAnswer(post, tutor, "Answer.", true, tutor);
        markAsStoredInCourseMemory(post);
        narrowed.setIsPublic(false);
        conversationRepository.save(narrowed);

        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(post.getId(), tutor, course);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().postId()).isEqualTo(String.valueOf(post.getId()));
        assertThat(captured.get().version()).isEqualTo(conversationMessageRepository.findCourseMemoryVersion(post.getId()).orElseThrow());
    }

    @Test
    void refresh_retractsWithoutAGateOnTheIrisSetting() {
        Post post = createQuestion("Stored while Iris was on");
        saveResolvingAnswer(post, tutor, "Answer.", true, tutor);
        markAsStoredInCourseMemory(post);
        disableIrisFor(course);

        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(post.getId(), tutor, course);

        assertThat(captured.get()).isNotNull();
    }

    @Test
    void studentEditOfATutorEndorsedAnswer_downgradesTheEntryToCommunityResolved() {
        Post post = createQuestion("When is the deadline?");
        AnswerPost studentAnswer = saveResolvingAnswer(post, student, "Friday.", false, tutor);
        markAsStoredInCourseMemory(post);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        // The tutor vouched for "Friday.", not for whatever the student writes next.
        userUtilService.changeUser(student.getLogin());
        answerMessageService.updateAnswerMessage(course.getId(), studentAnswer.getId(),
                new UpdatePostingDTO(studentAnswer.getId(), "Friday, and the exam is cancelled.", null, true));

        var dto = captured.get();
        assertThat(dto).isNotNull();
        assertThat(dto.source()).isEqualTo(PyrisCourseMemorySource.THREAD_RESOLVED);
        assertThat(dto.existingAnswer()).isNull();
        assertThat(answerPostRepository.findById(studentAnswer.getId()).orElseThrow().getResolvedAt()).isNull();
    }

    @Test
    void mentionsReachPyrisWithoutTheLogin() {
        Post post = createQuestion("Who grades the exercise?");
        saveResolvingAnswer(post, tutor, "Ask [user]Tutor One(" + tutor.getLogin() + ")[/user] about it.", true, tutor);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(post.getId(), tutor, course);

        var dto = captured.get();
        assertThat(dto.existingAnswer()).isEqualTo("Ask Tutor One about it.");
        assertThat(dto.thread()).allSatisfy(message -> assertThat(message.content()).doesNotContain(tutor.getLogin()));
    }

    @Test
    void anchor_isTheSameWhoeverTriggeredTheRefresh() {
        Post post = createQuestion("How is it graded?");
        saveResolvingAnswer(post, tutor, "Older tutor-endorsed answer.", true, tutor);
        AnswerPost newer = saveResolvingAnswer(post, tutor, "Newer tutor-endorsed answer.", true, tutor);

        List<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new ArrayList<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::add, ExpectedCount.times(2));

        courseMemoryIngestionService.refreshThread(post.getId(), student, course);
        courseMemoryIngestionService.refreshThread(post.getId(), tutor, course);

        assertThat(captured).extracting(PyrisWebhookCourseMemoryIngestionExecutionDTO::messageId).containsOnly(String.valueOf(newer.getId()));
        assertThat(captured.get(1).version()).isGreaterThan(captured.get(0).version());
    }

    @Test
    void deactivatedAuthor_isRedacted() {
        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        Post post = createQuestion("Question?");
        AnswerPost reply = saveAnswer(post, student2, "A reply by an account that will be deactivated.", false);
        saveResolvingAnswer(post, tutor, "Answer.", true, tutor);
        student2.setActivated(false);
        userTestRepository.save(student2);

        AtomicReference<PyrisWebhookCourseMemoryIngestionExecutionDTO> captured = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryIngestionWebhookRunResponse(captured::set);

        courseMemoryIngestionService.refreshThread(post.getId(), tutor, course);

        var redacted = messageWithId(captured.get().thread(), "answer-" + reply.getId());
        assertThat(redacted.redacted()).isTrue();
        assertThat(redacted.content()).isNullOrEmpty();
        student2.setActivated(true);
        userTestRepository.save(student2);
    }

    @Test
    void invalidatingTheThreadsOfAUser_bumpsExactlyTheThreadsWithTheirContent() {
        Post withReply = createQuestion("Thread with a reply by student2");
        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        saveAnswer(withReply, student2, "Reply.", false);
        markAsStoredInCourseMemory(withReply);
        Post unrelated = createQuestion("Thread without student2");
        markAsStoredInCourseMemory(unrelated);
        long withReplyBefore = conversationMessageRepository.findCourseMemoryVersion(withReply.getId()).orElseThrow();
        long unrelatedBefore = conversationMessageRepository.findCourseMemoryVersion(unrelated.getId()).orElseThrow();
        List<Long> affected = courseMemoryIngestionService.invalidateThreadsWithContentBy(student2.getId());

        assertThat(affected).contains(withReply.getId()).doesNotContain(unrelated.getId());
        assertThat(conversationMessageRepository.findCourseMemoryVersion(withReply.getId()).orElseThrow()).isEqualTo(withReplyBefore + 1);
        assertThat(conversationMessageRepository.findCourseMemoryVersion(unrelated.getId()).orElseThrow()).isEqualTo(unrelatedBefore);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "optingout", roles = "USER")
    void optingOutOfAi_isRecordedTogetherWithTheBump() throws Exception {
        // An account of its own: the shared test users have content in the threads of other tests, all of which an
        // opt-out would rebuild in the background.
        User optingOut = userUtilService.createAndSaveUser(TEST_PREFIX + "optingout");
        userUtilService.clearAiSelectionDecision(optingOut);
        Post thread = new Post();
        thread.setAuthor(optingOut);
        thread.setContent("Thread before the opt-out");
        thread.setConversation(channel);
        thread.setVisibleForStudents(true);
        thread = conversationMessageRepository.save(thread);
        markAsStoredInCourseMemory(thread);
        long before = conversationMessageRepository.findCourseMemoryVersion(thread.getId()).orElseThrow();
        // The rebuild runs in the background. The thread has no answer, so it retracts the entry; wait for it, so that it
        // cannot reach the mocked Pyris during a later test.
        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> retraction = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(retraction::set);

        request.put("/api/account/users/select-llm-usage", new SelectedLLMUsageDTO(AiSelectionDecision.NO_AI), HttpStatus.OK);

        assertThat(userAiPreferenceService.findDecision(optingOut.getId())).isEqualTo(AiSelectionDecision.NO_AI);
        await().until(() -> retraction.get() != null);
        // Bumped together with the decision, then minted once more by the rebuild.
        assertThat(retraction.get().version()).isGreaterThan(before + 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deactivatingAnAccount_outdatesAndRebuildsItsThreads(boolean throughDeactivateEndpoint) throws Exception {
        // An account of its own: the shared test users have content in the threads of other tests, all of which a
        // deactivation would rebuild in the background.
        // Short logins: the admin update validates the full name, which the test user derives from the login.
        User deactivated = userUtilService.createAndSaveUser(TEST_PREFIX + (throughDeactivateEndpoint ? "dea1" : "dea2"));
        Post othersThread = createQuestion("Thread with a reply by an account that will be deactivated");
        saveAnswer(othersThread, deactivated, "Reply that will be redacted.", false);
        markAsStoredInCourseMemory(othersThread);
        long before = conversationMessageRepository.findCourseMemoryVersion(othersThread.getId()).orElseThrow();
        // With the reply redacted the thread has nothing left to store, so its rebuild retracts the entry.
        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> retraction = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(retraction::set);

        if (throughDeactivateEndpoint) {
            request.patch("/api/account/admin/users/" + deactivated.getId() + "/deactivate", null, HttpStatus.OK);
        }
        else {
            ManagedUserVM update = new ManagedUserVM(deactivated);
            update.setActivated(false);
            request.put("/api/account/admin/users", update, HttpStatus.OK);
        }

        await().until(() -> retraction.get() != null);
        // Bumped before the deactivation, then minted once more by the rebuild.
        assertThat(retraction.get().version()).isEqualTo(before + 2);
    }

    @Test
    void accountContentDeletion_deletesTheMessagesAndRebuildsTheSurvivingThreads() {
        // An account of its own: the shared test users have content in the threads of other tests, all of which a deletion
        // would rebuild in the background.
        User deleted = userUtilService.createAndSaveUser(TEST_PREFIX + "deletedcontent");
        Post othersThread = createQuestion("Thread with a reply by the deleted account");
        AnswerPost reply = saveAnswer(othersThread, deleted, "Reply that will be deleted.", false);
        markAsStoredInCourseMemory(othersThread);
        long before = conversationMessageRepository.findCourseMemoryVersion(othersThread.getId()).orElseThrow();
        // Without the reply the thread has nothing left to store, so its rebuild retracts the entry.
        AtomicReference<PyrisWebhookCourseMemoryDeletionExecutionDTO> retraction = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryDeletionWebhookRunResponse(retraction::set);

        userOwnedContentDeletionService.deleteCommunicationContent(deleted.getId());

        assertThat(answerPostRepository.findById(reply.getId())).isEmpty();
        await().until(() -> retraction.get() != null);
        // Bumped before the deletion, then minted once more by the rebuild.
        assertThat(retraction.get().version()).isEqualTo(before + 2);
    }

    @Test
    void nightlySync_reportsEveryTrackedThreadWithItsVersionAndEligibility() {
        Post readable = createQuestion("Readable thread");
        markAsStoredInCourseMemory(readable);
        Channel narrowed = conversationUtilService.createPublicChannel(course, "sync-private");
        narrowed.setIsPublic(false);
        narrowed = conversationRepository.save(narrowed);
        Post hidden = new Post();
        hidden.setAuthor(student);
        hidden.setContent("Thread in a private channel");
        hidden.setConversation(narrowed);
        hidden.setVisibleForStudents(true);
        hidden = conversationMessageRepository.save(hidden);
        markAsStoredInCourseMemory(hidden);

        List<PyrisCourseMemoryCourseSyncDTO> courseSyncs = new ArrayList<>();
        AtomicReference<String> instanceSync = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryInstanceSyncResponse(instanceSync::set);
        irisRequestMockProvider.mockCourseMemoryCourseSyncResponse(courseSyncs::add);

        courseMemorySyncService.syncCourseMemory();

        var sent = jsonMapper.readValue(instanceSync.get(), PyrisCourseMemoryInstanceSyncDTO.class);
        assertThat(sent.courseIds()).contains(course.getId());
        assertThat(sent.courseIdsWithThreads()).contains(course.getId());
        var mine = courseSyncs.stream().filter(sync -> sync.courseId() == course.getId()).findFirst().orElseThrow();
        assertThat(mine.threads()).contains(
                new PyrisCourseMemorySyncThreadDTO(readable.getId(), conversationMessageRepository.findCourseMemoryVersion(readable.getId()).orElseThrow(), true),
                new PyrisCourseMemorySyncThreadDTO(hidden.getId(), conversationMessageRepository.findCourseMemoryVersion(hidden.getId()).orElseThrow(), false));
    }

    @Test
    void nightlySync_aCourseWhoseLastThreadIsGoneIsLeftToTheInstanceSync() {
        Post thread = createQuestion("Thread that will be deleted");
        markAsStoredInCourseMemory(thread);
        conversationMessageRepository.deleteById(thread.getId());

        List<PyrisCourseMemoryCourseSyncDTO> courseSyncs = new ArrayList<>();
        AtomicReference<String> instanceSync = new AtomicReference<>();
        irisRequestMockProvider.mockCourseMemoryInstanceSyncResponse(instanceSync::set);
        irisRequestMockProvider.mockCourseMemoryCourseSyncResponse(courseSyncs::add);

        courseMemorySyncService.syncCourseMemory();

        // Pyris retracts the entries of an existing course without threads itself; no course sync is sent for it.
        var sent = jsonMapper.readValue(instanceSync.get(), PyrisCourseMemoryInstanceSyncDTO.class);
        assertThat(sent.courseIds()).contains(course.getId());
        assertThat(sent.courseIdsWithThreads()).doesNotContain(course.getId());
        assertThat(courseSyncs).noneMatch(sync -> sync.courseId() == course.getId());
    }

    @Test
    void nightlySync_sendsBothCourseListsEvenWhenEmpty() {
        // Pyris requires both lists: one that went missing must never read as "no courses". NON_EMPTY would drop them.
        String json = jsonMapper.writeValueAsString(new PyrisCourseMemoryInstanceSyncDTO(null, "2026-10-05T03:00:00Z", List.of(), List.of()));

        assertThat(json).contains("\"courseIds\":[]").contains("\"courseIdsWithThreads\":[]");
    }
}
